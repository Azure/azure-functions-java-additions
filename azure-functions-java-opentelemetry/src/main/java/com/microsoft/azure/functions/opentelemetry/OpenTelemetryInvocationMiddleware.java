package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;


/**
 * OpenTelemetry middleware that creates spans for Azure Functions invocations.
 */
public class OpenTelemetryInvocationMiddleware implements Middleware {

    /**
     * Constructs the middleware and initializes OpenTelemetry.
     */
    public OpenTelemetryInvocationMiddleware() {
        FunctionsOpenTelemetry.initialize();
    }

    /**
     * Creates a span for the function invocation with tracing context.
     */

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        String spanName = context.getFunctionName();
        
        // Configure logger for this invocation
        FunctionsOpenTelemetry.setLogger(context.getLogger());
        
        Span invocationSpan = FunctionsOpenTelemetry.startSpan(spanName, context.getTraceContext(), SpanKind.INTERNAL);

        try (Scope ignored = invocationSpan.makeCurrent()) {
            // Set function-specific span attributes
            invocationSpan.setAttribute("faas.invocation_id", context.getInvocationId());
            invocationSpan.setAttribute("faas.name", context.getFunctionName());

            // Continue with the middleware chain
            chain.doNext(context);

        } catch (Throwable throwable) {
            // Record exception and set error status
            invocationSpan.recordException(throwable);
            invocationSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
            throw throwable;
        } finally {
            invocationSpan.end();
        }
    }
}
