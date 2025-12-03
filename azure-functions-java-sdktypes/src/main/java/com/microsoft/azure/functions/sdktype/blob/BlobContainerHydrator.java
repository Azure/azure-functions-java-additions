package com.microsoft.azure.functions.sdktype.blob;

import java.lang.reflect.Method;

/**
 * Reflection logic for building a BlobContainerClient from BlobContainerMetaData,
 * throwing SdkHydrationException on reflection or environment errors.
 * Supports both connection string usage and managed identity usage.
 */
public class BlobContainerHydrator extends BaseBlobHydrator<BlobContainerMetaData> {

    @Override
    protected Object buildWithConnectionString(BlobContainerMetaData metaData, String connStr) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobContainerClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        Method connMethod = builderClass.getMethod("connectionString", String.class);
        connMethod.invoke(builder, connStr);

        Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        Method buildM = builderClass.getMethod("buildClient");
        Object containerClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobContainerClient using connection string approach.");
        return containerClient;
    }

    @Override
    protected Object buildWithManagedIdentity(BlobContainerMetaData metaData, String endpoint, Object credential) throws Exception {
        LOGGER.info("buildWithManagedIdentity for container: " + metaData.getContainerName() + " endpoint: " + endpoint);

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobContainerClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        Method credMethod = builderClass.getMethod("credential", tokenCredClass);
        credMethod.invoke(builder, credential);

        Method endpointMethod = builderClass.getMethod("endpoint", String.class);
        endpointMethod.invoke(builder, endpoint);

        Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        Method buildM = builderClass.getMethod("buildClient");
        Object containerClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobContainerClient using managed identity approach.");
        return containerClient;
    }
}
