import {
  createEnvelope,
  parseEnvelope,
  parseSeatReservedPayload,
} from './event-envelope';

describe('event envelope', () => {
  const reservationId = '65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1';
  const eventId = '11111111-1111-4111-8111-111111111111';

  it('parses the standard polyglot envelope', () => {
    const envelope = createEnvelope(eventId, 'SeatReserved', reservationId, {
      reservationId,
    });

    expect(parseEnvelope(Buffer.from(JSON.stringify(envelope)))).toEqual(
      envelope,
    );
  });

  it('validates the SeatReserved business payload', () => {
    expect(
      parseSeatReservedPayload({
        reservationId,
        eventId,
        customerRef: 'client@test.fr',
        quantity: 2,
        unitPrice: 49.9,
        amount: 99.8,
        expiresAt: '2026-07-22T10:00:00Z',
      }),
    ).toMatchObject({ reservationId, eventId, amount: 99.8 });
  });

  it('rejects malformed JSON', () => {
    expect(() => parseEnvelope(Buffer.from('not-json'))).toThrow();
  });

  it('rejects an amount inconsistent with unitPrice and quantity', () => {
    expect(() =>
      parseSeatReservedPayload({
        reservationId,
        eventId,
        customerRef: 'client@test.fr',
        quantity: 2,
        unitPrice: 49.9,
        amount: 10,
        expiresAt: '2026-07-22T10:00:00Z',
      }),
    ).toThrow('amount doit etre egal');
  });
});
