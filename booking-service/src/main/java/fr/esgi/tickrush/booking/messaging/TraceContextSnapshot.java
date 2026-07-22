package fr.esgi.tickrush.booking.messaging;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

record TraceContextSnapshot(String traceParent, String traceState, String baggage) {

    private static final String TRACE_PARENT = "traceparent";
    private static final String TRACE_STATE = "tracestate";
    private static final String BAGGAGE = "baggage";
    private static final TextMapPropagator PROPAGATOR = TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()
    );
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    static TraceContextSnapshot capture() {
        Map<String, String> carrier = new LinkedHashMap<>();
        PROPAGATOR.inject(Context.current(), carrier, Map::put);
        return from(carrier);
    }

    static TraceContextSnapshot from(Map<String, String> carrier) {
        return new TraceContextSnapshot(
                carrier.get(TRACE_PARENT),
                carrier.get(TRACE_STATE),
                carrier.get(BAGGAGE)
        );
    }

    Scope activate() {
        Context extracted = PROPAGATOR.extract(Context.root(), asCarrier(), GETTER);
        return extracted.makeCurrent();
    }

    void injectInto(Headers headers) {
        asCarrier().forEach((key, value) ->
                headers.add(key, value.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> asCarrier() {
        Map<String, String> carrier = new LinkedHashMap<>();
        putIfPresent(carrier, TRACE_PARENT, traceParent);
        putIfPresent(carrier, TRACE_STATE, traceState);
        putIfPresent(carrier, BAGGAGE, baggage);
        return carrier;
    }

    private static void putIfPresent(Map<String, String> carrier, String key, String value) {
        if (value != null && !value.isBlank()) {
            carrier.put(key, value);
        }
    }
}
