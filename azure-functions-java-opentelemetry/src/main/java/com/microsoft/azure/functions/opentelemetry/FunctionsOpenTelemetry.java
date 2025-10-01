package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.TraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenTelemetry integration for Azure Functions.
 * 
 * <p>Provides span creation methods and context attribute utilities that work with OpenTelemetry agents.
 * This library assumes an OpenTelemetry agent is present and configured.
 */
public final class FunctionsOpenTelemetry {

    /** Default tracer name for Azure Functions spans. */
    private static final String DEFAULT_TRACER_NAME = "azure.functions.worker";

    /** TextMapGetter for Azure Functions TraceContext. */
    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER = TraceContextTextMapGetter.INSTANCE;
    
    /** Cached Azure resource attributes (loaded once, shared across all invocations) */
    private static volatile Map<String, String> cachedAzureResourceAttributes = null;

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
     * Creates and starts a new span with Azure Functions context.
     * 
     * <p>This method automatically sets Azure Functions attributes on the span:
     * <ul>
     *   <li>Azure resource attributes (service.name, cloud.provider, etc.)</li>
     *   <li>Function-specific attributes (faas.name, faas.invocation_id)</li>
     * </ul>
     * 
     * @param spanName the name of the span
     * @param functionName the Azure Functions function name
     * @param invocationId the unique invocation ID
     * @param traceContext the Azure Functions trace context (optional)
     * @param kind the span kind
     * @return the started span with Azure attributes already set
     */
    public static Span startSpan(String spanName, String functionName, String invocationId, 
                                TraceContext traceContext, SpanKind kind) {
        validateNonEmpty(spanName, "spanName");
        
        // Determine parent context
        Context parent = Context.current();
        if (traceContext != null) {
            parent = getOpenTelemetry().getPropagators()
                    .getTextMapPropagator()
                    .extract(Context.current(), traceContext, TRACE_CONTEXT_GETTER);
        }
        
        // Create the span
        Span span = getOpenTelemetry().getTracer(DEFAULT_TRACER_NAME)
                .spanBuilder(spanName)
                .setParent(parent)
                .setSpanKind(kind == null ? SpanKind.INTERNAL : kind)
                .startSpan();
        
        // Automatically set Azure attributes using the same API users will use for logs
        getCurrentAzureContext(functionName, invocationId).forEach(span::setAttribute);
        
        return span;
    }

    /**
     * Gets Azure Functions context attributes for log correlation.
     * 
     * <p>This method returns a map of key-value pairs that can be used to correlate logs
     * with Azure Functions context and the current OpenTelemetry span. The attributes include:
     * 
     * <ul>
     *   <li><strong>Azure resource attributes:</strong> service.name, cloud.provider, cloud.region, etc.</li>
     *   <li><strong>Function-specific attributes:</strong> faas.name, faas.invocation_id (if provided)</li>
     * </ul>
     * 
     * <p>Usage examples:
     * <pre>{@code
     * // With SLF4J structured logging
     * Map<String, String> context = FunctionsOpenTelemetry.getCurrentAzureContext("myFunction", "inv-123");
     * logger.info("Processing request", context);
     * 
     * // With MDC
     * Map<String, String> context = FunctionsOpenTelemetry.getCurrentAzureContext("myFunction", "inv-123");
     * context.forEach(MDC::put);
     * logger.info("Processing request");
     * MDC.clear();
     * 
     * // With custom formatting
     * Map<String, String> context = FunctionsOpenTelemetry.getCurrentAzureContext("myFunction", "inv-123");
     * logger.info("Processing request - {}", context);
     * }</pre>
     * 
     * @param functionName the name of the Azure Function (optional)
     * @param invocationId the unique invocation ID for this function execution (optional)
     * @return a map of context attributes that can be used for log correlation
     */
    public static Map<String, String> getCurrentAzureContext(String functionName, String invocationId) {
        Map<String, String> attributes = new HashMap<>();
        
        // Add cached Azure resource attributes
        addAzureResourceAttributes(attributes);
        
        // Add function-specific attributes if provided
        if (functionName != null && !functionName.isEmpty()) {
            attributes.put("faas.name", functionName);
        }
        if (invocationId != null && !invocationId.isEmpty()) {
            attributes.put("faas.invocation_id", invocationId);
        }
        
        return attributes;
    }

    /**
     * Gets Azure resource attributes, initializing cache if needed.
     */
    private static Map<String, String> getAzureResourceAttributes() {
        if (cachedAzureResourceAttributes == null) {
            synchronized (FunctionsOpenTelemetry.class) {
                if (cachedAzureResourceAttributes == null) {
                    cachedAzureResourceAttributes = initializeAzureResourceAttributes();
                }
            }
        }
        return cachedAzureResourceAttributes;
    }

    /**
     * Initializes Azure resource attributes from the environment.
     */
    private static Map<String, String> initializeAzureResourceAttributes() {
        Map<String, String> attributes = new HashMap<>();
        
        try {
            Resource azureResource = FunctionsResourceDetector.getResource();
            azureResource.getAttributes().forEach((key, value) -> {
                if (value != null) {
                    attributes.put(key.getKey(), value.toString());
                }
            });
        } catch (Exception e) {
            // If resource detection fails, add basic attributes
            attributes.put("service.name", "java-function-app");
        }
        
        return attributes;
    }

    /**
     * Adds cached Azure resource attributes to the given map.
     */
    private static void addAzureResourceAttributes(Map<String, String> attributes) {
        attributes.putAll(getAzureResourceAttributes());
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private FunctionsOpenTelemetry() {
    }
}
