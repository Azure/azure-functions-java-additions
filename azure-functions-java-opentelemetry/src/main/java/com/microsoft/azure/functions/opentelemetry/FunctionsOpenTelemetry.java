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


    private static Logger LOGGER = Logger.getLogger(FunctionsOpenTelemetry.class.getSimpleName());
    private static volatile OpenTelemetrySdk sdk;
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
            io.opentelemetry.api.OpenTelemetry global = GlobalOpenTelemetry.get();
            
            if (isNoOp(global)) {
                LOGGER.info("No global OpenTelemetry found; initializing SDK.");
                if (sdk == null) {
                    sdk = buildSdk();
                }
            } else {
                LOGGER.info("GlobalOpenTelemetry already set; using existing instance.");
                // Extract the SDK if it's available for our sdk() method
                if (global instanceof OpenTelemetrySdk) {
                    sdk = (OpenTelemetrySdk) global;
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
     * We check this by seeing if the tracer name equals the class name, which indicates
     * the default no-op implementation.
     */
    private static boolean isNoOp(io.opentelemetry.api.OpenTelemetry otel) {
        if (otel == null) {
            return true;
        }
        try {
            // The no-op implementation returns a tracer with the class name as its name
            String tracerName = otel.getTracer("test").getClass().getSimpleName();
            return tracerName.contains("Noop") || tracerName.contains("NoOp") || 
                   otel.getClass().getName().contains("DefaultOpenTelemetry");
        } catch (Exception e) {
            // If we can't determine, assume it's not a no-op
            return false;
        }
    }

    /**
     * Returns the OpenTelemetrySdk instance if available.
     * 
     * @return the SDK instance
     * @throws IllegalStateException if no SDK is available (e.g., when using an agent)
     */
    public static OpenTelemetrySdk sdk() {
        ensureInitialized(); // Fast path after first call
        if (sdk != null) {
            return sdk;
        }
        // If the global instance is an OpenTelemetrySdk, return it
        io.opentelemetry.api.OpenTelemetry global = GlobalOpenTelemetry.get();
        if (global instanceof OpenTelemetrySdk) {
            return (OpenTelemetrySdk) global;
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
        ensureInitialized(); // Fast path after first call
        // Use our SDK if we created one, otherwise use whatever is set globally
        return (sdk != null) ? sdk : GlobalOpenTelemetry.get();
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
                final String connStr = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
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
        return Boolean.parseBoolean(
                System.getenv("JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY"));
    }

    private static void applyAzureMonitor(AutoConfiguredOpenTelemetrySdkBuilder builder, String connStr) {

        try {
            ClassLoader cl = FunctionsOpenTelemetry.class.getClassLoader();

            // Resolve the types we need with the same CL
            Class<?> autoCfgClass = Class.forName(
                    "com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure", false, cl);

            Class<?> customizerIfc = Class.forName(
                    "io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer", false, cl);

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

    public static Span startSpan(
            String tracerName,
            String spanName,
            Context parent,
            SpanKind kind) {

        if (spanName == null || spanName.isEmpty()) {
            throw new IllegalArgumentException("spanName must be non-null and non-empty");
        }
        if (tracerName == null || tracerName.isEmpty()) {
            throw new IllegalArgumentException("tracerName must be non-null and non-empty");
        }

        return getOpenTelemetry().getTracer(tracerName)
                .spanBuilder(spanName)
                .setParent(parent == null ? Context.current() : parent)
                .setSpanKind(kind == null ? SpanKind.INTERNAL : kind)
                .startSpan();
    }

    public static Span startSpan(
            String tracerName,
            String spanName,
            TraceContext traceContext,
            SpanKind kind) {

        Context parent = getOpenTelemetry().getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), traceContext, TRACE_CONTEXT_GETTER);
        return startSpan(tracerName, spanName, parent, kind);
    }
}
