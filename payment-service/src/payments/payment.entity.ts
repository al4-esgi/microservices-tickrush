import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
} from 'typeorm';

export type PaymentStatus = 'RECEIVED' | 'REJECTED';

// numeric revient en string depuis pg → on le reconvertit en number
const numericTransformer = {
  to: (value: number) => value,
  from: (value: string | null) => (value === null ? null : parseFloat(value)),
};

@Entity('payments')
export class PaymentEntity {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  // unique : un seul paiement par réservation → idempotence garantie par la base
  @Column({ type: 'uuid', unique: true })
  reservationId!: string;

  @Column({
    type: 'numeric',
    precision: 12,
    scale: 2,
    transformer: numericTransformer,
  })
  amount!: number;

  @Column({ type: 'varchar', length: 16 })
  status!: PaymentStatus;

  @Column({ type: 'varchar', length: 64, nullable: true })
  failureReason!: string | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;
}
