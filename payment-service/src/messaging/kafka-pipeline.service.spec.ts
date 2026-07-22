import { EachMessagePayload, Kafka, ProducerRecord } from 'kafkajs';
import { PaymentEntity } from '../payments/payment.entity';
import { createEnvelope } from './event-envelope';
import { KafkaPipelineService } from './kafka-pipeline.service';
import { PaymentOutboxWriter } from './payment-outbox.writer';

describe('KafkaPipelineService', () => {
  const reservationId = '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1';
  const domainEventId = '11111111-1111-4111-8111-111111111111';
  const seatReservedEventId = '550e8400-e29b-41d4-a716-446655440000';
  const paymentId = '8f94db81-4ab1-4239-b69c-477f4907ffb6';

  let service: KafkaPipelineService;
  let eachMessage: (payload: EachMessagePayload) => Promise<void>;
  let sentRecords: ProducerRecord[];
  let heartbeat: jest.Mock;
  let producer: {
    connect: jest.Mock;
    disconnect: jest.Mock;
    send: jest.Mock;
  };
  let consumer: {
    connect: jest.Mock;
    disconnect: jest.Mock;
    subscribe: jest.Mock;
    run: jest.Mock;
  };
  let paymentOutbox: { authorizeAndEnqueue: jest.Mock };

  const seatReservedValue = (
    quantity = 2,
    amount = 99.8,
    expiresAt = new Date(Date.now() + 60_000).toISOString(),
  ): string =>
    JSON.stringify(
      createEnvelope(seatReservedEventId, 'SeatReserved', reservationId, {
        reservationId,
        eventId: domainEventId,
        customerRef: 'client@test.fr',
        quantity,
        unitPrice: 49.9,
        amount,
        expiresAt,
      }),
    );

  const context = (value: string): EachMessagePayload => {
    heartbeat = jest.fn().mockResolvedValue(undefined);
    return {
      topic: 'booking.seat-reserved',
      partition: 0,
      message: {
        key: Buffer.from(reservationId),
        value: Buffer.from(value),
        timestamp: Date.now().toString(),
        attributes: 0,
        offset: '1',
        headers: {},
      },
      heartbeat,
      pause: jest.fn(),
    };
  };

  beforeEach(async () => {
    process.env.KAFKA_RETRY_BACKOFF_MS = '0';
    process.env.KAFKA_MAX_ATTEMPTS = '3';
    sentRecords = [];
    producer = {
      connect: jest.fn().mockResolvedValue(undefined),
      disconnect: jest.fn().mockResolvedValue(undefined),
      send: jest.fn().mockImplementation((record: ProducerRecord) => {
        sentRecords.push(record);
        return Promise.resolve([]);
      }),
    };
    consumer = {
      connect: jest.fn().mockResolvedValue(undefined),
      disconnect: jest.fn().mockResolvedValue(undefined),
      subscribe: jest.fn().mockResolvedValue(undefined),
      run: jest.fn().mockImplementation(({ eachMessage: handler }) => {
        eachMessage = handler as (payload: EachMessagePayload) => Promise<void>;
        return Promise.resolve();
      }),
    };
    const kafka = {
      producer: jest.fn().mockReturnValue(producer),
      consumer: jest.fn().mockReturnValue(consumer),
    };
    paymentOutbox = { authorizeAndEnqueue: jest.fn() };
    service = new KafkaPipelineService(
      kafka as unknown as Kafka,
      paymentOutbox as unknown as PaymentOutboxWriter,
    );
    await service.onModuleInit();
  });

  afterEach(() => {
    delete process.env.KAFKA_RETRY_BACKOFF_MS;
    delete process.env.KAFKA_MAX_ATTEMPTS;
  });

  it('consumes SeatReserved and commits PaymentReceived to the outbox', async () => {
    const payment: PaymentEntity = {
      id: paymentId,
      reservationId,
      amount: 99.8,
      status: 'RECEIVED',
      failureReason: null,
      createdAt: new Date(),
    };
    paymentOutbox.authorizeAndEnqueue.mockResolvedValue({
      payment,
      created: true,
      eventType: 'PaymentReceived',
      queued: true,
    });

    await eachMessage(context(seatReservedValue()));

    expect(paymentOutbox.authorizeAndEnqueue).toHaveBeenCalledWith(
      {
        reservationId,
        amount: 99.8,
      },
      undefined,
    );
    expect(sentRecords).toHaveLength(0);
  });

  it('commits PaymentFailed to the outbox for a controlled rejection', async () => {
    const payment: PaymentEntity = {
      id: paymentId,
      reservationId,
      amount: 149.7,
      status: 'REJECTED',
      failureReason: 'AMOUNT_THRESHOLD',
      createdAt: new Date(),
    };
    paymentOutbox.authorizeAndEnqueue.mockResolvedValue({
      payment,
      created: true,
      eventType: 'PaymentFailed',
      queued: true,
    });

    await eachMessage(context(seatReservedValue(3, 149.7)));

    expect(paymentOutbox.authorizeAndEnqueue).toHaveBeenCalledWith(
      { reservationId, amount: 149.7 },
      undefined,
    );
    expect(sentRecords).toHaveLength(0);
  });

  it('rejects an expired reservation without authorizing it as received', async () => {
    const payment: PaymentEntity = {
      id: paymentId,
      reservationId,
      amount: 99.8,
      status: 'REJECTED',
      failureReason: 'RESERVATION_EXPIRED',
      createdAt: new Date(),
    };
    paymentOutbox.authorizeAndEnqueue.mockResolvedValue({
      payment,
      created: true,
      eventType: 'PaymentFailed',
      queued: true,
    });

    await eachMessage(
      context(seatReservedValue(2, 99.8, '2020-01-01T00:00:00.000Z')),
    );

    expect(paymentOutbox.authorizeAndEnqueue).toHaveBeenCalledWith(
      { reservationId, amount: 99.8 },
      'RESERVATION_EXPIRED',
    );
    expect(sentRecords).toHaveLength(0);
  });

  it('retries malformed JSON three times then publishes it to the DLQ', async () => {
    const malformedContext = context('not-json');

    await eachMessage(malformedContext);

    expect(heartbeat).toHaveBeenCalledTimes(2);
    expect(sentRecords).toHaveLength(1);
    expect(sentRecords[0].topic).toBe('booking.seat-reserved.DLQ');
    expect(sentRecords[0].messages[0].partition).toBe(0);
    expect(sentRecords[0].messages[0].headers?.['x-retry-count']).toBe('3');
  });

  it('sends a message with an exhausted retry header directly to the DLQ', async () => {
    const exhausted = context('not-json');
    exhausted.message.headers = { 'x-retry-count': '3' };

    await eachMessage(exhausted);

    expect(heartbeat).not.toHaveBeenCalled();
    expect(sentRecords).toHaveLength(1);
    expect(sentRecords[0].topic).toBe('booking.seat-reserved.DLQ');
  });
});
