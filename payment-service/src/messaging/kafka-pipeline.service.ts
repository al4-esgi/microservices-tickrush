import {
  Inject,
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import {
  Consumer,
  EachMessagePayload,
  IHeaders,
  Kafka,
  Partitioners,
  Producer,
} from 'kafkajs';
import { parseEnvelope, parseSeatReservedPayload } from './event-envelope';
import { KAFKA_CLIENT, SEAT_RESERVED_TOPIC } from './kafka.constants';
import { PaymentOutboxWriter } from './payment-outbox.writer';

@Injectable()
export class KafkaPipelineService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(KafkaPipelineService.name);
  private readonly producer: Producer;
  private readonly consumer: Consumer;
  private readonly maxAttempts: number;
  private readonly backoffMs: number;

  constructor(
    @Inject(KAFKA_CLIENT) kafka: Kafka,
    private readonly paymentOutbox: PaymentOutboxWriter,
  ) {
    this.producer = kafka.producer({
      createPartitioner: Partitioners.DefaultPartitioner,
      idempotent: true,
      maxInFlightRequests: 5,
      allowAutoTopicCreation: false,
    });
    this.consumer = kafka.consumer({
      groupId: process.env.KAFKA_GROUP_ID ?? 'payment-service',
      allowAutoTopicCreation: false,
    });
    this.maxAttempts = this.readPositiveInteger(
      process.env.KAFKA_MAX_ATTEMPTS,
      3,
    );
    this.backoffMs = this.readNonNegativeInteger(
      process.env.KAFKA_RETRY_BACKOFF_MS,
      1_000,
    );
  }

  async onModuleInit(): Promise<void> {
    await this.producer.connect();
    await this.consumer.connect();
    await this.consumer.subscribe({
      topic: SEAT_RESERVED_TOPIC,
      fromBeginning: true,
    });
    await this.consumer.run({
      eachMessage: (context) => this.processWithRetry(context),
    });
    this.logger.log(
      `Pipeline Kafka actif: ${SEAT_RESERVED_TOPIC} -> outbox paiement`,
    );
  }

  async onModuleDestroy(): Promise<void> {
    await Promise.allSettled([
      this.consumer.disconnect(),
      this.producer.disconnect(),
    ]);
  }

  private async processWithRetry(context: EachMessagePayload): Promise<void> {
    const initialAttempt = this.readRetryHeader(context.message.headers);
    if (initialAttempt >= this.maxAttempts) {
      await this.publishToDlq(
        context,
        initialAttempt,
        'Budget de retries deja epuise',
      );
      return;
    }

    for (
      let attempt = initialAttempt + 1;
      attempt <= this.maxAttempts;
      attempt++
    ) {
      try {
        await this.handleSeatReserved(context);
        return;
      } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        const key = context.message.key?.toString('utf8') ?? '<sans-cle>';
        this.logger.warn(
          `Echec Kafka tentative ${attempt}/${this.maxAttempts}: topic=${context.topic}, partition=${context.partition}, offset=${context.message.offset}, key=${key}, erreur=${reason}`,
        );

        if (attempt >= this.maxAttempts) {
          await this.publishToDlq(context, attempt, reason);
          return;
        }

        await this.delay(this.backoffMs);
        await context.heartbeat();
      }
    }
  }

  private async handleSeatReserved({
    message,
  }: EachMessagePayload): Promise<void> {
    const envelope = parseEnvelope(message.value);
    if (envelope.eventType !== 'SeatReserved') {
      this.logger.debug(`Type ignore: ${envelope.eventType}`);
      return;
    }

    const payload = parseSeatReservedPayload(envelope.payload);
    const key = message.key?.toString('utf8');
    if (key !== envelope.aggregateId) {
      throw new Error('La cle Kafka doit etre egale a aggregateId');
    }
    if (payload.reservationId !== envelope.aggregateId) {
      throw new Error('aggregateId et reservationId doivent etre identiques');
    }

    const expired = Date.parse(payload.expiresAt) <= Date.now();
    const result = await this.paymentOutbox.authorizeAndEnqueue(
      {
        reservationId: payload.reservationId,
        amount: payload.amount,
      },
      expired ? 'RESERVATION_EXPIRED' : undefined,
    );
    this.logger.log(
      `${result.eventType} enregistre dans l'outbox: eventId=${result.payment.id}, reservationId=${result.payment.reservationId}, amount=${result.payment.amount}, nouveau=${result.queued}`,
    );
  }

  private async publishToDlq(
    { topic, partition, message }: EachMessagePayload,
    attempts: number,
    reason: string,
  ): Promise<void> {
    const dlqTopic = `${topic}.DLQ`;
    await this.producer.send({
      topic: dlqTopic,
      acks: -1,
      messages: [
        {
          key: message.key,
          value: message.value,
          partition,
          headers: {
            ...message.headers,
            'x-original-topic': topic,
            'x-retry-count': String(attempts),
            'x-error-message': reason.slice(0, 500),
          },
        },
      ],
    });
    this.logger.error(
      `Message transfere vers ${dlqTopic} apres ${attempts} tentatives`,
    );
  }

  private readRetryHeader(headers?: IHeaders): number {
    const value = headers?.['x-retry-count'];
    const first = Array.isArray(value) ? value[0] : value;
    const parsed = Number(
      Buffer.isBuffer(first) ? first.toString('utf8') : first,
    );
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
  }

  private readPositiveInteger(
    value: string | undefined,
    fallback: number,
  ): number {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
  }

  private readNonNegativeInteger(
    value: string | undefined,
    fallback: number,
  ): number {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
  }

  private delay(durationMs: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, durationMs));
  }
}
