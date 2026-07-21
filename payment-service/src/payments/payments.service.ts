import { Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { CreatePaymentDto } from './dto/create-payment.dto';

export type PaymentStatus = 'RECEIVED' | 'REJECTED';

export interface Payment {
  id: string;
  reservationId: string;
  amount: number;
  status: PaymentStatus;
  createdAt: string;
}

/**
 * Paiement simulé, stocké en mémoire (persistance PostgreSQL = travail perso ultérieur).
 * Idempotent : rejouer un paiement pour la même réservation renvoie l'existant
 * (exigence du sujet TickRush — ne pas émettre deux billets).
 * Taux d'échec configurable via PAYMENT_FAILURE_RATE (0..1, défaut 0 = toujours accepté).
 */
@Injectable()
export class PaymentsService {
  private readonly byReservation = new Map<string, Payment>();

  authorize(dto: CreatePaymentDto): { payment: Payment; created: boolean } {
    const existing = this.byReservation.get(dto.reservationId);
    if (existing) {
      return { payment: existing, created: false };
    }

    const failureRate = Number(process.env.PAYMENT_FAILURE_RATE ?? '0');
    const status: PaymentStatus = Math.random() < failureRate ? 'REJECTED' : 'RECEIVED';

    const payment: Payment = {
      id: randomUUID(),
      reservationId: dto.reservationId,
      amount: dto.amount,
      status,
      createdAt: new Date().toISOString(),
    };
    this.byReservation.set(dto.reservationId, payment);
    return { payment, created: true };
  }

  /** Statut du paiement d'une réservation. 'NONE' si aucun paiement (service sain). */
  statusOf(reservationId: string): string {
    return this.byReservation.get(reservationId)?.status ?? 'NONE';
  }
}
