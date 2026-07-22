import {
  Inject,
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import { Kafka, Partitioners, Producer } from 'kafkajs';
import { DataSource } from 'typeorm';
import { KAFKA_CLIENT } from './kafka.constants';
import { PaymentOutboxEventEntity } from './payment-outbox-event.entity';

@Injectable()
export class PaymentOutboxRelayService
  implements OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger(PaymentOutboxRelayService.name);
  private readonly producer: Producer;
  private readonly enabled: boolean;
  private readonly intervalMs: number;
  private readonly batchSize: number;
  private timer?: NodeJS.Timeout;
  private stopped = false;
  private connected = false;
  private polling = false;

  constructor(
    @Inject(KAFKA_CLIENT) kafka: Kafka,
    private readonly dataSource: DataSource,
  ) {
    this.producer = kafka.producer({
      createPartitioner: Partitioners.DefaultPartitioner,
      idempotent: true,
      maxInFlightRequests: 5,
      allowAutoTopicCreation: false,
    });
    this.enabled = (process.env.OUTBOX_RELAY_ENABLED ?? 'true') === 'true';
    this.intervalMs = this.readPositiveInteger(
      process.env.OUTBOX_RELAY_INTERVAL_MS,
      500,
    );
    this.batchSize = this.readPositiveInteger(
      process.env.OUTBOX_RELAY_BATCH_SIZE,
      50,
    );
  }

  onModuleInit(): void {
    if (!this.enabled) {
      this.logger.warn('Relayeur Outbox desactive');
      return;
    }
    this.schedule(0);
  }

  async onModuleDestroy(): Promise<void> {
    this.stopped = true;
    if (this.timer) {
      clearTimeout(this.timer);
    }
    if (this.connected) {
      await this.producer.disconnect();
    }
  }

  async relayPending(): Promise<number> {
    if (this.polling) {
      return 0;
    }
    this.polling = true;
    let published = 0;
    try {
      await this.ensureConnected();
      while (published < this.batchSize && (await this.publishNext())) {
        published++;
      }
      return published;
    } finally {
      this.polling = false;
    }
  }

  async publishNext(): Promise<boolean> {
    await this.ensureConnected();
    return this.dataSource.transaction(async (manager) => {
      const event = await manager
        .createQueryBuilder(PaymentOutboxEventEntity, 'event')
        .setLock('pessimistic_write')
        .where('event.published_at IS NULL')
        .orderBy('event.id', 'ASC')
        .getOne();
      if (!event) {
        return false;
      }

      await this.producer.send({
        topic: event.topic,
        acks: -1,
        messages: [{ key: event.eventKey, value: event.payload }],
      });
      event.publishedAt = new Date();
      await manager.save(event);
      this.logger.log(
        `Outbox publiee: outboxId=${event.id}, eventId=${event.eventId}, eventType=${event.eventType}, reservationId=${event.aggregateId}, topic=${event.topic}`,
      );
      return true;
    });
  }

  private schedule(delayMs: number): void {
    if (this.stopped) {
      return;
    }
    this.timer = setTimeout(() => {
      void this.pollAndSchedule();
    }, delayMs);
    this.timer.unref();
  }

  private async pollAndSchedule(): Promise<void> {
    try {
      await this.relayPending();
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      this.logger.warn(
        `Outbox en attente, nouvel essai au prochain polling: ${reason}`,
      );
    } finally {
      this.schedule(this.intervalMs);
    }
  }

  private async ensureConnected(): Promise<void> {
    if (this.connected) {
      return;
    }
    await this.producer.connect();
    this.connected = true;
  }

  private readPositiveInteger(
    value: string | undefined,
    fallback: number,
  ): number {
    const parsed = Number(value);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
  }
}
