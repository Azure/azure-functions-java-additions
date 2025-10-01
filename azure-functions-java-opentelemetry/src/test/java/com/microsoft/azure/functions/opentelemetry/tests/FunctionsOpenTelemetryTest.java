package com.microsoft.azure.functions.opentelemetry.tests;

import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
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
    void testAgentDetectionThrowsException() {
        // Since no agent is present during testing, we expect an IllegalStateException
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.getOpenTelemetry();
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }

    @Test
    void testStartSpanThrowsExceptionWithoutAgent() {
        // Since no agent is present during testing, we expect an IllegalStateException
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.startSpan("testSpan", "testFunction", "testInvocation", (com.microsoft.azure.functions.TraceContext) null, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }

    @Test
    void testStartSpanWithInternalSpanKindThrowsExceptionWithoutAgent() {
        // Since no agent is present during testing, we expect an IllegalStateException
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.startSpan("internalSpan", "testFunction", "testInvocation", (com.microsoft.azure.functions.TraceContext) null, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }
}
