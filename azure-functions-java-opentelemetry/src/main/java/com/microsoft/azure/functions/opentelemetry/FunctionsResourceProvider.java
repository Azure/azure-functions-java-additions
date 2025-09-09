package com.microsoft.azure.functions.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/**
 * SPI-based resource provider for Azure Functions resource detection.
 * 
 * <p>Automatically contributes Azure Functions-specific resource attributes
 * to any OpenTelemetry configuration via the SPI mechanism.
 */
public class FunctionsResourceProvider implements ResourceProvider {

    /**
     * Creates a resource with Azure Functions-specific attributes.
     */
    @Override
    public Resource createResource(ConfigProperties config) {
        return FunctionsResourceDetector.getResource();
    }

    /**
     * Returns the priority order for this resource provider.
     */
    @Override
    public int order() {
        return 100;
    }
}
