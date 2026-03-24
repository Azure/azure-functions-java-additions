/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.ExecutionContext;
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
    private static final String TRACER_NAME = "azure.functions.worker";

    /** TextMapGetter for Azure Functions TraceContext. */
    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER = TraceContextTextMapGetter.INSTANCE;
    
    /** Cached Azure resource attributes (loaded once, shared across all invocations) */
    private static volatile Map<String, String> cachedAzureResourceAttributes = null;

    /**
     * Returns the OpenTelemetry instance for tracing operations.
     * Assumes an OpenTelemetry agent has configured the global instance.
     * 
     * @return the global OpenTelemetry instance
     * @throws IllegalStateException if no OpenTelemetry agent is detected or if an error occurs while getting the SDK
     */
    public static io.opentelemetry.api.OpenTelemetry getOpenTelemetry() {
        try {
            final io.opentelemetry.api.OpenTelemetry otel = GlobalOpenTelemetry.get();
            
            if (isNoOp(otel)) {
                throw new IllegalStateException(
                    "No OpenTelemetry agent detected. This library requires an OpenTelemetry agent to be present. "
                    + "Please ensure your application is running with an OpenTelemetry Java agent."
                );
            }
            
            return otel;
        } catch (IllegalStateException e) {
            // Re-throw our own IllegalStateException
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to get OpenTelemetry instance: " + e.getMessage()
                + ". Please ensure your application is running with a properly configured OpenTelemetry Java agent.",
                e
            );
        }
    }

    /**
     * Checks if the given OpenTelemetry instance is a no-op implementation.
     */
    private static boolean isNoOp(io.opentelemetry.api.OpenTelemetry otel) {
        if (otel == null) {
            return true;
        }
        // Check class name to detect no-op implementations
        final String className = otel.getClass().getName();
        return className.contains("Noop")
               || className.contains("NoOp")
               || className.contains("DefaultOpenTelemetry")
               || className.contains("ObfuscatedOpenTelemetry"); // Default GlobalOpenTelemetry wrapper
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
     * @param executionContext the Azure Functions execution context containing function name, invocation ID, and trace context
     * @param kind the span kind
     * @return the started span with Azure attributes already set
     */
    public static Span startSpan(String spanName, ExecutionContext executionContext, SpanKind kind) {
        if (spanName == null || spanName.isEmpty()) {
            throw new IllegalArgumentException("spanName must be non-null and non-empty");
        }
        
        if (executionContext == null) {
            throw new IllegalArgumentException("executionContext must not be null");
        }
        
        // Extract trace context from execution context
        final TraceContext traceContext = executionContext.getTraceContext();
        
        // Determine parent context
        Context parent = Context.current();
        if (traceContext != null) {
            parent = getOpenTelemetry().getPropagators()
                    .getTextMapPropagator()
                    .extract(Context.current(), traceContext, TRACE_CONTEXT_GETTER);
        }
        
        // Create the span
        final Span span = getOpenTelemetry().getTracer(TRACER_NAME)
                .spanBuilder(spanName)
                .setParent(parent)
                .setSpanKind(kind == null ? SpanKind.INTERNAL : kind)
                .startSpan();
        
        // Automatically set Azure attributes using the execution context
        getAzureContext(executionContext).forEach(span::setAttribute);
        
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
     *   <li><strong>Function-specific attributes:</strong> faas.name, faas.invocation_id, faas.instance</li>
     *   <li><strong>Runtime attributes:</strong> process.pid, #AzFuncLiveLogsSessionId (from trace context)</li>
     * </ul>
     * 
     * <p>Usage examples:
     * <pre>{@code
     * // With SLF4J structured logging
     * Map<String, String> context = FunctionsOpenTelemetry.getAzureContext(executionContext);
     * logger.info("Processing request", context);
     * 
     * // With MDC
     * Map<String, String> context = FunctionsOpenTelemetry.getAzureContext(executionContext);
     * context.forEach(MDC::put);
     * logger.info("Processing request");
     * MDC.clear();
     * 
     * // With custom formatting
     * Map<String, String> context = FunctionsOpenTelemetry.getAzureContext(executionContext);
     * logger.info("Processing request - {}", context);
     * }</pre>
     * 
     * @param executionContext the Azure Functions execution context containing function name and invocation ID
     * @return a map of context attributes that can be used for log correlation
     */
    public static Map<String, String> getAzureContext(ExecutionContext executionContext) {
        if (executionContext == null) {
            throw new IllegalArgumentException("executionContext must not be null");
        }
        
        final Map<String, String> attributes = new HashMap<>();
        
        // Add cached Azure resource attributes
        addAzureResourceAttributes(attributes);
        
        // Add function-specific attributes from execution context
        final String functionName = executionContext.getFunctionName();
        if (functionName != null && !functionName.isEmpty()) {
            attributes.put("faas.name", functionName);
        }
        
        final String invocationId = executionContext.getInvocationId();
        if (invocationId != null && !invocationId.isEmpty()) {
            attributes.put("faas.invocation_id", invocationId);
        }
        
        // Add trace context attributes (HostInstanceId, ProcessId, etc.)
        final TraceContext traceContext = executionContext.getTraceContext();
        if (traceContext != null && traceContext.getAttributes() != null) {
            final Map<String, String> traceAttributes = traceContext.getAttributes();
            
            // Add HostInstanceId if available
            final String hostInstanceId = traceAttributes.get("HostInstanceId");
            if (hostInstanceId != null && !hostInstanceId.isEmpty()) {
                attributes.put("faas.instance", hostInstanceId);
            }
            
            // Add ProcessId if available
            final String processId = traceAttributes.get("ProcessId");
            if (processId != null && !processId.isEmpty()) {
                attributes.put("process.pid", processId);
            }
            
            // Add AzFuncLiveLogsSessionId if available
            final String liveLogsSessionId = traceAttributes.get("#AzFuncLiveLogsSessionId");
            if (liveLogsSessionId != null && !liveLogsSessionId.isEmpty()) {
                attributes.put("#AzFuncLiveLogsSessionId", liveLogsSessionId);
            }
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
        final Map<String, String> attributes = new HashMap<>();
        
        try {
            final Resource azureResource = FunctionsResourceDetector.getResource();
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
