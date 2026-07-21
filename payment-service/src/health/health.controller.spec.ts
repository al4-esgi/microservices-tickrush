import { Test, TestingModule } from '@nestjs/testing';
import { DataSource } from 'typeorm';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  let controller: HealthController;
  const dataSource = { query: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      controllers: [HealthController],
      providers: [{ provide: DataSource, useValue: dataSource }],
    }).compile();

    controller = module.get(HealthController);
  });

  it('reports the process as live without querying the database', () => {
    expect(controller.live()).toEqual({ status: 'UP' });
    expect(dataSource.query).not.toHaveBeenCalled();
  });

  it('reports readiness only after a successful database query', async () => {
    dataSource.query.mockResolvedValue([{ '?column?': 1 }]);

    await expect(controller.ready()).resolves.toEqual({
      status: 'UP',
      database: 'UP',
    });
    expect(dataSource.query).toHaveBeenCalledWith('SELECT 1');
  });

  it('propagates a database failure so readiness returns an error', async () => {
    dataSource.query.mockRejectedValue(new Error('database unavailable'));

    await expect(controller.ready()).rejects.toThrow('database unavailable');
  });
});
