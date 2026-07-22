import { context, propagation, ROOT_CONTEXT } from '@opentelemetry/api';

export type OutboxTraceContext = {
  traceParent: string | null;
  traceState: string | null;
  baggage: string | null;
};

type TraceCarrier = Record<string, string>;

export function captureTraceContext(): OutboxTraceContext {
  const carrier: TraceCarrier = {};
  propagation.inject(context.active(), carrier);
  return {
    traceParent: carrier.traceparent ?? null,
    traceState: carrier.tracestate ?? null,
    baggage: carrier.baggage ?? null,
  };
}

export function traceContextHeaders(
  snapshot: OutboxTraceContext,
): TraceCarrier {
  const carrier: TraceCarrier = {};
  putIfPresent(carrier, 'traceparent', snapshot.traceParent);
  putIfPresent(carrier, 'tracestate', snapshot.traceState);
  putIfPresent(carrier, 'baggage', snapshot.baggage);
  return carrier;
}

export function withTraceContext<T>(
  snapshot: OutboxTraceContext,
  work: () => T,
): T {
  const parent = propagation.extract(
    ROOT_CONTEXT,
    traceContextHeaders(snapshot),
  );
  return context.with(parent, work);
}

function putIfPresent(
  carrier: TraceCarrier,
  key: string,
  value: string | null,
): void {
  if (value) {
    carrier[key] = value;
  }
}
