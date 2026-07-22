import { QueryFailedError, Repository } from 'typeorm';
import { PaymentEntity } from './payment.entity';
import { PaymentsService } from './payments.service';

type RepositoryMock = {
  findOne: jest.Mock;
  findOneByOrFail: jest.Mock;
  create: jest.Mock;
  save: jest.Mock;
};

describe('PaymentsService', () => {
  let service: PaymentsService;
  let repo: RepositoryMock;

  const dto = {
    reservationId: '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1',
    amount: 42,
  };

  const payment: PaymentEntity = {
    id: '8f94db81-4ab1-4239-b69c-477f4907ffb6',
    reservationId: dto.reservationId,
    amount: dto.amount,
    status: 'RECEIVED',
    failureReason: null,
    createdAt: new Date('2026-07-21T15:00:00Z'),
  };

  beforeEach(() => {
    jest.restoreAllMocks();
    process.env.PAYMENT_FAILURE_RATE = '0';
    process.env.PAYMENT_REJECTION_THRESHOLD = '100';
    repo = {
      findOne: jest.fn(),
      findOneByOrFail: jest.fn(),
      create: jest.fn(),
      save: jest.fn(),
    };
    service = new PaymentsService(repo as unknown as Repository<PaymentEntity>);
  });

  afterAll(() => {
    delete process.env.PAYMENT_FAILURE_RATE;
    delete process.env.PAYMENT_REJECTION_THRESHOLD;
  });

  it('returns the existing payment without creating a duplicate', async () => {
    repo.findOne.mockResolvedValue(payment);

    await expect(service.authorize(dto)).resolves.toEqual({
      payment,
      created: false,
    });
    expect(repo.create).not.toHaveBeenCalled();
    expect(repo.save).not.toHaveBeenCalled();
  });

  it('persists a new payment once', async () => {
    repo.findOne.mockResolvedValue(null);
    repo.create.mockReturnValue(payment);
    repo.save.mockResolvedValue(payment);

    await expect(service.authorize(dto)).resolves.toEqual({
      payment,
      created: true,
    });
    expect(repo.create).toHaveBeenCalledWith({
      reservationId: dto.reservationId,
      amount: dto.amount,
      status: 'RECEIVED',
      failureReason: null,
    });
    expect(repo.save).toHaveBeenCalledWith(payment);
  });

  it('returns the winner after a concurrent unique-key conflict', async () => {
    const conflict = new QueryFailedError('INSERT', [], {
      code: '23505',
    });
    repo.findOne.mockResolvedValue(null);
    repo.create.mockReturnValue(payment);
    repo.save.mockRejectedValue(conflict);
    repo.findOneByOrFail.mockResolvedValue(payment);

    await expect(service.authorize(dto)).resolves.toEqual({
      payment,
      created: false,
    });
    expect(repo.findOneByOrFail).toHaveBeenCalledWith({
      reservationId: dto.reservationId,
    });
  });

  it('can deterministically simulate a rejected payment', async () => {
    const rejected: PaymentEntity = {
      ...payment,
      status: 'REJECTED',
      failureReason: 'SIMULATED_FAILURE',
    };
    process.env.PAYMENT_FAILURE_RATE = '1';
    jest.spyOn(Math, 'random').mockReturnValue(0);
    repo.findOne.mockResolvedValue(null);
    repo.create.mockReturnValue(rejected);
    repo.save.mockResolvedValue(rejected);

    const result = await service.authorize(dto);

    expect(result.payment.status).toBe('REJECTED');
  });

  it('rejects a payment above the configured amount threshold', async () => {
    const expensiveDto = { ...dto, amount: 149.7 };
    const rejected: PaymentEntity = {
      ...payment,
      amount: expensiveDto.amount,
      status: 'REJECTED',
      failureReason: 'AMOUNT_THRESHOLD',
    };
    repo.findOne.mockResolvedValue(null);
    repo.create.mockReturnValue(rejected);
    repo.save.mockResolvedValue(rejected);

    await service.authorize(expensiveDto);

    expect(repo.create).toHaveBeenCalledWith({
      reservationId: expensiveDto.reservationId,
      amount: 149.7,
      status: 'REJECTED',
      failureReason: 'AMOUNT_THRESHOLD',
    });
  });

  it('persists a forced failure reason for an expired reservation', async () => {
    const rejected: PaymentEntity = {
      ...payment,
      status: 'REJECTED',
      failureReason: 'RESERVATION_EXPIRED',
    };
    repo.findOne.mockResolvedValue(null);
    repo.create.mockReturnValue(rejected);
    repo.save.mockResolvedValue(rejected);

    await service.authorize(dto, 'RESERVATION_EXPIRED');

    expect(repo.create).toHaveBeenCalledWith({
      reservationId: dto.reservationId,
      amount: dto.amount,
      status: 'REJECTED',
      failureReason: 'RESERVATION_EXPIRED',
    });
  });

  it('returns NONE when no payment exists', async () => {
    repo.findOne.mockResolvedValue(null);

    await expect(service.statusOf(dto.reservationId)).resolves.toBe('NONE');
  });

  it('returns the persisted payment status', async () => {
    repo.findOne.mockResolvedValue(payment);

    await expect(service.statusOf(dto.reservationId)).resolves.toBe('RECEIVED');
  });
});
