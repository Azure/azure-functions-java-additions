package com.microsoft.azure.functions.opentelemetry.tests;

import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class FunctionsOpenTelemetryTest {

    @BeforeAll
    static void setUp() {
        System.setProperty("otel.metrics.exporter", "none");
        System.setProperty("otel.traces.exporter", "none");
        System.setProperty("otel.logs.exporter", "none");
    }

    @Test
    void testSdkInitialization() {
        OpenTelemetrySdk sdk = FunctionsOpenTelemetry.sdk();
        Assertions.assertNotNull(sdk, "Expected a non-null OpenTelemetrySdk instance");
    }

    @Test
    void testStartSpanWithNullTraceContext() {
        Span span = FunctionsOpenTelemetry.startSpan("testTracer", "testSpan", (com.microsoft.azure.functions.TraceContext) null, SpanKind.INTERNAL);
        Assertions.assertNotNull(span, "Expected a non-null Span object");
        span.end();
    }

    @Test
    void testStartSpanWithDefaultSpanKind() {
        // Passing null for SpanKind should default to INTERNAL
        Span span = FunctionsOpenTelemetry.startSpan("defaultTracer", "defaultSpan", (com.microsoft.azure.functions.TraceContext) null, null);
        Assertions.assertNotNull(span, "Expected a non-null Span object");
        span.end();
    }
}
