package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reflection logic for building a BlobContainerClient from BlobContainerMetaData,
 * supporting both connection string usage and managed identity usage.
 */
public class BlobContainerHydrator implements SdkTypeHydrator<BlobContainerMetaData> {
    private static final Logger LOGGER = Logger.getLogger(BlobContainerHydrator.class.getName());

    @Override
    public Object createInstance(BlobContainerMetaData metaData) throws Exception {
        String containerName = metaData.getContainerName();
        String envVar = metaData.getConnectionEnvVar();

        LOGGER.info("BlobContainerHydrator: Using environment variable name as prefix: " + envVar);

        // Check if environment variable => connection string
        String maybeConnString = System.getenv(envVar);
        if (maybeConnString != null && isConnectionString(maybeConnString)) {
            LOGGER.info("Detected connection string usage from environment variable: " + envVar);
            return buildWithConnectionString(containerName, maybeConnString);
        } else {
            // interpret envVar as prefix for managed identity
            LOGGER.info("Detected Managed Identity usage. Prefix: " + envVar);

            final String accountName = System.getenv(envVar + "__accountName");
            final String serviceUri = System.getenv(envVar + "__serviceUri");
            final String blobServiceUri = System.getenv(envVar + "__blobServiceUri");
            final String clientId = System.getenv(envVar + "__clientId");

            String endpoint = resolveEndpoint(accountName, serviceUri, blobServiceUri);
            Object credential = buildManagedIdentityCredential(clientId);

            return buildWithManagedIdentity(containerName, endpoint, credential);
        }
    }

    private boolean isConnectionString(String val) {
        return val.contains("AccountKey=")
                || val.contains("DefaultEndpointsProtocol=")
                || val.contains("UseDevelopmentStorage=true");
    }

    private Object buildWithConnectionString(String containerName, String connStr) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobContainerClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        Method connMethod = builderClass.getMethod("connectionString", String.class);
        connMethod.invoke(builder, connStr);

        Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, containerName);

        Method buildM = builderClass.getMethod("buildClient");
        Object containerClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobContainerClient using connection string approach.");
        return containerClient;
    }

    private Object buildWithManagedIdentity(String containerName, String endpoint, Object credential) throws Exception {
        LOGGER.info("buildWithManagedIdentity for container: " + containerName + " endpoint: " + endpoint);

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobContainerClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        // reflect builder.credential(tokenCredential)
        Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        Method credMethod = builderClass.getMethod("credential", tokenCredClass);
        credMethod.invoke(builder, credential);

        Method endpointMethod = builderClass.getMethod("endpoint", String.class);
        endpointMethod.invoke(builder, endpoint);

        Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, containerName);

        Method buildM = builderClass.getMethod("buildClient");
        Object containerClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobContainerClient using managed identity approach.");
        return containerClient;
    }

    private Object buildManagedIdentityCredential(String clientId) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        Class<?> builderClass = cl.loadClass("com.azure.identity.DefaultAzureCredentialBuilder");

        Object builder = builderClass.getDeclaredConstructor().newInstance();
        if (clientId != null && !clientId.isEmpty()) {
            LOGGER.info("Using user-assigned managed identity: " + clientId);
            Method micidMethod = builderClass.getMethod("managedIdentityClientId", String.class);
            micidMethod.invoke(builder, clientId);
        } else {
            LOGGER.info("Using system-assigned managed identity (no clientId).");
        }

        Method buildMethod = builderClass.getMethod("build");
        return buildMethod.invoke(builder);
    }

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
        throw new SdkHydrationException("Missing accountName, blobServiceUri, or serviceUri for managed identity scenario.");
    }
}
