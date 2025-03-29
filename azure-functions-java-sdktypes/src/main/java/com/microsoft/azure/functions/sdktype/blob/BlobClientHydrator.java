package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reflection logic for building a BlobClient from BlobClientMetaData.
 * Supports both connection strings and managed identity (with fallback to shaded azure-identity if not present).
 */
public class BlobClientHydrator implements SdkTypeHydrator<BlobClientMetaData> {
    private static final Logger LOGGER = Logger.getLogger(BlobClientHydrator.class.getName());

    @Override
    public Object createInstance(BlobClientMetaData metaData) throws Exception {
        LOGGER.info("Starting BlobClientHydrator.createInstance()");

        // Gather fields from metaData
        String containerName = metaData.getContainerName();
        String blobName = metaData.getBlobName();
        String envVar = metaData.getConnectionEnvVar();
        String configValue = System.getenv(envVar);

        if (configValue == null || configValue.isEmpty()) {
            throw new SdkHydrationException("No environment variable set for: " + envVar);
        }

        // Step 1: Reflectively load com.azure.storage.blob.BlobClientBuilder
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> blobBuilderClass = classLoader.loadClass("com.azure.storage.blob.BlobClientBuilder");
        Object blobBuilder = blobBuilderClass.getDeclaredConstructor().newInstance();

        // Step 2: If configValue is a connection string, do the existing approach
        if (isConnectionString(configValue)) {
            LOGGER.info("Detected connection string usage from: " + envVar);

            Method conn = blobBuilderClass.getMethod("connectionString", String.class);
            conn.invoke(blobBuilder, configValue);

            Method cont = blobBuilderClass.getMethod("containerName", String.class);
            cont.invoke(blobBuilder, containerName);

            Method bName = blobBuilderClass.getMethod("blobName", String.class);
            bName.invoke(blobBuilder, blobName);
        } else {
            LOGGER.info("Detected Managed Identity usage with prefix: " + envVar);

            // Attempt to load 'accountName', 'serviceUri', 'blobServiceUri', 'clientId' from prefix
            final String accountName = System.getenv(envVar + "__accountName");
            final String serviceUri = System.getenv(envVar + "__serviceUri");
            final String blobServiceUri = System.getenv(envVar + "__blobServiceUri");
            final String clientId = System.getenv(envVar + "__clientId");

            // Resolve the endpoint
            String endpoint = resolveEndpoint(accountName, serviceUri, blobServiceUri);

            // Build the credential (DefaultAzureCredential) reflectively
            Object credential = buildManagedIdentityCredential(classLoader, clientId);

            // Now call builder.credential(...) and builder.endpoint(...)
            // NOTE: For this reflection, we need the 'TokenCredential' class
            Class<?> tokenCredClass = classLoader.loadClass("com.azure.core.credential.TokenCredential");

            Method credentialMethod = blobBuilderClass.getMethod("credential", tokenCredClass);
            credentialMethod.invoke(blobBuilder, credential);

            Method endpointMethod = blobBuilderClass.getMethod("endpoint", String.class);
            endpointMethod.invoke(blobBuilder, endpoint);

            // Also set container & blob names
            Method cont = blobBuilderClass.getMethod("containerName", String.class);
            cont.invoke(blobBuilder, containerName);

            Method bName = blobBuilderClass.getMethod("blobName", String.class);
            bName.invoke(blobBuilder, blobName);
        }

        // Step 3: finally build the client
        Method buildMethod = blobBuilderClass.getMethod("buildClient");
        Object blobClient = buildMethod.invoke(blobBuilder);

        LOGGER.info("Successfully created BlobClient instance via reflection.");
        return blobClient;
    }

    /**
     * Decide if configValue is likely a connection string by checking for well-known keywords.
     */
    private boolean isConnectionString(String value) {
        return value.contains("AccountKey=")
                || value.contains("DefaultEndpointsProtocol=")
                || value.contains("UseDevelopmentStorage=true");
    }

    /**
     * Resolves the endpoint for managed identity from environment variables, or throws if none found.
     */
    private String resolveEndpoint(String accountName, String serviceUri, String blobServiceUri) {
        if (accountName != null && !accountName.isEmpty()) {
            String ep = String.format("https://%s.blob.core.windows.net", accountName);
            LOGGER.info("Resolved endpoint from accountName: " + ep);
            return ep;
        }
        if (blobServiceUri != null && !blobServiceUri.isEmpty()) {
            LOGGER.info("Resolved endpoint from blobServiceUri: " + blobServiceUri);
            return blobServiceUri;
        }
        if (serviceUri != null && !serviceUri.isEmpty()) {
            LOGGER.info("Resolved endpoint from serviceUri: " + serviceUri);
            return serviceUri;
        }
        throw new SdkHydrationException("Missing accountName, blobServiceUri, or serviceUri for the managed identity scenario.");
    }

    /**
     * Build the DefaultAzureCredential (or shaded fallback) reflectively, including user-assigned clientId if present.
     */
    private Object buildManagedIdentityCredential(ClassLoader classLoader, String clientId) throws Exception {
        LOGGER.info("Attempting to build DefaultAzureCredential reflectively.");

        Class<?> builderClass = classLoader.loadClass("com.azure.identity.DefaultAzureCredentialBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        if (clientId != null && !clientId.isEmpty()) {
            LOGGER.info("Using user-assigned managed identity: " + clientId);
            // reflectively call .managedIdentityClientId(clientId)
            Method micidMethod = builderClass.getMethod("managedIdentityClientId", String.class);
            micidMethod.invoke(builder, clientId);
        } else {
            LOGGER.info("Using system-assigned managed identity (no clientId).");
        }
        // call build() to get the credential
        Method buildMethod = builderClass.getMethod("build");
        return buildMethod.invoke(builder);
    }
}
