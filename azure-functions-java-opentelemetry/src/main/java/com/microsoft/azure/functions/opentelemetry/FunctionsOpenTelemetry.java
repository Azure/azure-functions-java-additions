package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.TraceContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class FunctionsOpenTelemetry {


    private static Logger LOGGER = null;
    private static volatile OpenTelemetrySdk sdk;

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    /* ─────────────────────────────  SDK (lazy-init)  ─────────────────────────── */
    public static OpenTelemetrySdk sdk() {
        if (sdk == null) {
            synchronized (FunctionsOpenTelemetry.class) {
                if (sdk == null) sdk = buildSdk();
            }
        }
        return sdk;
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

        AutoConfiguredOpenTelemetrySdkBuilder builder =
                AutoConfiguredOpenTelemetrySdk.builder();

        /* 1) Always merge the Azure Functions resource attributes */
        builder.addResourceCustomizer(
                (existing, unused) -> existing.merge(FunctionsResourceDetector.getResource()));

        /* 2) Conditionally add Azure Monitor */
        String connStr =
                System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");

        if (!connStr.isEmpty()) {
            applyAzureMonitor(builder, connStr);
//            AzureMonitorAutoConfigure.customize(builder, connStr);
        }

        /* 3) Build, register globally, add shutdown hook */
        OpenTelemetrySdk sdk = builder.build().getOpenTelemetrySdk();
        GlobalOpenTelemetry.set(sdk);

        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> sdk.getSdkTracerProvider().shutdown()));

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

    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER = new TextMapGetter<TraceContext>() {
        @Override public Iterable<String> keys(TraceContext t) { return t.getAttributes().keySet(); }
        @Override public String get(TraceContext t, String key) {
            if (t == null) return null;
            if ("traceparent".equalsIgnoreCase(key)) return t.getTraceparent();
            if ("tracestate".equalsIgnoreCase(key)) return t.getTracestate();
            return t.getAttributes().get(key);
        }
    };

    public static Span startSpan(
            String tracerName,
            String spanName,
            Context parent,
            SpanKind kind) {

        return sdk().getTracer(tracerName)
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

        Context parent = sdk().getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), traceContext, TRACE_CONTEXT_GETTER);
        return startSpan(tracerName, spanName, parent, kind);
    }
}
