![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/master/src/Azure.Functions.Cli/npm/assets/azure-functions-logo-color-raster.png)

# Azure Functions OpenTelemetry Integration (Java)

This library integrates [OpenTelemetry](https://opentelemetry.io/) with Java-based Azure Functions. It automatically:

1. Initializes the OpenTelemetry SDK on-demand.
2. Merges resource attributes for Azure Functions (e.g., function name, region, resource group).
3. Optionally configures Azure Monitor if the environment variable `APPLICATIONINSIGHTS_CONNECTION_STRING` is set.
4. Provides a middleware that creates a new span for each function invocation.

---

## Contents

* [Key Classes](#key-classes)
* [Installation](#installation)
* [How It Works](#how-it-works)
* [Azure Monitor Integration (Optional)](#azure-monitor-integration-optional)
* [Usage in Azure Functions](#usage-in-azure-functions)
* [Local Development](#local-development)
* [Testing](#testing)

---

## Key Classes

1. **`FunctionsOpenTelemetry`**

  * Provides a static `sdk()` method to lazily initialize the SDK.
  * Offers helper methods like `startSpan(...)` which accept either an OpenTelemetry `Context` or an Azure Functions `TraceContext`.

2. **`FunctionsResourceDetector`**

  * Detects standard Azure Functions environment variables (e.g. `WEBSITE_SITE_NAME`) and builds resource attributes like `service.name`, `cloud.provider`, etc.
  * Fallbacks to `java-function-app` for `service.name` if not running on Azure.

3. **`OpenTelemetryInvocationMiddleware`**

  * Implements Azure Functions middleware (`com.microsoft.azure.functions.internal.spi.middleware.Middleware`).
  * Initializes a static OTel SDK during when it is loaded in by the Java Worker during Worker Initialization. 
  * Starts a span on each function invocation and propagates existing trace context from the Azure Functions host.

---

## Installation

Add the library to your `pom.xml`:

```xml
<dependency>
  <groupId>com.microsoft.azure.functions</groupId>
  <artifactId>azure-functions-java-opentelemetry</artifactId>
  <version>1.0.0</version>
</dependency>
```

(*Adjust the version as needed.*)

---

## How It Works

1. **Lazy Initialization**
   When you first call `FunctionsOpenTelemetry.sdk()`, it constructs an `OpenTelemetrySdk` via `AutoConfiguredOpenTelemetrySdk.builder()`.

  * Merges Azure Functions resource attributes.
  * Registers a shutdown hook to cleanly close the tracer provider on JVM exit.

2. **Span Creation**

  * `FunctionsOpenTelemetry.startSpan(...)` can start spans for custom logic.
  * `OpenTelemetryInvocationMiddleware` automatically starts a span for each function invocation and tags it with `faas.invocation_id` and `faas.name`.

---

## Azure Monitor Integration (Optional)

If you set `APPLICATIONINSIGHTS_CONNECTION_STRING` and also set `JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY=true`, this library tries to reflectively call `com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure`.

* If that class is found, Azure Monitor is configured to export traces, metrics, and logs.
* If not present, the initialization logs a warning and continues without Azure Monitor.

This approach avoids a hard compile-time dependency on the Azure Monitor library.

---

## Usage in Azure Functions

1. **Middleware Registration**

  * Azure Functions Java Worker auto-discovers middleware. Make sure `OpenTelemetryInvocationMiddleware` is on your classpath.
  * Once loaded, each invocation automatically has a new span, parented by any incoming trace context from the host.

2. **Custom Spans**

   ```java
   Span customSpan = FunctionsOpenTelemetry.startSpan(
       "myTracer",
       "mySpan",
       someTraceContext,   // or null
       SpanKind.INTERNAL   // or null -> defaults to INTERNAL
   );
   try (Scope scope = customSpan.makeCurrent()) {
       // do your work
   } finally {
       customSpan.end();
   }
   ```

---

## Local Development

* If `WEBSITE_SITE_NAME` is not set, `FunctionsResourceDetector` defaults `service.name` to `"java-function-app"`.
* All other Azure-specific attributes (e.g., region, resource group) remain unset if the corresponding environment variables do not exist.
* You do **not** need to set Azure Monitor or any other exporters unless you want local telemetry exporting.

---

## Testing

This repo includes simple unit tests under `src/test/java/...`. Key points:

1. **Disabling Default Exporters**
   If OpenTelemetry auto-configuration tries to enable exporters you haven’t added, you may get a `ConfigurationException`. We disable them in tests via:

   ```java
   @BeforeAll
   static void disableUnusedExporters() {
       System.setProperty("otel.metrics.exporter", "none");
       System.setProperty("otel.traces.exporter", "none");
       System.setProperty("otel.logs.exporter", "none");
   }
   ```

2. **Mocking Environment Variables**
   We use [System Stubs](https://github.com/webcompere/system-stubs) to temporarily override `System.getenv()` in tests. Example:

   ```java
   @SystemStub
   private EnvironmentVariables environment;

   @Test
   void testResourceDetection() {
       environment.set("WEBSITE_SITE_NAME", "myFunctionApp");
       // ...
   }
   ```

   This allows verifying resource attributes without polluting global environment variables.

3. **Middleware Testing**
   We use [Mockito](https://site.mockito.org/) to mock `MiddlewareContext` and confirm `OpenTelemetryInvocationMiddleware` properly invokes the chain and handles exceptions.