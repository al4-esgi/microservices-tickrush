package fr.esgi.tickrush.booking.messaging;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextSnapshotTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    void capturesAndRestoresAValidW3CContext() {
        SpanContext spanContext = SpanContext.create(
                TRACE_ID,
                SPAN_ID,
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        TraceContextSnapshot snapshot;
        try (var ignored = Context.root().with(Span.wrap(spanContext)).makeCurrent()) {
            snapshot = TraceContextSnapshot.capture();
        }

        assertThat(snapshot.traceParent())
                .isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        try (var ignored = snapshot.activate()) {
            assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(TRACE_ID);
            assertThat(Span.current().getSpanContext().getSpanId()).isEqualTo(SPAN_ID);
        }
    }
}
