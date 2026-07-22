export type ReservationStatus = 'PENDING' | 'PAID' | 'TICKET_ISSUED' | 'EXPIRED' | 'CANCELLED'

export interface EventDto {
  id: string
  name: string
  totalSeats: number
  availableSeats: number
  unitPrice: number
}

export interface ReservationDto {
  id: string
  eventId: string
  customerRef: string
  quantity: number
  unitPrice: number
  amount: number
  status: ReservationStatus
  expiresAt: string
  createdAt: string
  ticketId: string | null
  ticketIssuedAt: string | null
}

export interface PaymentDto {
  id: string
  reservationId: string
  amount: number
  status: string
  createdAt: string
}

export interface MailAddress {
  address: string
  name?: string
}

export interface Email {
  id: string
  subject: string
  from: MailAddress[]
  to: MailAddress[]
  text?: string
  date: string
}

export interface ApiResult<T = unknown> {
  ok: boolean
  status: number
  data: T
}
