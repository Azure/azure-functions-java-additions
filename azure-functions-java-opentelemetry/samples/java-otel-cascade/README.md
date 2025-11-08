# Azure Functions Java OpenTelemetry Sample

This sample demonstrates how to integrate OpenTelemetry with Azure Functions in Java, showcasing automatic instrumentation and distributed tracing across multiple function calls.

## What This Sample Does

The sample contains three Azure Functions that demonstrate different aspects of OpenTelemetry integration:

1. **HttpTriggerCaller** (`/api/HttpTriggerCaller`) - Makes an HTTP call to another function, demonstrating distributed tracing across function calls
2. **HttpExample** (`/api/HttpExample`) - A simple HTTP trigger that can optionally send messages to a Service Bus queue
3. **ServiceBusProcessor** - Processes messages from the Service Bus queue (requires Service Bus setup)

### Expected Behavior

When you call the functions, you should see:

- **Automatic span creation** for HTTP requests and Service Bus operations
- **Distributed tracing** with proper trace correlation across function calls
- **Azure Functions context attributes** automatically added to spans (function name, invocation ID, host instance, etc.)
- **Log correlation** with OpenTelemetry trace and span IDs
- **Telemetry export** to your configured OpenTelemetry endpoint (e.g., New Relic, Jaeger, Application Insights)

## Dependencies

### Minimum Requirements

- **Azure Functions Java Library**: 3.1.0+
- **Azure Functions OpenTelemetry Library**: 1.1.0+ (minimum required version)

### Required Maven Dependencies

The sample includes these key dependencies in `pom.xml`:

```xml
<dependency>
    <groupId>com.microsoft.azure.functions</groupId>
    <artifactId>azure-functions-java-opentelemetry</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.49.0</version>
</dependency>
```

## Configuration

### Required Environment Variables

Configure these environment variables in your `local.settings.json` for local development or as Application Settings in Azure:

#### Core OpenTelemetry Configuration

```json
{
  "Values": {
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "JAVA_ENABLE_OPENTELEMETRY": "true",
    "OTEL_SERVICE_NAME": "my-java-function-app",
    "OTEL_EXPORTER_OTLP_ENDPOINT": "https://your-otel-endpoint",
    "OTEL_EXPORTER_OTLP_HEADERS": "api-key=your-api-key",
    "OTEL_EXPORTER_OTLP_PROTOCOL": "http/protobuf",
    "OTEL_TRACES_EXPORTER": "otlp",
    "OTEL_LOGS_EXPORTER": "otlp",
    "OTEL_METRICS_EXPORTER": "otlp"
  }
}
```

#### Variable Explanations

- **`JAVA_ENABLE_OPENTELEMETRY`**: Enables OpenTelemetry integration in the Azure Functions Java worker
- **`OTEL_SERVICE_NAME`**: Sets the service name that appears in your telemetry data
- **`OTEL_EXPORTER_OTLP_ENDPOINT`**: The URL of your OpenTelemetry collector or vendor endpoint
- **`OTEL_EXPORTER_OTLP_HEADERS`**: Authentication headers (API keys, tokens) for your telemetry backend
- **`OTEL_EXPORTER_OTLP_PROTOCOL`**: Protocol for OTLP export (`http/protobuf` or `grpc`)
- **`OTEL_*_EXPORTER`**: Configures which telemetry signals to export (`otlp`, `none`, etc.)

### Optional Service Bus Configuration

If using the Service Bus functionality:

```json
{
  "Values": {
    "ServiceBusConnectionString": "Endpoint=sb://your-namespace.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=your-key"
  }
}
```

## OpenTelemetry Agent Options

You have two main options for OpenTelemetry agents, each with different configurations:

### Option 1: Vanilla OpenTelemetry Agent (Recommended)

Use the standard OpenTelemetry Java agent for the most flexibility and vendor-neutral setup.

#### Setup Steps:

1. **Download the OpenTelemetry Java Agent**:
   ```bash
   wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
   ```

2. **Configure local.settings.json**:
   ```json
   {
     "Values": {
       "FUNCTIONS_WORKER_RUNTIME": "java",
       "JAVA_ENABLE_OPENTELEMETRY": "true",
       "APPLICATIONINSIGHTS_ENABLE_AGENT": "false",
       "OTEL_SERVICE_NAME": "my-java-function-app",
       "OTEL_EXPORTER_OTLP_ENDPOINT": "https://otlp.nr-data.net",
       "OTEL_EXPORTER_OTLP_HEADERS": "api-key=your-new-relic-key",
       "OTEL_EXPORTER_OTLP_PROTOCOL": "http/protobuf",
       "OTEL_TRACES_EXPORTER": "otlp",
       "OTEL_LOGS_EXPORTER": "otlp", 
       "OTEL_METRICS_EXPORTER": "otlp",
       "JAVA_OPTS": "-javaagent:opentelemetry-javaagent.jar"
     }
   }
   ```

3. **Key Points**:
   - Set `APPLICATIONINSIGHTS_ENABLE_AGENT=false` to disable the Application Insights agent
   - Use standard OpenTelemetry environment variables
   - Works with any OTLP-compatible backend (New Relic, Jaeger, Grafana, etc.)

### Option 2: Application Insights Agent

Use the Azure Application Insights agent, which includes OpenTelemetry support and automatically sends data to Application Insights.

#### Setup Steps:

1. **Configure local.settings.json**:
   ```json
   {
     "Values": {
       "FUNCTIONS_WORKER_RUNTIME": "java",
       "JAVA_ENABLE_OPENTELEMETRY": "true",
       "APPLICATIONINSIGHTS_CONNECTION_STRING": "InstrumentationKey=your-key;IngestionEndpoint=https://your-region.in.applicationinsights.azure.com/;LiveEndpoint=https://your-region.livediagnostics.monitor.azure.com/",
       "OTEL_SERVICE_NAME": "my-java-function-app",
       "OTEL_EXPORTER_OTLP_ENDPOINT": "https://your-additional-endpoint",
       "OTEL_EXPORTER_OTLP_HEADERS": "api-key=your-api-key",
       "OTEL_EXPORTER_OTLP_PROTOCOL": "http/protobuf",
       "OTEL_TRACES_EXPORTER": "otlp",
       "OTEL_LOGS_EXPORTER": "otlp",
       "OTEL_METRICS_EXPORTER": "otlp"
     }
   }
   ```

2. **Key Points**:
   - **Must include** `APPLICATIONINSIGHTS_CONNECTION_STRING` even if you're primarily using OTLP export
   - Application Insights will receive telemetry for **every request** automatically
   - You can still export to additional OTLP endpoints alongside Application Insights
   - The Application Insights agent includes OpenTelemetry but requires the connection string for proper initialization

## Running the Sample

### Local Development

1. **Clone and build**:
   ```bash
   git clone <repository-url>
   cd azure-functions-java-opentelemetry/samples/java-otel-cascade
   mvn clean package
   ```

2. **Start the Functions runtime**:
   ```bash
   mvn azure-functions:run
   ```

3. **Test the functions**:
   ```bash
   # Test the cascade call (HttpTriggerCaller -> HttpExample)
   curl "http://localhost:7071/api/HttpTriggerCaller?name=TestUser"
   
   # Test direct HTTP call
   curl "http://localhost:7071/api/HttpExample?name=DirectUser"
   ```

## Learn More

- [Azure Functions Java Developer Guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java)
- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Azure Application Insights](https://docs.microsoft.com/en-us/azure/azure-monitor/app/app-insights-overview)