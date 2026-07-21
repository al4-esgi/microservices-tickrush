import {
  Body,
  Controller,
  Get,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Res,
} from '@nestjs/common';
import type { Response } from 'express';
import { CreatePaymentDto } from './dto/create-payment.dto';
import { PaymentsService } from './payments.service';

@Controller('payments')
export class PaymentsController {
  constructor(private readonly payments: PaymentsService) {}

  /** Déclenche un paiement simulé. 201 si créé, 200 si déjà existant (idempotent). */
  @Post()
  async create(@Body() dto: CreatePaymentDto, @Res() res: Response): Promise<void> {
    const { payment, created } = await this.payments.authorize(dto);
    res.status(created ? HttpStatus.CREATED : HttpStatus.OK).json(payment);
  }

  /**
   * Statut du paiement d'une réservation (cible de l'appel protégé côté booking-service).
   * Renvoie 200 + un statut texte même sans paiement ('NONE') : seule une VRAIE panne
   * (timeout / service injoignable) doit faire basculer le circuit, pas un "pas encore payé".
   */
  @Get('by-reservation/:reservationId/status')
  status(
    @Param('reservationId', new ParseUUIDPipe()) reservationId: string,
  ): Promise<string> {
    return this.payments.statusOf(reservationId);
  }
}
