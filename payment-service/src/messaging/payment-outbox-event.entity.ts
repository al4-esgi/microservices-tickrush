import { Column, Entity, Index, PrimaryGeneratedColumn } from 'typeorm';

@Entity({ name: 'outbox' })
@Index('idx_payment_outbox_pending', ['publishedAt', 'id'])
export class PaymentOutboxEventEntity {
  @PrimaryGeneratedColumn()
  id: number;

  @Column({ name: 'event_id', type: 'uuid', unique: true })
  eventId: string;

  @Column({ name: 'event_type' })
  eventType: string;

  @Column({ name: 'aggregate_id', type: 'uuid' })
  aggregateId: string;

  @Column()
  topic: string;

  @Column({ name: 'event_key' })
  eventKey: string;

  @Column({ type: 'text' })
  payload: string;

  @Column({
    name: 'trace_parent',
    type: 'varchar',
    nullable: true,
    length: 55,
  })
  traceParent: string | null;

  @Column({
    name: 'trace_state',
    type: 'varchar',
    nullable: true,
    length: 512,
  })
  traceState: string | null;

  @Column({ type: 'text', nullable: true })
  baggage: string | null;

  @Column({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @Column({ name: 'published_at', type: 'timestamptz', nullable: true })
  publishedAt: Date | null;
}
