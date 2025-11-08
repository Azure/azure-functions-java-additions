![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

# Azure Functions OpenTelemetry Integration (Java)

This library provides [OpenTelemetry](https://opentelemetry.io/) integration for Java-based Azure Functions when running with an OpenTelemetry agent. It automatically:

1. Creates spans for each function invocation with proper trace context propagation.
2. Provides convenient helper methods for creating custom spans with Azure Functions context.
3. Extracts Azure Functions context attributes for log correlation and observability.

**Note:** This library requires an OpenTelemetry Java agent to be present. It does not initialize or configure the OpenTelemetry SDK itself.

---

## Contents

* [Key Classes](#key-classes)
* [Installation](#installation)
* [Prerequisites](#prerequisites)
* [How It Works](#how-it-works)
* [Usage in Azure Functions](#usage-in-azure-functions)
* [Azure Functions Context](#azure-functions-context)
* [Local Development](#local-development)
* [Testing](#testing)
* [Migration from Previous Versions](#migration-from-previous-versions)

---

## Key Classes

1. **`FunctionsOpenTelemetry`**
   * `startSpan(String spanName, ExecutionContext executionContext, SpanKind kind)` - Creates spans with Azure Functions context
   * `getAzureContext(ExecutionContext executionContext)` - Extracts Azure Functions context attributes for logging
   * `getOpenTelemetry()` - Returns the global OpenTelemetry instance configured by the agent

2. **`OpenTelemetryInvocationMiddleware`**
   * Implements Azure Functions middleware (`com.microsoft.azure.functions.internal.spi.middleware.Middleware`).
   * Automatically starts a span for each function invocation and propagates trace context.

---

## Installation

Add the library to your `pom.xml`:

```xml
<dependency>
  <groupId>com.microsoft.azure.functions</groupId>
  <artifactId>azure-functions-java-opentelemetry</artifactId>
  <version>1.1.0</version>
</dependency>
```

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

2. **Automatic Middleware**
   * `OpenTelemetryInvocationMiddleware` automatically creates a span for each function invocation.
   * Extracts trace context from the Azure Functions host and propagates it.
   * Sets Azure Functions attributes like `faas.name`, `faas.invocation_id`, and `faas.instance`.

3. **Custom Span Creation**
   * `startSpan(...)` creates custom spans with automatic Azure Functions context attributes.
   * `getAzureContext(...)` extracts context attributes for structured logging and correlation.

---

## Usage in Azure Functions

### 1. Automatic Middleware
The middleware is automatically registered and creates spans for all function invocations. No code changes required.

### 2. Custom Spans
```java
import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;

@FunctionName("MyFunction")
public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
    
    // Create a custom span with Azure Functions context
    Span customSpan = FunctionsOpenTelemetry.startSpan(
        "business-logic",
        context,  // ExecutionContext provides trace context and function metadata
        SpanKind.INTERNAL
    );
    
    try (Scope scope = customSpan.makeCurrent()) {
        // Your business logic here
        customSpan.setAttribute("user.id", "12345");
        
        // Azure Functions attributes are automatically added:
        // - faas.name (function name)
        // - faas.invocation_id 
        // - faas.instance (host instance ID)
        // - service.name (from Azure resource detection)
        
        return request.createResponseBuilder(HttpStatus.OK)
            .body("Hello World")
            .build();
    } finally {
        customSpan.end();
    }
}
```

### 3. Azure Functions Context for Logging

Extract Azure Functions context attributes for structured logging and correlation:

```java
import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;

@FunctionName("MyFunction")
public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}) 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
    
    // Get Azure Functions context attributes
    Map<String, String> azureContext = FunctionsOpenTelemetry.getAzureContext(context);
    
    // Use with structured logging frameworks
    context.getLogger().info("Processing request with context: " + azureContext);
    
    // Available attributes include:
    // - faas.name: Function name
    // - faas.invocation_id: Unique invocation ID
    // - faas.instance: Host instance ID (from trace context)
    // - process.pid: Process ID (from trace context)
    // - #AzFuncLiveLogsSessionId: Live logs session ID (from trace context)
    // - service.name: Azure Functions app name
    // - cloud.provider: "azure"
    // - cloud.region: Azure region
    
    return request.createResponseBuilder(HttpStatus.OK)
        .body("Hello World")
        .build();
}
```

---

## Azure Functions Context

The library automatically extracts and provides Azure Functions context attributes:

### Resource Attributes (cached, shared across invocations)
- `service.name`: Azure Functions app name
- `cloud.provider`: "azure"  
- `cloud.region`: Azure region
- `cloud.resource_id`: Azure resource ID

### Function-Specific Attributes (per invocation)
- `faas.name`: Function name
- `faas.invocation_id`: Unique invocation ID

### Trace Context Attributes (from Azure Functions host)
- `faas.instance`: Host instance ID
- `process.pid`: Process ID  
- `#AzFuncLiveLogsSessionId`: Live logs session ID for debugging

These attributes are automatically added to custom spans created with `startSpan()` and are available via `getAzureContext()` for logging correlation.

---

## Local Development

### Configure OpenTelemetry Agent

Make sure to run with the OpenTelemetry agent even during local development:

```bash
# Download the OpenTelemetry Java agent
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Configure local.settings.json
{
  "Values": {
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "JAVA_ENABLE_OPENTELEMETRY": "true",
    "JAVA_OPTS": "-javaagent:opentelemetry-javaagent.jar",
    "OTEL_SERVICE_NAME": "my-function-app-local",
    "OTEL_EXPORTER_OTLP_ENDPOINT": "http://localhost:4317",
    "OTEL_TRACES_EXPORTER": "otlp"
  }
}

# Run Azure Functions locally
func start
```

### Local Testing with Console Exporter

For local development, you can use the console exporter to see traces in the console:

```bash
# Set environment variables for console output
export OTEL_TRACES_EXPORTER=console
export OTEL_METRICS_EXPORTER=console
export OTEL_LOGS_EXPORTER=console

# Or in local.settings.json
{
  "Values": {
    "OTEL_TRACES_EXPORTER": "console",
    "OTEL_METRICS_EXPORTER": "console", 
    "OTEL_LOGS_EXPORTER": "console"
  }
}
```

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
