import { DataSource, EntityManager } from 'typeorm';
import { PaymentEntity } from '../payments/payment.entity';
import { PaymentsService } from '../payments/payments.service';
import { PaymentOutboxEventEntity } from './payment-outbox-event.entity';
import { PaymentOutboxWriter } from './payment-outbox.writer';

describe('PaymentOutboxWriter', () => {
  const reservationId = '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1';
  const paymentId = '8f94db81-4ab1-4239-b69c-477f4907ffb6';
  const dto = { reservationId, amount: 49.9 };

  let writer: PaymentOutboxWriter;
  let payments: { authorize: jest.Mock };
  let outbox: {
    findOne: jest.Mock;
    create: jest.Mock;
    save: jest.Mock;
  };
  let manager: { getRepository: jest.Mock };
  let createdRow: { payload: string } | undefined;

  const receivedPayment = (): PaymentEntity => ({
    id: paymentId,
    reservationId,
    amount: 49.9,
    status: 'RECEIVED',
    failureReason: null,
    createdAt: new Date('2026-07-22T10:00:00Z'),
  });

  beforeEach(() => {
    payments = { authorize: jest.fn() };
    createdRow = undefined;
    outbox = {
      findOne: jest.fn().mockResolvedValue(null),
      create: jest.fn().mockImplementation((row: { payload: string }) => {
        createdRow = row;
        return row;
      }),
      save: jest
        .fn()
        .mockImplementation((row: unknown) => Promise.resolve(row)),
    };
    manager = {
      getRepository: jest.fn().mockReturnValue(outbox),
    };
    const dataSource = {
      transaction: jest
        .fn()
        .mockImplementation(
          (work: (entityManager: typeof manager) => unknown) => work(manager),
        ),
    };
    writer = new PaymentOutboxWriter(
      dataSource as unknown as DataSource,
      payments as unknown as PaymentsService,
    );
  });

  it('commits a received payment and its standard envelope in one transaction', async () => {
    const payment = receivedPayment();
    payments.authorize.mockResolvedValue({ payment, created: true });

    await expect(writer.authorizeAndEnqueue(dto)).resolves.toMatchObject({
      payment,
      created: true,
      eventType: 'PaymentReceived',
      queued: true,
    });

    expect(payments.authorize).toHaveBeenCalledWith(
      dto,
      undefined,
      manager as unknown as EntityManager,
    );
    expect(manager.getRepository).toHaveBeenCalledWith(
      PaymentOutboxEventEntity,
    );
    expect(outbox.create).toHaveBeenCalledWith(
      expect.objectContaining({
        eventId: paymentId,
        eventType: 'PaymentReceived',
        aggregateId: reservationId,
        eventKey: reservationId,
        topic: 'payment.received',
        publishedAt: null,
      }),
    );
    expect(createdRow).toBeDefined();
    expect(JSON.parse(createdRow?.payload ?? '')).toMatchObject({
      eventId: paymentId,
      eventType: 'PaymentReceived',
      aggregateId: reservationId,
      payload: { paymentId, reservationId, amount: 49.9 },
    });
  });

  it('queues PaymentFailed with its business reason', async () => {
    const payment: PaymentEntity = {
      ...receivedPayment(),
      amount: 149.7,
      status: 'REJECTED',
      failureReason: 'AMOUNT_THRESHOLD',
    };
    payments.authorize.mockResolvedValue({ payment, created: true });

    const result = await writer.authorizeAndEnqueue({
      reservationId,
      amount: 149.7,
    });

    expect(result.eventType).toBe('PaymentFailed');
    expect(outbox.create).toHaveBeenCalledWith(
      expect.objectContaining({ topic: 'payment.rejected' }),
    );
    expect(createdRow).toBeDefined();
    expect(JSON.parse(createdRow?.payload ?? '')).toMatchObject({
      payload: { reason: 'AMOUNT_THRESHOLD' },
    });
  });

  it('does not enqueue the same persistent payment twice', async () => {
    const payment = receivedPayment();
    payments.authorize.mockResolvedValue({ payment, created: false });
    outbox.findOne.mockResolvedValue({ eventId: paymentId });

    await expect(writer.authorizeAndEnqueue(dto)).resolves.toMatchObject({
      created: false,
      queued: false,
    });
    expect(outbox.save).not.toHaveBeenCalled();
  });
});
