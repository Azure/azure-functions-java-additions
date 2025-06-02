package com.microsoft.azure.functions.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Detects a set of well-known Azure Functions environment variables and maps them
 * to <a href="https://opentelemetry.io/docs/specs/semconv/resource/">OpenTelemetry
 * semantic resource attributes</a>.
 * <p>
 * The detector is intentionally simple—no network calls or reflection—so it can
 * be invoked very early in the worker start-up sequence. When running locally,
 * only {@code service.name} is set; when running on the platform, additional
 * cloud-specific attributes are included.
 * </p>
 */
public final class FunctionsResourceDetector {

    /** {@code cloud.provider} – fixed to {@code "azure"} when running on Azure. */
    public static final String CLOUD_PROVIDER = "cloud.provider";
    /** {@code cloud.platform} – {@code "azure_functions"} for hosted apps. */
    public static final String CLOUD_PLATFORM = "cloud.platform";
    /** {@code cloud.region} – Azure region (e.g. {@code westus2}). */
    public static final String CLOUD_REGION = "cloud.region";
    /** {@code cloud.resource.id} – Full ARM resource ID of the function app. */
    public static final String CLOUD_RESOURCE_ID = "cloud.resource.id";
    /** {@code deployment.environment} – Function slot name (e.g. {@code production}). */
    public static final String DEPLOYMENT_ENVIRONMENT = "deployment.environment";
    /** {@code service.name} – Logical service identifier (function-app name). */
    public static final String SERVICE_NAME = "service.name";

    // ─── Well-known Azure Functions environment variables ────────────────────────
    public static final String WEBSITE_SITE_NAME    = "WEBSITE_SITE_NAME";
    public static final String REGION_NAME          = "REGION_NAME";
    public static final String WEBSITE_RESOURCE_GROUP = "WEBSITE_RESOURCE_GROUP";
    public static final String WEBSITE_OWNER_NAME   = "WEBSITE_OWNER_NAME";
    public static final String WEBSITE_SLOT_NAME    = "WEBSITE_SLOT_NAME";

    /**
     * Builds an {@link io.opentelemetry.sdk.resources.Resource Resource} populated
     * with attributes derived from the current process environment.
     *
     * @return a new immutable {@code Resource} instance.
     *         <ul>
     *           <li>Always contains at least {@code service.name}.</li>
     *           <li>Contains {@code cloud.*} attributes when running on Azure Functions.</li>
     *           <li>Defaults {@code deployment.environment} to {@code production} if the
     *               slot name is not present.</li>
     *         </ul>
     */
    public static Resource getResource() {

        final String siteName      = System.getenv(WEBSITE_SITE_NAME);
        final String region        = System.getenv(REGION_NAME);
        final String resourceGroup = System.getenv(WEBSITE_RESOURCE_GROUP);
        final String ownerName     = System.getenv(WEBSITE_OWNER_NAME);
        String slotName      = System.getenv(WEBSITE_SLOT_NAME);

        final AttributesBuilder attrBuilder = Attributes.builder();

        // ─── Basic service + cloud metadata ──────────────────────────────────────
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

        // Construct fully-qualified ARM resource ID when all pieces are present
        final String subscriptionId = extractSubscriptionId(ownerName);
        if (subscriptionId != null && resourceGroup != null && siteName != null) {
            String resourceId = String.format(
                    "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Web/sites/%s",
                    subscriptionId, resourceGroup, siteName);
            attrBuilder.put(CLOUD_RESOURCE_ID, resourceId);
        }

        // ─── Deployment slot / environment ───────────────────────────────────────
        if (slotName == null || slotName.isEmpty()) {
            slotName = "production";
        }
        attrBuilder.put(DEPLOYMENT_ENVIRONMENT, slotName);

        return Resource.create(attrBuilder.build());
    }

    /**
     * Utility that parses {@code WEBSITE_OWNER_NAME} to extract the subscription ID.
     * <p>The variable normally has the form {@code <subscriptionId>+<stamp>}.</p>
     *
     * @param ownerName the raw {@code WEBSITE_OWNER_NAME} value
     * @return the subscription ID, or {@code null} if it cannot be parsed
     */
    public static String extractSubscriptionId(final String ownerName) {
        if (ownerName == null || ownerName.isEmpty()) {
            return null;
        }
        final int idx = ownerName.indexOf('+');
        return (idx > 0) ? ownerName.substring(0, idx) : null;
    }

    // Prevent instantiation
    private FunctionsResourceDetector() { }
}
