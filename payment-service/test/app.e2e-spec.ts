import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { DataSource } from 'typeorm';
import { AppController } from '../src/app.controller';
import { AppService } from '../src/app.service';
import { HealthController } from '../src/health/health.controller';
import { PaymentsController } from '../src/payments/payments.controller';
import { PaymentsService } from '../src/payments/payments.service';

describe('Payment API (e2e)', () => {
  let app: INestApplication<App>;

  const payment = {
    id: '8f94db81-4ab1-4239-b69c-477f4907ffb6',
    reservationId: '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1',
    amount: 42,
    status: 'RECEIVED',
    createdAt: new Date('2026-07-21T15:00:00Z'),
  };

  const payments = {
    authorize: jest.fn(),
    statusOf: jest.fn(),
  };

  const dataSource = { query: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    payments.authorize.mockResolvedValue({ payment, created: true });
    payments.statusOf.mockResolvedValue('RECEIVED');
    dataSource.query.mockResolvedValue([{ '?column?': 1 }]);

    const moduleFixture: TestingModule = await Test.createTestingModule({
      controllers: [AppController, HealthController, PaymentsController],
      providers: [
        AppService,
        { provide: PaymentsService, useValue: payments },
        { provide: DataSource, useValue: dataSource },
      ],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({ whitelist: true, transform: true }),
    );
    await app.init();
  });

  it('GET / identifies the service', () => {
    return request(app.getHttpServer())
      .get('/')
      .expect(200)
      .expect({ service: 'payment-service', status: 'UP' });
  });

  it('GET /health/ready checks the database', () => {
    return request(app.getHttpServer())
      .get('/health/ready')
      .expect(200)
      .expect({ status: 'UP', database: 'UP' });
  });

  it('POST /payments validates its payload', () => {
    return request(app.getHttpServer())
      .post('/payments')
      .send({ reservationId: 'invalid', amount: 0 })
      .expect(400);
  });

  it('POST /payments returns 201 for a newly created payment', () => {
    return request(app.getHttpServer())
      .post('/payments')
      .send({ reservationId: payment.reservationId, amount: payment.amount })
      .expect(201)
      .expect(({ body }) => {
        expect(body).toMatchObject({
          reservationId: payment.reservationId,
          amount: 42,
          status: 'RECEIVED',
        });
      });
  });

  afterEach(async () => {
    await app.close();
  });
});
