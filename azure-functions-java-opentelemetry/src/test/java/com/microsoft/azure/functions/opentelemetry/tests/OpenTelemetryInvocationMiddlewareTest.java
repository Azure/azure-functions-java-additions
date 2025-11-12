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
    void testInvokeGracefullyHandlesNoAgent() throws Exception {
        // Mock context
        MiddlewareContext context = Mockito.mock(MiddlewareContext.class);
        Mockito.when(context.getFunctionName()).thenReturn("myFunction");
        Mockito.when(context.getInvocationId()).thenReturn("1234");
        Mockito.when(context.getLogger()).thenReturn(Logger.getLogger("testLogger"));
        Mockito.when(context.getTraceContext()).thenReturn(dummyTraceContext());

        // Mock chain
        MiddlewareChain chain = Mockito.mock(MiddlewareChain.class);

        OpenTelemetryInvocationMiddleware middleware = new OpenTelemetryInvocationMiddleware();
        
        // Since no agent is present during testing, middleware should gracefully continue without tracing
        // This should not throw an exception, allowing the function to execute normally
        Assertions.assertDoesNotThrow(() -> {
            middleware.invoke(context, chain);
        });
        
        // Verify chain.doNext was called even though span creation failed
        Mockito.verify(chain, Mockito.times(1)).doNext(context);
    }

    @Test
    void testInvokeContinuesChainExecutionWithoutAgent() throws Exception {
        // Mock context
        MiddlewareContext context = Mockito.mock(MiddlewareContext.class);
        Mockito.when(context.getFunctionName()).thenReturn("testFunction");
        Mockito.when(context.getInvocationId()).thenReturn("9876");
        Mockito.when(context.getLogger()).thenReturn(Logger.getLogger("testLogger"));
        Mockito.when(context.getTraceContext()).thenReturn(dummyTraceContext());

        // Mock chain
        MiddlewareChain chain = Mockito.mock(MiddlewareChain.class);

        OpenTelemetryInvocationMiddleware middleware = new OpenTelemetryInvocationMiddleware();

        // The middleware should gracefully handle the lack of agent and continue execution
        Assertions.assertDoesNotThrow(() -> {
            middleware.invoke(context, chain);
        });
        
        // Verify chain.doNext was called even without an agent
        Mockito.verify(chain, Mockito.times(1)).doNext(context);
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
