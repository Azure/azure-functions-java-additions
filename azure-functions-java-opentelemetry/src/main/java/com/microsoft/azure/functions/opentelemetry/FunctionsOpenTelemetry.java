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


public final class FunctionsOpenTelemetry {

    // Configuration constants
    private static final String DEFAULT_TRACER_NAME = "azure.functions.worker";
    private static final String APP_INSIGHTS_ENABLE_ENV = "JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY";
    private static final String APP_INSIGHTS_CONNECTION_STRING_ENV = "APPLICATIONINSIGHTS_CONNECTION_STRING";
    
    // Azure Monitor reflection constants
    private static final String AZURE_MONITOR_CLASS = "com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure";
    private static final String AUTO_CUSTOMIZER_CLASS = "io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer";

    private static Logger LOGGER = Logger.getLogger(FunctionsOpenTelemetry.class.getSimpleName());
    private static volatile OpenTelemetrySdk sdk;
    private static volatile io.opentelemetry.api.OpenTelemetry globalOtel;
    private static volatile boolean initialized = false;

    public static void setLogger(Logger logger) {
        if (logger != null) {
            LOGGER = logger;
        }
    }

    /**
     * Ensures that OpenTelemetry is initialized. This method is safe to call multiple times
     * and is optimized for performance after the first initialization.
     */
    public static void initialize() {
        if (initialized) {
            return; // Fast path - no synchronization needed after initialization
        }
        
        synchronized (FunctionsOpenTelemetry.class) {
            if (initialized) {
                return; // Double-check after acquiring lock
            }
            
            // Check if GlobalOpenTelemetry has already been configured
            globalOtel = GlobalOpenTelemetry.get();
            
            if (isNoOp(globalOtel)) {
                LOGGER.info("No global OpenTelemetry found; initializing SDK.");
                if (sdk == null) {
                    sdk = buildSdk();
                    globalOtel = sdk; // Cache our SDK as the global instance
                }
            } else {
                LOGGER.info("GlobalOpenTelemetry already set; using existing instance.");
                // Extract the SDK if it's available for our sdk() method
                if (globalOtel instanceof OpenTelemetrySdk) {
                    sdk = (OpenTelemetrySdk) globalOtel;
                }
            }
            
            initialized = true;
        }
    }

    /**
     * Internal method to ensure initialization with zero overhead after first call.
     * This provides automatic initialization for library methods while maintaining performance.
     */
    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    /**
     * Checks if the given OpenTelemetry instance is a no-op (default) implementation.
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
     * Returns the OpenTelemetrySdk instance if available.
     * 
     * @return the SDK instance
     * @throws IllegalStateException if no SDK is available (e.g., when using an agent)
     */
    public static OpenTelemetrySdk sdk() {
        ensureInitialized();
        if (sdk != null) {
            return sdk;
        }
        // Use cached global instance instead of calling GlobalOpenTelemetry.get() again
        if (globalOtel instanceof OpenTelemetrySdk) {
            return (OpenTelemetrySdk) globalOtel;
        }
        throw new IllegalStateException("No OpenTelemetrySdk available. Use getOpenTelemetry() for general tracing.");
    }

    /**
     * Returns the appropriate OpenTelemetry instance - either our SDK when we created it,
     * or the global OpenTelemetry when an agent or other setup is detected.
     * 
     * @return the OpenTelemetry instance to use for tracing and propagation
     */
    public static io.opentelemetry.api.OpenTelemetry getOpenTelemetry() {
        ensureInitialized();
        // Use cached global instance instead of calling GlobalOpenTelemetry.get() again
        return (sdk != null) ? sdk : globalOtel;
    }

    /**
     * Creates an SDK, optionally enriching it with Azure Monitor if the
     * environment variable<br>
     * {@code APPLICATIONINSIGHTS_CONNECTION_STRING}
     * is present and not null.
     *
     * <p>Reflection is used <em>only</em> for the optional
     * {@code AzureMonitorAutoConfigure} class so that users are free to exclude
     * it (or pull it in transitively) without breaking compilation.</p>
     */
    private static OpenTelemetrySdk buildSdk() {
        LOGGER.info("Initializing OpenTelemetry SDK ...");
        OpenTelemetrySdk sdk;

        try {
            final AutoConfiguredOpenTelemetrySdkBuilder builder =
                    AutoConfiguredOpenTelemetrySdk.builder();

            // Note: Functions resource attributes are automatically added via 
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

        // ---- Add shutdown hook (needs a final reference) --------------------------
        final OpenTelemetrySdk finalSdk = sdk;
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> finalSdk.getSdkTracerProvider().shutdown()));

        return sdk;
    }

    private static boolean isAppInsightsEnabled() {
        return Boolean.parseBoolean(System.getenv(APP_INSIGHTS_ENABLE_ENV));
    }

    private static void applyAzureMonitor(AutoConfiguredOpenTelemetrySdkBuilder builder, String connStr) {

        try {
            ClassLoader cl = FunctionsOpenTelemetry.class.getClassLoader();

            // Resolve the types we need with the same CL
            Class<?> autoCfgClass = Class.forName(AZURE_MONITOR_CLASS, false, cl);
            Class<?> customizerIfc = Class.forName(AUTO_CUSTOMIZER_CLASS, false, cl);

            // Directly look up the exact overload we expect
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

    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER = TraceContextTextMapGetter.INSTANCE;

    /**
     * Validates that the given string is non-null and non-empty.
     */
    private static void validateNonEmpty(String value, String paramName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(paramName + " must be non-null and non-empty");
        }
    }

    public static Span startSpan(String tracerName, String spanName, Context parent, SpanKind kind) {
        validateNonEmpty(spanName, "spanName");
        validateNonEmpty(tracerName, "tracerName");

        return getOpenTelemetry().getTracer(tracerName)
                .spanBuilder(spanName)
                .setParent(parent == null ? Context.current() : parent)
                .setSpanKind(kind == null ? SpanKind.INTERNAL : kind)
                .startSpan();
    }

    public static Span startSpan(String tracerName, String spanName, TraceContext traceContext, SpanKind kind) {
        Context parent = getOpenTelemetry().getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), traceContext, TRACE_CONTEXT_GETTER);
        return startSpan(tracerName, spanName, parent, kind);
    }

    // Convenience method with default tracer name
    public static Span startSpan(String spanName, TraceContext traceContext, SpanKind kind) {
        return startSpan(DEFAULT_TRACER_NAME, spanName, traceContext, kind);
    }
}
