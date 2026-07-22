import { Injectable } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { CreatePaymentDto } from '../payments/dto/create-payment.dto';
import { PaymentEntity } from '../payments/payment.entity';
import { PaymentsService } from '../payments/payments.service';
import { createEnvelope, PaymentResultPayload } from './event-envelope';
import {
  PAYMENT_FAILED_TOPIC,
  PAYMENT_RECEIVED_TOPIC,
} from './kafka.constants';
import { PaymentOutboxEventEntity } from './payment-outbox-event.entity';

export type QueuedPaymentResult = {
  payment: PaymentEntity;
  created: boolean;
  eventType: 'PaymentReceived' | 'PaymentFailed';
  queued: boolean;
};

@Injectable()
export class PaymentOutboxWriter {
  constructor(
    private readonly dataSource: DataSource,
    private readonly payments: PaymentsService,
  ) {}

  async authorizeAndEnqueue(
    dto: CreatePaymentDto,
    forcedFailureReason?: string,
  ): Promise<QueuedPaymentResult> {
    return this.dataSource.transaction(async (manager) => {
      const { payment, created } = await this.payments.authorize(
        dto,
        forcedFailureReason,
        manager,
      );
      const eventType =
        payment.status === 'RECEIVED'
          ? ('PaymentReceived' as const)
          : ('PaymentFailed' as const);
      const topic =
        payment.status === 'RECEIVED'
          ? PAYMENT_RECEIVED_TOPIC
          : PAYMENT_FAILED_TOPIC;
      const response = createEnvelope<PaymentResultPayload>(
        payment.id,
        eventType,
        payment.reservationId,
        {
          paymentId: payment.id,
          reservationId: payment.reservationId,
          amount: payment.amount,
          ...(payment.failureReason ? { reason: payment.failureReason } : {}),
        },
      );

      const outbox = manager.getRepository(PaymentOutboxEventEntity);
      const existing = await outbox.findOne({
        where: { eventId: response.eventId },
      });
      if (existing) {
        return { payment, created, eventType, queued: false };
      }

      await outbox.save(
        outbox.create({
          eventId: response.eventId,
          eventType,
          aggregateId: response.aggregateId,
          topic,
          eventKey: response.aggregateId,
          payload: JSON.stringify(response),
          createdAt: new Date(response.occurredAt),
          publishedAt: null,
        }),
      );
      return { payment, created, eventType, queued: true };
    });
  }
}
