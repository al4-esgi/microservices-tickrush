import { Kafka } from 'kafkajs';
import { DataSource } from 'typeorm';
import { PaymentOutboxEventEntity } from './payment-outbox-event.entity';
import { PaymentOutboxRelayService } from './payment-outbox-relay.service';

describe('PaymentOutboxRelayService', () => {
  let service: PaymentOutboxRelayService;
  let producer: {
    connect: jest.Mock;
    disconnect: jest.Mock;
    send: jest.Mock;
  };
  let queryBuilder: {
    setLock: jest.Mock;
    where: jest.Mock;
    orderBy: jest.Mock;
    getOne: jest.Mock;
  };
  let manager: {
    createQueryBuilder: jest.Mock;
    save: jest.Mock;
  };

  const pending = (): PaymentOutboxEventEntity => ({
    id: 1,
    eventId: '8f94db81-4ab1-4239-b69c-477f4907ffb6',
    eventType: 'PaymentReceived',
    aggregateId: '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1',
    topic: 'payment.received',
    eventKey: '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1',
    payload: '{"eventType":"PaymentReceived"}',
    traceParent: null,
    traceState: null,
    baggage: null,
    createdAt: new Date('2026-07-22T10:00:00Z'),
    publishedAt: null,
  });

  beforeEach(() => {
    producer = {
      connect: jest.fn().mockResolvedValue(undefined),
      disconnect: jest.fn().mockResolvedValue(undefined),
      send: jest.fn().mockResolvedValue([]),
    };
    queryBuilder = {
      setLock: jest.fn().mockReturnThis(),
      where: jest.fn().mockReturnThis(),
      orderBy: jest.fn().mockReturnThis(),
      getOne: jest.fn(),
    };
    manager = {
      createQueryBuilder: jest.fn().mockReturnValue(queryBuilder),
      save: jest
        .fn()
        .mockImplementation((row: PaymentOutboxEventEntity) =>
          Promise.resolve(row),
        ),
    };
    const kafka = {
      producer: jest.fn().mockReturnValue(producer),
    };
    const dataSource = {
      transaction: jest
        .fn()
        .mockImplementation(
          (work: (entityManager: typeof manager) => unknown) => work(manager),
        ),
    };
    service = new PaymentOutboxRelayService(
      kafka as unknown as Kafka,
      dataSource as unknown as DataSource,
    );
  });

  it('marks the row only after the Kafka acknowledgement', async () => {
    const event = pending();
    queryBuilder.getOne.mockResolvedValue(event);

    await expect(service.publishNext()).resolves.toBe(true);

    expect(producer.send).toHaveBeenCalledWith({
      topic: event.topic,
      acks: -1,
      messages: [{ key: event.eventKey, value: event.payload }],
    });
    expect(event.publishedAt).toBeInstanceOf(Date);
    expect(manager.save).toHaveBeenCalledWith(event);
  });

  it('leaves the row pending when Kafka rejects the publication', async () => {
    const event = pending();
    queryBuilder.getOne.mockResolvedValue(event);
    producer.send.mockRejectedValue(new Error('broker indisponible'));

    await expect(service.publishNext()).rejects.toThrow('broker indisponible');

    expect(event.publishedAt).toBeNull();
    expect(manager.save).not.toHaveBeenCalled();
  });

  it('forwards the W3C context stored with the outbox event', async () => {
    const event = pending();
    event.traceParent =
      '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01';
    queryBuilder.getOne.mockResolvedValue(event);

    await expect(service.publishNext()).resolves.toBe(true);

    expect(producer.send).toHaveBeenCalledWith(
      expect.objectContaining({
        messages: [
          expect.objectContaining({
            headers: { traceparent: event.traceParent },
          }),
        ],
      }),
    );
  });

  it('does nothing when no row is pending', async () => {
    queryBuilder.getOne.mockResolvedValue(null);

    await expect(service.publishNext()).resolves.toBe(false);

    expect(producer.send).not.toHaveBeenCalled();
    expect(manager.save).not.toHaveBeenCalled();
  });
});
