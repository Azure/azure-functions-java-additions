package com.microsoft.azure.functions.opentelemetry;

import java.util.Map;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

/**
 * OpenTelemetry middleware that creates spans for Azure Functions invocations.
 * 
 * <p>This middleware creates a span for each function invocation and adds Azure Functions
 * specific attributes. It assumes an OpenTelemetry agent is present and configured.
 * 
 * <p><strong>Note:</strong> For log correlation, users should use the utility methods in
 * {@link FunctionsOpenTelemetry#getAzureContext(com.microsoft.azure.functions.ExecutionContext)} to get context attributes
 * and add them to their logs in the way that best fits their logging framework.
 */
public class OpenTelemetryInvocationMiddleware implements Middleware {

    /**
     * Creates a span for the function invocation with tracing context and Azure Functions attributes.
     * 
     * <p>The span includes the following attributes:
     * <ul>
     *   <li>{@code faas.name} - The function name</li>
     *   <li>{@code faas.invocation_id} - The unique invocation ID</li>
     *   <li>Azure resource attributes - service.name, cloud.provider, cloud.region, etc.</li>
     * </ul>
     * 
     * @param context the middleware context containing function metadata
     * @param chain the middleware chain to continue execution
     * @throws Exception if span creation fails or the function execution throws an exception
     */
    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        Span invocationSpan = null;
        
        try {
            String spanName = "Invoke";
            
            // Create and start the function invocation span with Azure attributes automatically set
            invocationSpan = FunctionsOpenTelemetry.startSpan(
                spanName, 
                context,  // MiddlewareContext extends ExecutionContext
                SpanKind.INTERNAL
            );
            
            try (Scope ignored = invocationSpan.makeCurrent()) {
                // Continue with the middleware chain
                chain.doNext(context);

            } catch (Throwable throwable) {
                // Record exception and set error status
                invocationSpan.recordException(throwable);
                invocationSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
                throw throwable;
            }
            
        } catch (Exception spanCreationException) {
            // If span creation fails, continue without tracing to avoid breaking function execution
            // This could happen if OpenTelemetry agent is misconfigured or has issues
            chain.doNext(context);
        } finally {
            // End the span if it was successfully created
            if (invocationSpan != null) {
                invocationSpan.end();
            }
        }
    }
}
