import { Controller, Get } from '@nestjs/common';
import { DataSource } from 'typeorm';

@Controller('health')
export class HealthController {
  constructor(private readonly dataSource: DataSource) {}

  @Get('live')
  live(): { status: 'UP' } {
    return { status: 'UP' };
  }

  @Get('ready')
  async ready(): Promise<{ status: 'UP'; database: 'UP' }> {
    await this.dataSource.query('SELECT 1');
    return { status: 'UP', database: 'UP' };
  }
}
