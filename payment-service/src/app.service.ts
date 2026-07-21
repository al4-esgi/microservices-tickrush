import { Injectable } from '@nestjs/common';

@Injectable()
export class AppService {
  getInfo(): { service: 'payment-service'; status: 'UP' } {
    return { service: 'payment-service', status: 'UP' };
  }
}
