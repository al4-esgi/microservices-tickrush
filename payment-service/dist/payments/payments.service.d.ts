import { CreatePaymentDto } from './dto/create-payment.dto';
export type PaymentStatus = 'RECEIVED' | 'REJECTED';
export interface Payment {
    id: string;
    reservationId: string;
    amount: number;
    status: PaymentStatus;
    createdAt: string;
}
export declare class PaymentsService {
    private readonly byReservation;
    authorize(dto: CreatePaymentDto): {
        payment: Payment;
        created: boolean;
    };
    statusOf(reservationId: string): string;
}
