package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.TraceContext;
import io.opentelemetry.context.propagation.TextMapGetter;

/**
 * A singleton {@link TextMapGetter} that adapts an Azure Functions
 * {@link TraceContext} to OpenTelemetry’s propagation API.
 *
 * <p>Implementation is deliberately allocation-free:
 * the enum constant {@link #INSTANCE} is reused for every extraction call.</p>
 */
public enum TraceContextTextMapGetter implements TextMapGetter<TraceContext> {

    /** The single shared instance. */
    INSTANCE;

    @Override
    public Iterable<String> keys(final TraceContext carrier) {
        return carrier.getAttributes().keySet();
    }

    @Override
    public String get(final TraceContext carrier, final String key) {
        if (carrier == null || key == null) {
            return null;
        }
        // Match W3C header names first
        if ("traceparent".equalsIgnoreCase(key)) {
            return carrier.getTraceparent();
        }
        if ("tracestate".equalsIgnoreCase(key)) {
            return carrier.getTracestate();
        }
        // Fallback to custom attributes
        return carrier.getAttributes().get(key);
    }
}
