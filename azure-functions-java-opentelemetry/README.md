![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

# Azure Functions OpenTelemetry Integration (Java)

This library provides [OpenTelemetry](https://opentelemetry.io/) integration for Java-based Azure Functions when running with an OpenTelemetry agent. It automatically:

1. Creates spans for each function invocation with proper trace context propagation.
2. Provides convenient helper methods for creating custom spans.

**Note:** This library requires an OpenTelemetry Java agent to be present. It does not initialize or configure the OpenTelemetry SDK itself.

---

## Contents

* [Key Classes](#key-classes)
* [Installation](#installation)
* [Prerequisites](#prerequisites)
* [How It Works](#how-it-works)
* [Usage in Azure Functions](#usage-in-azure-functions)
* [Local Development](#local-development)
* [Testing](#testing)

---

## Key Classes

1. **`FunctionsOpenTelemetry`**
   * Provides helper methods like `startSpan(...)` which work with the global OpenTelemetry instance configured by the agent.
   * Accepts either an OpenTelemetry `Context` or an Azure Functions `TraceContext`.

2. **`OpenTelemetryInvocationMiddleware`**
   * Implements Azure Functions middleware (`com.microsoft.azure.functions.internal.spi.middleware.Middleware`).
   * Starts a span for each function invocation and propagates trace context from the Azure Functions host.

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

## Prerequisites

This library requires an OpenTelemetry Java agent to be present and configured. The agent handles:

* OpenTelemetry SDK initialization and configuration
* Exporter setup (e.g., OTLP, Azure Monitor, Jaeger)
* Instrumentation of HTTP clients, databases, etc.

To run your Azure Functions with the agent:

```bash
# Download the OpenTelemetry Java agent
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Run with the agent
func start --java-options="-javaagent:opentelemetry-javaagent.jar"
```

For Azure deployment, configure the agent via application settings:
```
JAVA_OPTS=-javaagent:opentelemetry-javaagent.jar
OTEL_SERVICE_NAME=my-function-app
OTEL_EXPORTER_OTLP_ENDPOINT=https://your-collector-endpoint
```

---

## How It Works

1. **Agent Detection**
   The library checks if an OpenTelemetry agent has configured the global OpenTelemetry instance. If no agent is detected, it throws an `IllegalStateException`.

2. **Span Creation**
   * `OpenTelemetryInvocationMiddleware` automatically creates a span for each function invocation with `faas.invocation_id` and `faas.name` attributes.
   * `FunctionsOpenTelemetry.startSpan(...)` provides convenient methods for creating custom spans.

---

## Usage in Azure Functions

1. **Automatic Middleware**
   The middleware is automatically registered and creates spans for all function invocations.

2. **Custom Spans**
   ```java
   import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;
   import io.opentelemetry.api.trace.Span;
   import io.opentelemetry.api.trace.SpanKind;
   import io.opentelemetry.context.Scope;

   @FunctionName("MyFunction")
   public HttpResponseMessage run(
           @HttpTrigger(name = "req", methods = {HttpMethod.GET}) HttpRequestMessage<Optional<String>> request,
           final ExecutionContext context) {
       
       // Create a custom span
       Span customSpan = FunctionsOpenTelemetry.startSpan(
           "business-logic",
           context.getTraceContext(),
           SpanKind.INTERNAL
       );
       
       try (Scope scope = customSpan.makeCurrent()) {
           // Your business logic here
           customSpan.setAttribute("user.id", "12345");
           return request.createResponseBuilder(HttpStatus.OK).body("Hello World").build();
       } finally {
           customSpan.end();
       }
   }
   ```

---

## Local Development

* Make sure to run with the OpenTelemetry agent even during local development.
* Configure the agent with appropriate exporters for local testing (e.g., console exporter).

---

## Testing

This repo includes unit tests under `src/test/java/...`. Key points:

1. **Agent Simulation**
   Tests may need to mock the global OpenTelemetry instance since the agent won't be present during unit testing.

2. **Disabling Default Exporters**
   We disable exporters in tests to avoid configuration issues:

   ```java
   @BeforeAll
   static void setUp() {
       System.setProperty("otel.metrics.exporter", "none");
       System.setProperty("otel.traces.exporter", "none");
       System.setProperty("otel.logs.exporter", "none");
   }
   ```

3. **Middleware Testing**
   We use [Mockito](https://site.mockito.org/) to mock `MiddlewareContext` and verify the middleware behavior.

---

## Migration from SDK-based Versions

If you were using a previous version that initialized its own SDK:

1. **Remove manual initialization** - Delete any calls to `FunctionsOpenTelemetry.initialize()`
2. **Add OpenTelemetry agent** - Configure your deployment to use the OpenTelemetry Java agent
3. **Move configuration to agent** - Move exporter and instrumentation configuration to agent properties (environment variables starting with `OTEL_`)

The span creation APIs remain unchanged, so your custom instrumentation code should continue to work.

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
