package com.microsoft.azure.functions.opentelemetry.tests;

import com.microsoft.azure.functions.opentelemetry.FunctionsResourceDetector;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


@ExtendWith(SystemStubsExtension.class)
public class FunctionsResourceDetectorTest {
    private static final AttributeKey<String> SERVICE_NAME_KEY =
            AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> CLOUD_PROVIDER_KEY =
            AttributeKey.stringKey("cloud.provider");
    private static final AttributeKey<String> CLOUD_PLATFORM_KEY =
            AttributeKey.stringKey("cloud.platform");
    private static final AttributeKey<String> CLOUD_REGION_KEY =
            AttributeKey.stringKey("cloud.region");
    private static final AttributeKey<String> DEPLOYMENT_ENV_KEY =
            AttributeKey.stringKey("deployment.environment");
    private static final AttributeKey<String> CLOUD_RESOURCE_ID_KEY =
            AttributeKey.stringKey("cloud.resource.id");

    @SystemStub
    private EnvironmentVariables environment;

    @Test
    void testResourceWithoutAnyEnvironmentVariables() {
        Resource resource = FunctionsResourceDetector.getResource();
        Attributes attrs = resource.getAttributes();

        String serviceName = attrs.get(SERVICE_NAME_KEY);
        assertEquals("java-function-app", serviceName,
                "Should fall back to 'java-function-app' if WEBSITE_SITE_NAME is not set");

        // None of these should be set if we don't have Azure environment variables
        assertNull(attrs.get(CLOUD_PROVIDER_KEY));
        assertNull(attrs.get(CLOUD_PLATFORM_KEY));

        // Should default to 'production' if WEBSITE_SLOT_NAME is not set
        String env = attrs.get(DEPLOYMENT_ENV_KEY);
        assertEquals("production", env);
    }

    @Test
    void testResourceWithBasicAzureVariables() {
        // The @SystemStub annotation is crucial!
        environment.set("WEBSITE_SITE_NAME", "myFunctionApp");
        environment.set("REGION_NAME", "CentralUS");
        environment.set("WEBSITE_SLOT_NAME", "staging");
        // Not setting WEBSITE_OWNER_NAME or WEBSITE_RESOURCE_GROUP => no subscription ID

        Resource resource = FunctionsResourceDetector.getResource();
        Attributes attrs = resource.getAttributes();

        assertEquals("myFunctionApp", attrs.get(SERVICE_NAME_KEY));
        assertEquals("azure", attrs.get(CLOUD_PROVIDER_KEY));
        assertEquals("azure_functions", attrs.get(CLOUD_PLATFORM_KEY));
        assertEquals("CentralUS", attrs.get(CLOUD_REGION_KEY));
        assertEquals("staging", attrs.get(DEPLOYMENT_ENV_KEY));
        assertNull(attrs.get(CLOUD_RESOURCE_ID_KEY),
                "No resource ID if we have no subscription or resource group");
    }

    @Test
    void testResourceWithSubscriptionAndResourceGroup() {
        environment.set("WEBSITE_SITE_NAME", "siteWithSub");
        environment.set("REGION_NAME", "EastUS");
        environment.set("WEBSITE_RESOURCE_GROUP", "myRg");
        // Typically looks like <subscription>+<something else>
        environment.set("WEBSITE_OWNER_NAME", "12345678-abcd-90ef-1234-5678abcd90ef+random");

        Resource resource = FunctionsResourceDetector.getResource();
        Attributes attrs = resource.getAttributes();

        assertEquals("siteWithSub", attrs.get(SERVICE_NAME_KEY));
        assertEquals("azure", attrs.get(CLOUD_PROVIDER_KEY));
        assertEquals("azure_functions", attrs.get(CLOUD_PLATFORM_KEY));
        assertEquals("EastUS", attrs.get(CLOUD_REGION_KEY));

        String expectedId = "/subscriptions/12345678-abcd-90ef-1234-5678abcd90ef/resourceGroups/myRg"
                + "/providers/Microsoft.Web/sites/siteWithSub";
        String actualId = attrs.get(CLOUD_RESOURCE_ID_KEY);
        assertEquals(expectedId, actualId, "Should build correct resource ID");
    }

    @Test
    void testResourceWithNullSlot() {
        environment.set("WEBSITE_SITE_NAME", "someSite");
        // Not setting WEBSITE_SLOT_NAME => default to 'production'

        Resource resource = FunctionsResourceDetector.getResource();
        Attributes attrs = resource.getAttributes();

        assertEquals("someSite", attrs.get(SERVICE_NAME_KEY));
        assertEquals("azure", attrs.get(CLOUD_PROVIDER_KEY));
        assertEquals("azure_functions", attrs.get(CLOUD_PLATFORM_KEY));
        assertEquals("production", attrs.get(DEPLOYMENT_ENV_KEY),
                "Should fall back to 'production' for slot");
    }
}
