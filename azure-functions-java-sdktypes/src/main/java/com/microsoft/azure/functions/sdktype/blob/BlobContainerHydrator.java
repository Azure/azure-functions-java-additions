package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;

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
            LOGGER.info("Detected managed identity usage. Prefix: " + envVar);

            String accountName = System.getenv(envVar + "__accountName");
            String serviceUri = System.getenv(envVar + "__serviceUri");
            String blobServiceUri = System.getenv(envVar + "__blobServiceUri");
            String clientId = System.getenv(envVar + "__clientId");

            String endpoint = resolveEndpoint(accountName, serviceUri, blobServiceUri);
            Object credential = buildManagedIdentityCredential(clientId);

            return buildWithManagedIdentity(containerName, endpoint, credential);
        }
    }

    private boolean isConnectionString(String val) {
        return val.contains("AccountKey=") || val.contains("DefaultEndpointsProtocol=");
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
        final String unshadedName = "com.azure.core.credential.TokenCredential";
        final String fallBackName = "com.microsoft.azure.functions.shaded.com.azure.core.credential.TokenCredential";
        Class<?> tokenCredClass = tryLoadClass(cl, unshadedName, fallBackName);
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

        final String unshadedName = "com.azure.identity.DefaultAzureCredentialBuilder";
        final String fallBackName = "com.microsoft.azure.functions.shaded.com.azure.identity.DefaultAzureCredentialBuilder";
        Class<?> builderClass = tryLoadClass(cl, unshadedName, fallBackName);

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
        throw new IllegalArgumentException("Missing accountName, blobServiceUri, or serviceUri for managed identity scenario.");
    }

    private Class<?> tryLoadClass(ClassLoader cl, String unshadedName, String fallbackName) throws ClassNotFoundException {
        try {
            return cl.loadClass(unshadedName);
        } catch (ClassNotFoundException ex) {
            LOGGER.warning("Could not find unshaded class: " + unshadedName
                    + ". Attempting fallback: " + fallbackName);
            return cl.loadClass(fallbackName);
        }
    }
}
