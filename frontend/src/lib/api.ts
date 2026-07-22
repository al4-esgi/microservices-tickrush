import type { ApiResult, Email, EventDto, ReservationDto } from './types'

// Toutes les requêtes passent par le proxy Vite :
//   /api/*         -> gateway Traefik (localhost:8081) -> services
//   /maildev-api/* -> MailDev (localhost:1080)
const API = '/api'
const MAILDEV = '/maildev-api'

async function request<T = unknown>(
  method: string,
  path: string,
  body?: unknown,
): Promise<ApiResult<T>> {
  const res = await fetch(API + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  const raw = await res.text()
  let data: unknown = raw
  try {
    data = raw ? JSON.parse(raw) : null
  } catch {
    // réponse texte brut (ex. statut de paiement "RECEIVED")
  }
  return { ok: res.ok, status: res.status, data: data as T }
}

export interface CreateReservationInput {
  eventId: string
  customerRef: string
  quantity: number
}

export interface CreatePaymentInput {
  reservationId: string
  amount: number
}

export interface NotifyInput {
  to: string
  reservationId: string
  eventName: string
  quantity: number
}

export const api = {
  getEvent: (id: string) => request<EventDto>('GET', `/events/${id}`),
  createReservation: (b: CreateReservationInput) =>
    request<ReservationDto>('POST', '/reservations', b),
  getReservation: (id: string) => request<ReservationDto>('GET', `/reservations/${id}`),
  paymentStatus: (id: string) => request('GET', `/reservations/${id}/payment-status`),
  createPayment: (b: CreatePaymentInput) => request('POST', '/payments', b),
  notify: (b: NotifyInput) => request('POST', '/notifications/ticket-issued', b),

  emails: async (): Promise<Email[]> => {
    const res = await fetch(`${MAILDEV}/email`)
    if (!res.ok) throw new Error(`MailDev HTTP ${res.status}`)
    return res.json()
  },
  clearEmails: () => fetch(`${MAILDEV}/email/all`, { method: 'DELETE' }),
}
