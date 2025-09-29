package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.TraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.logging.Logger;

/**
 * OpenTelemetry integration for Azure Functions.
 * 
 * <p>Provides span creation methods that work with OpenTelemetry agents.
 * This library assumes an OpenTelemetry agent is present and configured.
 */
public final class FunctionsOpenTelemetry {

    /** Default tracer name for Azure Functions spans. */
    private static final String DEFAULT_TRACER_NAME = "azure.functions.worker";

    private static Logger LOGGER = Logger.getLogger(FunctionsOpenTelemetry.class.getSimpleName());

    /** TextMapGetter for Azure Functions TraceContext. */
    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER = TraceContextTextMapGetter.INSTANCE;

    /**
     * Sets the logger instance used by this class.
     * @param logger the logger instance to use
     */
    public static void setLogger(Logger logger) {
        if (logger != null) {
            LOGGER = logger;
        }
    }

    /**
     * Returns the OpenTelemetry instance for tracing operations.
     * Assumes an OpenTelemetry agent has configured the global instance.
     * 
     * @return the global OpenTelemetry instance
     * @throws IllegalStateException if no OpenTelemetry agent is detected
     */
    public static io.opentelemetry.api.OpenTelemetry getOpenTelemetry() {
        io.opentelemetry.api.OpenTelemetry otel = GlobalOpenTelemetry.get();
        
        if (isNoOp(otel)) {
            throw new IllegalStateException(
                "No OpenTelemetry agent detected. This library requires an OpenTelemetry agent to be present. " +
                "Please ensure your application is running with an OpenTelemetry Java agent."
            );
        }
        
        return otel;
    }

    /**
     * Checks if the given OpenTelemetry instance is a no-op implementation.
     */
    private static boolean isNoOp(io.opentelemetry.api.OpenTelemetry otel) {
        if (otel == null) {
            return true;
        }
        // Check class name to detect no-op implementations
        String className = otel.getClass().getName();
        return className.contains("Noop") || 
               className.contains("NoOp") || 
               className.contains("DefaultOpenTelemetry") ||
               className.contains("ObfuscatedOpenTelemetry"); // Default GlobalOpenTelemetry wrapper
    }

    /**
     * Validates that a string parameter is non-null and non-empty.
     */
    private static void validateNonEmpty(String value, String paramName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(paramName + " must be non-null and non-empty");
        }
    }

    /**
     * Creates and starts a new span.
     * @param tracerName the name of the tracer
     * @param spanName the name of the span
     * @param parent the parent context
     * @param kind the span kind
     * @return the started span
     */
    public static Span startSpan(String tracerName, String spanName, Context parent, SpanKind kind) {
        validateNonEmpty(spanName, "spanName");
        validateNonEmpty(tracerName, "tracerName");

        return getOpenTelemetry().getTracer(tracerName)
                .spanBuilder(spanName)
                .setParent(parent == null ? Context.current() : parent)
                .setSpanKind(kind == null ? SpanKind.INTERNAL : kind)
                .startSpan();
    }

    /**
     * Creates and starts a new span with trace context from Azure Functions.
     * @param tracerName the name of the tracer
     * @param spanName the name of the span
     * @param traceContext the Azure Functions trace context
     * @param kind the span kind
     * @return the started span
     */
    public static Span startSpan(String tracerName, String spanName, TraceContext traceContext, SpanKind kind) {
        Context parent = getOpenTelemetry().getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), traceContext, TRACE_CONTEXT_GETTER);
        return startSpan(tracerName, spanName, parent, kind);
    }

    /**
     * Convenience method using the default tracer name.
     * @param spanName the name of the span
     * @param traceContext the Azure Functions trace context
     * @param kind the span kind
     * @return the started span
     */
    public static Span startSpan(String spanName, TraceContext traceContext, SpanKind kind) {
        return startSpan(DEFAULT_TRACER_NAME, spanName, traceContext, kind);
    }
}
