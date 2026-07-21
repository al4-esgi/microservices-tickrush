import type { Response } from 'express';
import { CreatePaymentDto } from './dto/create-payment.dto';
import { PaymentsService } from './payments.service';
export declare class PaymentsController {
    private readonly payments;
    constructor(payments: PaymentsService);
    create(dto: CreatePaymentDto, res: Response): void;
    status(reservationId: string): string;
}
