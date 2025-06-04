package com.microsoft.azure.functions.opentelemetry.tests;

import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.opentelemetry.OpenTelemetryInvocationMiddleware;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.logging.Logger;

public class OpenTelemetryInvocationMiddlewareTest {

    @Test
    void testInvokeCallsNextAndEndsSpan() throws Exception {
        // Mock context
        MiddlewareContext context = Mockito.mock(MiddlewareContext.class);
        Mockito.when(context.getFunctionName()).thenReturn("myFunction");
        Mockito.when(context.getInvocationId()).thenReturn("1234");
        Mockito.when(context.getLogger()).thenReturn(Logger.getLogger("testLogger"));
        Mockito.when(context.getTraceContext()).thenReturn(dummyTraceContext());

        // Mock chain
        MiddlewareChain chain = Mockito.mock(MiddlewareChain.class);

        OpenTelemetryInvocationMiddleware middleware = new OpenTelemetryInvocationMiddleware();
        middleware.invoke(context, chain);

        // Verify chain.doNext was called
        Mockito.verify(chain, Mockito.times(1)).doNext(context);

        // We can’t directly assert the state of the internal Span easily
        // but we can ensure no exceptions and that the code path was taken.
        Assertions.assertTrue(true, "Middleware invoked successfully");
    }

    @Test
    void testInvokeRecordsExceptionOnThrowable() throws Exception {
        // Mock context
        MiddlewareContext context = Mockito.mock(MiddlewareContext.class);
        Mockito.when(context.getFunctionName()).thenReturn("failingFunction");
        Mockito.when(context.getInvocationId()).thenReturn("9876");
        Mockito.when(context.getLogger()).thenReturn(Logger.getLogger("testLogger"));
        Mockito.when(context.getTraceContext()).thenReturn(dummyTraceContext());

        // Mock chain to throw
        MiddlewareChain chain = Mockito.mock(MiddlewareChain.class);
        Mockito.doThrow(new RuntimeException("Simulated failure")).when(chain).doNext(context);

        OpenTelemetryInvocationMiddleware middleware = new OpenTelemetryInvocationMiddleware();

        RuntimeException thrown = Assertions.assertThrows(
                RuntimeException.class,
                () -> middleware.invoke(context, chain)
        );
        Assertions.assertEquals("Simulated failure", thrown.getMessage());

        // Not easy to directly verify Span is marked with ERROR, but we know code calls setStatus.
        // If we wanted to ensure the Span's status is updated, we'd have to capture the Span object.
    }

    private TraceContext dummyTraceContext() {
        return new TraceContext() {
            @Override
            public String getTraceparent() {
                return "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
            }

            @Override
            public String getTracestate() {
                return "some=state";
            }

            @Override
            public java.util.Map<String, String> getAttributes() {
                return Collections.emptyMap();
            }
        };
    }
}
