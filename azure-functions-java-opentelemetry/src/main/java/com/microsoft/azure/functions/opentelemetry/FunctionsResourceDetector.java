/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Detects Azure Functions environment variables and maps them to OpenTelemetry resource attributes.
 * 
 * <p>This detector implements Azure Functions resource detection according to the
 * <a href="https://opentelemetry.io/docs/specs/semconv/resource/">OpenTelemetry
 * semantic resource conventions</a>. It provides a lightweight, no-dependency approach
 * to resource detection that can be invoked early in the worker startup sequence.
 * 
 * <p>The detector operates in two modes:
 * <ul>
 *   <li><strong>Local development:</strong> Only {@code service.name} is set to a default value</li>
 *   <li><strong>Azure-hosted:</strong> Full cloud resource attributes are populated from environment variables</li>
 * </ul>
 * 
 * <p>Detected attributes follow OpenTelemetry semantic conventions:
 * <ul>
 *   <li>{@code service.name} - Logical service identifier (function app name)</li>
 *   <li>{@code cloud.provider} - Cloud provider ("azure")</li>
 *   <li>{@code cloud.platform} - Cloud platform ("azure_functions")</li>
 *   <li>{@code cloud.region} - Azure region (e.g., "westus2")</li>
 *   <li>{@code cloud.resource.id} - Full ARM resource ID</li>
 *   <li>{@code deployment.environment} - Deployment slot name</li>
 * </ul>
 */
public final class FunctionsResourceDetector {

    // OpenTelemetry semantic convention attribute names
    
    /** OpenTelemetry attribute: {@code cloud.provider} - Cloud provider name. */
    public static final String CLOUD_PROVIDER = "cloud.provider";
    
    /** OpenTelemetry attribute: {@code cloud.platform} - Cloud platform identifier. */
    public static final String CLOUD_PLATFORM = "cloud.platform";
    
    /** OpenTelemetry attribute: {@code cloud.region} - Cloud region identifier. */
    public static final String CLOUD_REGION = "cloud.region";
    
    /** OpenTelemetry attribute: {@code cloud.resource.id} - Cloud resource identifier. */
    public static final String CLOUD_RESOURCE_ID = "cloud.resource.id";
    
    /** OpenTelemetry attribute: {@code deployment.environment} - Deployment environment name. */
    public static final String DEPLOYMENT_ENVIRONMENT = "deployment.environment";
    
    /** OpenTelemetry attribute: {@code service.name} - Logical service name. */
    public static final String SERVICE_NAME = "service.name";

    // Azure Functions environment variable names
    
    /** Azure Functions environment variable: Function app name. */
    public static final String WEBSITE_SITE_NAME    = "WEBSITE_SITE_NAME";
    
    /** Azure Functions environment variable: Azure region name. */
    public static final String REGION_NAME          = "REGION_NAME";
    
    /** Azure Functions environment variable: Resource group name. */
    public static final String WEBSITE_RESOURCE_GROUP = "WEBSITE_RESOURCE_GROUP";
    
    /** Azure Functions environment variable: Subscription and stamp information. */
    public static final String WEBSITE_OWNER_NAME   = "WEBSITE_OWNER_NAME";
    
    /** Azure Functions environment variable: Deployment slot name. */
    public static final String WEBSITE_SLOT_NAME    = "WEBSITE_SLOT_NAME";

    /**
     * Creates a Resource populated with Azure Functions-specific attributes.
     * 
     * <p>This method examines well-known Azure Functions environment variables and
     * maps them to OpenTelemetry semantic resource attributes. The detection is
     * designed to be lightweight and free of external dependencies.
     * 
     * <p>Resource construction behavior:
     * <ul>
     *   <li><strong>Always includes:</strong> {@code service.name} (function app name or default)</li>
     *   <li><strong>Azure-hosted apps:</strong> Cloud provider, platform, region, and resource ID</li>
     *   <li><strong>Local development:</strong> Only basic service identification</li>
     *   <li><strong>Deployment environment:</strong> Slot name or "production" default</li>
     * </ul>
     *
     * @return an immutable Resource containing detected attributes
     */
    public static Resource getResource() {
        final String siteName      = System.getenv(WEBSITE_SITE_NAME);
        final String region        = System.getenv(REGION_NAME);
        final String resourceGroup = System.getenv(WEBSITE_RESOURCE_GROUP);
        final String ownerName     = System.getenv(WEBSITE_OWNER_NAME);
        String slotName      = System.getenv(WEBSITE_SLOT_NAME);

        final AttributesBuilder attrBuilder = Attributes.builder();

        // Service identification and cloud metadata
        if (siteName != null && !siteName.isEmpty()) {
            attrBuilder.put(SERVICE_NAME, siteName)
                    .put(CLOUD_PROVIDER, "azure")
                    .put(CLOUD_PLATFORM, "azure_functions");
        } else {
            // Local execution (func host, unit tests, etc.)
            attrBuilder.put(SERVICE_NAME, "java-function-app");
        }

        if (region != null && !region.isEmpty()) {
            attrBuilder.put(CLOUD_REGION, region);
        }

        // Construct fully-qualified ARM resource ID when all components are available
        final String subscriptionId = extractSubscriptionId(ownerName);
        if (subscriptionId != null && resourceGroup != null && siteName != null) {
            final String resourceId = String.format(
                    "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Web/sites/%s",
                    subscriptionId, resourceGroup, siteName);
            attrBuilder.put(CLOUD_RESOURCE_ID, resourceId);
        }

        // Deployment environment (slot name)
        if (slotName == null || slotName.isEmpty()) {
            slotName = "production";
        }
        attrBuilder.put(DEPLOYMENT_ENVIRONMENT, slotName);

        return Resource.create(attrBuilder.build());
    }

    /**
     * Extracts the Azure subscription ID from the WEBSITE_OWNER_NAME environment variable.
     * 
     * <p>The {@code WEBSITE_OWNER_NAME} variable typically contains the subscription ID
     * followed by a stamp identifier in the format: {@code <subscriptionId>+<stamp>}.
     * This method extracts only the subscription ID portion.
     *
     * @param ownerName the raw value of the WEBSITE_OWNER_NAME environment variable
     * @return the subscription ID if successfully parsed, null otherwise
     */
    public static String extractSubscriptionId(final String ownerName) {
        if (ownerName == null || ownerName.isEmpty()) {
            return null;
        }
        final int idx = ownerName.indexOf('+');
        return (idx > 0) ? ownerName.substring(0, idx) : null;
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FunctionsResourceDetector() { }
}
