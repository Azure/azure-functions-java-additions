package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;


/**
 * Middleware that starts a Span for every function invocation,
 * using the trace context from the host if available.
 */
public class OpenTelemetryInvocationMiddleware implements Middleware {

    public OpenTelemetryInvocationMiddleware() {
        FunctionsOpenTelemetry.initialize();
    }

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        String spanName = context.getFunctionName();
        String tracerName = "azure.functions.worker";

        FunctionsOpenTelemetry.setLogger(context.getLogger());
        Span invocationSpan = FunctionsOpenTelemetry.startSpan(tracerName, spanName, context.getTraceContext(), SpanKind.INTERNAL);

        try (Scope ignored = invocationSpan.makeCurrent()) {
            invocationSpan.setAttribute("faas.invocation_id", context.getInvocationId());
            invocationSpan.setAttribute("faas.name", context.getFunctionName());

            // Delegate to the rest of the chain
            chain.doNext(context);

        } catch (Throwable throwable) {          // capture any user exception
            invocationSpan.recordException(throwable);
            invocationSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
            throw throwable;                     // keep behaviour unchanged
        } finally {
            invocationSpan.end();
        }
    }
}
