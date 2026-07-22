import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { HealthController } from './health/health.controller';
import { KafkaMessagingModule } from './messaging/kafka-messaging.module';
import { PaymentOutboxEventEntity } from './messaging/payment-outbox-event.entity';
import { PaymentEntity } from './payments/payment.entity';
import { PaymentsModule } from './payments/payments.module';

@Module({
  imports: [
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: process.env.DB_HOST ?? 'localhost',
      port: Number(process.env.DB_PORT ?? '5432'),
      username: process.env.DB_USER ?? 'payment',
      password: process.env.DB_PASSWORD ?? 'payment',
      database: process.env.DB_NAME ?? 'paymentdb',
      entities: [PaymentEntity, PaymentOutboxEventEntity],
      synchronize: true, // acceptable pour le TP ; en prod : migrations
    }),
    PaymentsModule,
    KafkaMessagingModule,
  ],
  controllers: [AppController, HealthController],
  providers: [AppService],
})
export class AppModule {}
