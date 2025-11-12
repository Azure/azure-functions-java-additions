package com.microsoft.azure.functions.opentelemetry.tests;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


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
        ExecutionContext mockContext = createMockExecutionContext("testFunction", "testInvocation");
        
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.startSpan("testSpan", mockContext, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }

    @Test
    void testStartSpanWithInternalSpanKindThrowsExceptionWithoutAgent() {
        // Since no agent is present during testing, we expect an IllegalStateException
        ExecutionContext mockContext = createMockExecutionContext("testFunction", "testInvocation");
        
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.startSpan("internalSpan", mockContext, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }

    @Test
    void testStartSpanWithExecutionContextThrowsExceptionWithoutAgent() {
        // Since no agent is present during testing, we expect an IllegalStateException
        ExecutionContext mockContext = createMockExecutionContext("testFunction", "testInvocation");
        
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.startSpan("testSpan", mockContext, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }

    @Test
    void testStartSpanWithNullExecutionContextThrowsException() {
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FunctionsOpenTelemetry.startSpan("testSpan", null, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("executionContext must not be null"));
    }

    @Test
    void testStartSpanWithEmptySpanNameThrowsException() {
        ExecutionContext mockContext = createMockExecutionContext("testFunction", "testInvocation");
        
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FunctionsOpenTelemetry.startSpan("", mockContext, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("spanName must be non-null and non-empty"));
    }

    @Test
    void testStartSpanWithNullSpanNameThrowsException() {
        ExecutionContext mockContext = createMockExecutionContext("testFunction", "testInvocation");
        
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FunctionsOpenTelemetry.startSpan(null, mockContext, SpanKind.INTERNAL);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("spanName must be non-null and non-empty"));
    }

    @Test
    void testStartSpanWithNullSpanKindThrowsExceptionWithoutAgent() {
        // Since no agent is present during testing, we expect an IllegalStateException
        // Test that null SpanKind defaults to INTERNAL but still fails due to no agent
        ExecutionContext mockContext = createMockExecutionContext("testFunction", "testInvocation");
        
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            FunctionsOpenTelemetry.startSpan("testSpan", mockContext, null);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("No OpenTelemetry agent detected"));
    }

    @Test
    void testGetAzureContextReturnsCorrectAttributes() {
        // This test doesn't require OpenTelemetry agent since it only tests context attribute extraction
        ExecutionContext mockContext = createMockExecutionContext("myFunction", "inv-123");
        
        Map<String, String> context = FunctionsOpenTelemetry.getAzureContext(mockContext);
        
        Assertions.assertNotNull(context);
        Assertions.assertEquals("myFunction", context.get("faas.name"));
        Assertions.assertEquals("inv-123", context.get("faas.invocation_id"));
        // Should also contain Azure resource attributes
        Assertions.assertTrue(context.containsKey("service.name"));
    }

    @Test
    void testGetAzureContextWithNullExecutionContextThrowsException() {
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FunctionsOpenTelemetry.getAzureContext(null);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("executionContext must not be null"));
    }

    @Test
    void testGetAzureContextIncludesTraceContextAttributes() {
        // Test that trace context attributes like HostInstanceId and ProcessId are included
        ExecutionContext mockContext = createMockExecutionContextWithTraceAttributes("myFunction", "inv-123");
        
        Map<String, String> context = FunctionsOpenTelemetry.getAzureContext(mockContext);
        
        Assertions.assertNotNull(context);
        Assertions.assertEquals("myFunction", context.get("faas.name"));
        Assertions.assertEquals("inv-123", context.get("faas.invocation_id"));
        Assertions.assertEquals("test-host-instance", context.get("faas.instance"));
        Assertions.assertEquals("12345", context.get("process.pid"));
        Assertions.assertEquals("test-session-123", context.get("#AzFuncLiveLogsSessionId"));
        // Should also contain Azure resource attributes
        Assertions.assertTrue(context.containsKey("service.name"));
    }

    private ExecutionContext createMockExecutionContext(String functionName, String invocationId) {
        return new ExecutionContext() {
            @Override
            public Logger getLogger() {
                return Logger.getLogger("test");
            }

            @Override
            public String getInvocationId() {
                return invocationId;
            }

            @Override
            public String getFunctionName() {
                return functionName;
            }

            @Override
            public TraceContext getTraceContext() {
                return null;
            }
        };
    }

    private ExecutionContext createMockExecutionContextWithTraceAttributes(String functionName, String invocationId) {
        return new ExecutionContext() {
            @Override
            public Logger getLogger() {
                return Logger.getLogger("test");
            }

            @Override
            public String getInvocationId() {
                return invocationId;
            }

            @Override
            public String getFunctionName() {
                return functionName;
            }

            @Override
            public TraceContext getTraceContext() {
                return new TraceContext() {
                    @Override
                    public String getTraceparent() {
                        return "00-test-parent-01";
                    }

                    @Override
                    public String getTracestate() {
                        return "test-state";
                    }

                    @Override
                    public Map<String, String> getAttributes() {
                        Map<String, String> attributes = new HashMap<>();
                        attributes.put("HostInstanceId", "test-host-instance");
                        attributes.put("ProcessId", "12345");
                        attributes.put("#AzFuncLiveLogsSessionId", "test-session-123");
                        return attributes;
                    }
                };
            }
        };
    }
}
