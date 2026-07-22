export interface EventEnvelope<T> {
  eventId: string;
  eventType: string;
  occurredAt: string;
  aggregateId: string;
  payload: T;
}

export interface SeatReservedPayload {
  reservationId: string;
  eventId: string;
  customerRef: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  expiresAt: string;
}

export interface PaymentResultPayload {
  paymentId: string;
  reservationId: string;
  amount: number;
  reason?: string;
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireUuid(value: unknown, field: string): string {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    throw new Error(`${field} doit etre un UUID`);
  }
  return value;
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${field} doit etre une chaine non vide`);
  }
  return value;
}

function requirePositiveNumber(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    throw new Error(`${field} doit etre un nombre strictement positif`);
  }
  return value;
}

export function parseEnvelope(value: Buffer | null): EventEnvelope<unknown> {
  if (value === null) {
    throw new Error('Le message Kafka ne contient aucune valeur');
  }

  const decoded: unknown = JSON.parse(value.toString('utf8'));
  if (!isObject(decoded)) {
    throw new Error("L'enveloppe Kafka doit etre un objet JSON");
  }

  const occurredAt = requireString(decoded.occurredAt, 'occurredAt');
  if (Number.isNaN(Date.parse(occurredAt))) {
    throw new Error('occurredAt doit etre une date ISO-8601');
  }

  return {
    eventId: requireUuid(decoded.eventId, 'eventId'),
    eventType: requireString(decoded.eventType, 'eventType'),
    occurredAt,
    aggregateId: requireUuid(decoded.aggregateId, 'aggregateId'),
    payload: decoded.payload,
  };
}

export function parseSeatReservedPayload(
  payload: unknown,
): SeatReservedPayload {
  if (!isObject(payload)) {
    throw new Error('Le payload SeatReserved doit etre un objet');
  }

  const quantity = requirePositiveNumber(payload.quantity, 'quantity');
  if (!Number.isInteger(quantity)) {
    throw new Error('quantity doit etre un entier');
  }

  const unitPrice = requirePositiveNumber(payload.unitPrice, 'unitPrice');
  const amount = requirePositiveNumber(payload.amount, 'amount');
  if (Math.round(unitPrice * 100) * quantity !== Math.round(amount * 100)) {
    throw new Error('amount doit etre egal a unitPrice multiplie par quantity');
  }

  const expiresAt = requireString(payload.expiresAt, 'expiresAt');
  if (Number.isNaN(Date.parse(expiresAt))) {
    throw new Error('expiresAt doit etre une date ISO-8601');
  }

  return {
    reservationId: requireUuid(payload.reservationId, 'reservationId'),
    eventId: requireUuid(payload.eventId, 'eventId metier'),
    customerRef: requireString(payload.customerRef, 'customerRef'),
    quantity,
    unitPrice,
    amount,
    expiresAt,
  };
}

export function createEnvelope<T>(
  eventId: string,
  eventType: string,
  aggregateId: string,
  payload: T,
): EventEnvelope<T> {
  return {
    eventId,
    eventType,
    occurredAt: new Date().toISOString(),
    aggregateId,
    payload,
  };
}
