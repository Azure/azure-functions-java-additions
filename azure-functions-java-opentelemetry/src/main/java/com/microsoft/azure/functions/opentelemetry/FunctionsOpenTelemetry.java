package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.TraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenTelemetry integration for Azure Functions.
 * 
 * <p>Provides agent-agnostic initialization that works with or without OpenTelemetry agents,
 * with optional Azure Monitor integration and convenient span creation methods.
 */
public final class FunctionsOpenTelemetry {

    /** Default tracer name for Azure Functions spans. */
    private static final String DEFAULT_TRACER_NAME = "azure.functions.worker";
    private static final String APP_INSIGHTS_ENABLE_ENV = "JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY";
    private static final String APP_INSIGHTS_CONNECTION_STRING_ENV = "APPLICATIONINSIGHTS_CONNECTION_STRING";
    private static final String AZURE_MONITOR_CLASS = "com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure";
    private static final String AUTO_CUSTOMIZER_CLASS = "io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer";

    private static Logger LOGGER = Logger.getLogger(FunctionsOpenTelemetry.class.getSimpleName());
    private static volatile io.opentelemetry.api.OpenTelemetry globalOtel;

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
     * Initializes OpenTelemetry for Azure Functions.
     * Safe to call multiple times.
     */
    public static void initialize() {
        if (globalOtel != null) {
            return; // Fast path - no synchronization needed after initialization
        }
        
        synchronized (FunctionsOpenTelemetry.class) {
            if (globalOtel != null) {
                return; // Double-check after acquiring lock
            }
            
            // Check if GlobalOpenTelemetry has already been configured
            io.opentelemetry.api.OpenTelemetry candidate = GlobalOpenTelemetry.get();
            
            if (isNoOp(candidate)) {
                LOGGER.info("No global OpenTelemetry found; initializing SDK.");
                globalOtel = buildSdk(); // Set our SDK as the global instance
            } else {
                LOGGER.info("GlobalOpenTelemetry already set; using existing instance.");
                globalOtel = candidate; // Set the agent's instance
            }
        }
    }

    /**
     * Ensures initialization has occurred.
     */
    private static void ensureInitialized() {
        if (globalOtel == null) {
            initialize();
        }
    }

    /**
     * Checks if the given OpenTelemetry instance is a no-op implementation.
     */
    private static boolean isNoOp(io.opentelemetry.api.OpenTelemetry otel) {
        if (otel == null) {
            return true;
        }
        // Check class name directly instead of creating tracer instances
        String className = otel.getClass().getName();
        return className.contains("Noop") || 
               className.contains("NoOp") || 
               className.contains("DefaultOpenTelemetry");
    }

    /**
     * Returns the OpenTelemetry instance for tracing operations.
     * @return the OpenTelemetry instance
     */
    public static io.opentelemetry.api.OpenTelemetry getOpenTelemetry() {
        ensureInitialized();
        return globalOtel;
    }

    /**
     * Creates and configures an OpenTelemetry SDK with Azure Monitor integration
     * if enabled via environment variables.
     */
    private static OpenTelemetrySdk buildSdk() {
        LOGGER.info("Initializing OpenTelemetry SDK ...");
        OpenTelemetrySdk sdk;

        try {
            final AutoConfiguredOpenTelemetrySdkBuilder builder =
                    AutoConfiguredOpenTelemetrySdk.builder();

            // Azure Functions resource attributes are automatically added via 
            // FunctionsResourceProvider (SPI mechanism)

            if (isAppInsightsEnabled()) {
                final String connStr = System.getenv(APP_INSIGHTS_CONNECTION_STRING_ENV);
                applyAzureMonitor(builder, connStr);
            }

            // AutoConfiguredOpenTelemetrySdk automatically registers globally when built
            AutoConfiguredOpenTelemetrySdk autoSdk = builder.build();
            sdk = autoSdk.getOpenTelemetrySdk();

            LOGGER.info("OpenTelemetry SDK initialised successfully.");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to initialise OpenTelemetry SDK – falling back to no-op", ex);

            // Use buildAndRegisterGlobal to avoid double registration
            sdk = OpenTelemetrySdk.builder().buildAndRegisterGlobal();
        }

        // Add shutdown hook for clean resource cleanup
        final OpenTelemetrySdk finalSdk = sdk;
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> finalSdk.getSdkTracerProvider().shutdown()));

        return sdk;
    }

    /**
     * Checks if Application Insights is enabled.
     */
    private static boolean isAppInsightsEnabled() {
        return Boolean.parseBoolean(System.getenv(APP_INSIGHTS_ENABLE_ENV));
    }

    /**
     * Applies Azure Monitor configuration via reflection if available.
     */

    private static void applyAzureMonitor(AutoConfiguredOpenTelemetrySdkBuilder builder, String connStr) {
        try {
            ClassLoader cl = FunctionsOpenTelemetry.class.getClassLoader();

            // Resolve the types we need with the same ClassLoader
            Class<?> autoCfgClass = Class.forName(AZURE_MONITOR_CLASS, false, cl);
            Class<?> customizerIfc = Class.forName(AUTO_CUSTOMIZER_CLASS, false, cl);

            // Look up the exact method overload we expect
            Method customize =
                    autoCfgClass.getMethod("customize", customizerIfc, String.class);

            customize.invoke(null, builder, connStr);
            LOGGER.info("AzureMonitorAutoConfigure applied via reflection");

        } catch (ClassNotFoundException e) {
            LOGGER.fine("azure-monitor-opentelemetry-autoconfigure not present – skipping");
        } catch (NoSuchMethodException e) {
            LOGGER.warning("AzureMonitorAutoConfigure.customize(...) not found – "
                    + "library version may have changed");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to apply AzureMonitorAutoConfigure", t);
        }
    }

    /** TextMapGetter for Azure Functions TraceContext. */
    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER = TraceContextTextMapGetter.INSTANCE;

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
