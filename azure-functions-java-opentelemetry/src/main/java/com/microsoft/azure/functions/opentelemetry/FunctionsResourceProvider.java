package com.microsoft.azure.functions.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Resource provider that integrates Azure Functions-specific resource detection
 * with OpenTelemetry agents via SPI (Service Provider Interface).
 * 
 * This provider is automatically discovered by OpenTelemetry agents and
 * AutoConfiguredOpenTelemetrySdk, ensuring Functions-specific attributes
 * are included regardless of how OpenTelemetry is initialized.
 */
public class FunctionsResourceProvider implements ResourceProvider {

    @Override
    public Resource createResource(ConfigProperties config) {
        return FunctionsResourceDetector.getResource();
    }

    @Override
    public int order() {
        // Return a higher priority to ensure Functions attributes take precedence
        // over default resource detection
        return 100;
    }
}
