package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;

import java.lang.reflect.Method;

/**
 * Reflection logic for building a BlobClient from BlobClientMetaData,
 * potentially throwing SdkHydrationException if reflection or environment
 * variables are invalid. Supports both connection strings and managed identity.
 */
public class BlobClientHydrator extends BaseBlobHydrator<BlobClientMetaData> implements SdkTypeHydrator<BlobClientMetaData> {

    @Override
    public Object createInstance(BlobClientMetaData metaData) throws Exception {
        LOGGER.info("Starting BlobClientHydrator.createInstance()");
        return createInstance(metaData, metaData.getConnectionEnvVar());
    }

    @Override
    protected Object buildWithConnectionString(BlobClientMetaData metaData, String connStr) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        Method connMethod = builderClass.getMethod("connectionString", String.class);
        connMethod.invoke(builder, connStr);

        Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        Method bNameMethod = builderClass.getMethod("blobName", String.class);
        bNameMethod.invoke(builder, metaData.getBlobName());

        Method buildM = builderClass.getMethod("buildClient");
        Object blobClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobClient using connection string approach.");
        return blobClient;
    }

    @Override
    protected Object buildWithManagedIdentity(BlobClientMetaData metaData, String endpoint, Object credential) throws Exception {
        LOGGER.info("buildWithManagedIdentity for container: " + metaData.getContainerName() + ", blob: " + metaData.getBlobName() + " endpoint: " + endpoint);

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        Method credMethod = builderClass.getMethod("credential", tokenCredClass);
        credMethod.invoke(builder, credential);

        Method endpointMethod = builderClass.getMethod("endpoint", String.class);
        endpointMethod.invoke(builder, endpoint);

        Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        Method bNameMethod = builderClass.getMethod("blobName", String.class);
        bNameMethod.invoke(builder, metaData.getBlobName());

        Method buildM = builderClass.getMethod("buildClient");
        Object blobClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobClient using managed identity approach.");
        return blobClient;
    }
}
