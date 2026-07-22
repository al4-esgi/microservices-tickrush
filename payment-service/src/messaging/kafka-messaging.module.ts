import { Module } from '@nestjs/common';
import { Kafka, logLevel } from 'kafkajs';
import { PaymentsModule } from '../payments/payments.module';
import { KAFKA_CLIENT } from './kafka.constants';
import { KafkaPipelineService } from './kafka-pipeline.service';

@Module({
  imports: [PaymentsModule],
  providers: [
    {
      provide: KAFKA_CLIENT,
      useFactory: () =>
        new Kafka({
          clientId: 'payment-service',
          brokers: (process.env.KAFKA_BROKERS ?? 'localhost:9092')
            .split(',')
            .map((broker) => broker.trim())
            .filter(Boolean),
          logLevel: logLevel.WARN,
        }),
    },
    KafkaPipelineService,
  ],
})
export class KafkaMessagingModule {}
