import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { QueryFailedError, Repository } from 'typeorm';
import { CreatePaymentDto } from './dto/create-payment.dto';
import { PaymentEntity, PaymentStatus } from './payment.entity';

const PG_UNIQUE_VIOLATION = '23505';

/**
 * Paiement simulé, persisté en PostgreSQL (TypeORM).
 * Idempotent : rejouer un paiement pour la même réservation renvoie l'existant.
 * L'unicité de `reservationId` en base garantit l'idempotence même sous concurrence
 * (deux requêtes simultanées → une seule insertion, l'autre récupère l'existante).
 * Échec contrôlable par seuil de montant, complété par un taux aléatoire optionnel.
 */
@Injectable()
export class PaymentsService {
  constructor(
    @InjectRepository(PaymentEntity)
    private readonly repo: Repository<PaymentEntity>,
  ) {}

  async authorize(
    dto: CreatePaymentDto,
  ): Promise<{ payment: PaymentEntity; created: boolean }> {
    const existing = await this.repo.findOne({
      where: { reservationId: dto.reservationId },
    });
    if (existing) {
      return { payment: existing, created: false };
    }

    const failureRate = Number(process.env.PAYMENT_FAILURE_RATE ?? '0');
    const rejectionThreshold = Number(
      process.env.PAYMENT_REJECTION_THRESHOLD ?? '100',
    );
    const status: PaymentStatus =
      dto.amount > rejectionThreshold || Math.random() < failureRate
        ? 'REJECTED'
        : 'RECEIVED';

    const entity = this.repo.create({
      reservationId: dto.reservationId,
      amount: dto.amount,
      status,
    });

    try {
      const saved = await this.repo.save(entity);
      return { payment: saved, created: true };
    } catch (err) {
      // course : violation d'unicité → un paiement a été créé entre-temps
      if (
        err instanceof QueryFailedError &&
        (err.driverError as { code?: string })?.code === PG_UNIQUE_VIOLATION
      ) {
        const winner = await this.repo.findOneByOrFail({
          reservationId: dto.reservationId,
        });
        return { payment: winner, created: false };
      }
      throw err;
    }
  }

  /** Statut du paiement d'une réservation. 'NONE' si aucun paiement (service sain). */
  async statusOf(reservationId: string): Promise<string> {
    const payment = await this.repo.findOne({ where: { reservationId } });
    return payment?.status ?? 'NONE';
  }
}
