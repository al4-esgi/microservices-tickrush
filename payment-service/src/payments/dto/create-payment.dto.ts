import { IsNumber, IsPositive, IsUUID } from 'class-validator';

export class CreatePaymentDto {
  @IsUUID()
  reservationId!: string;

  @IsNumber()
  @IsPositive()
  amount!: number;
}
