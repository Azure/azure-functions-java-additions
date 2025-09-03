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

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    /**
     * Ensures that the OpenTelemetry SDK is created.
     */
    public static void initialize() {
        if (isAgentPresent()) {
            LOGGER.info("OTel Java agent detected; using GlobalOpenTelemetry from agent.");
            return;
        }
        else {
            LOGGER.info("OTel Java agent not detected; initializing SDK.");
        }
        
        if (sdk == null) {
            synchronized (FunctionsOpenTelemetry.class) {
                if (sdk == null) {
                    sdk = buildSdk();
                }
            }
        }
    }

    private static boolean isAgentPresent() {
        try {
            Class.forName("io.opentelemetry.javaagent.OpenTelemetryAgent", false,
                          FunctionsOpenTelemetry.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static OpenTelemetrySdk sdk() {
        initialize(); // Ensure SDK is initialized
        if (sdk != null) {
            return sdk;
        }
        // When agent is present, we don't have direct access to OpenTelemetrySdk
        // This method should not be called in agent scenarios
        throw new IllegalStateException("OpenTelemetry agent is present. Use GlobalOpenTelemetry.get() instead of sdk() when agent is detected.");
    }

    /**
     * Returns the appropriate OpenTelemetry instance - either the SDK when no agent is present,
     * or the global OpenTelemetry when an agent is detected.
     */
    private static io.opentelemetry.api.OpenTelemetry getOpenTelemetry() {
        initialize(); // Ensure SDK is initialized
        if (sdk != null) {
            return sdk;
        }
        // When agent is present, use GlobalOpenTelemetry
        return GlobalOpenTelemetry.get();
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

            builder.addResourceCustomizer(
                    (existing, unused) -> existing.merge(FunctionsResourceDetector.getResource()));

            if (isAppInsightsEnabled()) {
                final String connStr = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
                applyAzureMonitor(builder, connStr);
            }

            sdk = builder.build().getOpenTelemetrySdk();
            GlobalOpenTelemetry.set(sdk);

            LOGGER.info("OpenTelemetry SDK initialised successfully.");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to initialise OpenTelemetry SDK – falling back to no-op", ex);

            sdk = OpenTelemetrySdk.builder().build();
            GlobalOpenTelemetry.set(sdk);
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
