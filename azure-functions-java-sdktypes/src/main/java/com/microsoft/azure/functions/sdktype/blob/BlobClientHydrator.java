/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import java.lang.reflect.Method;

/**
 * Reflection logic for building a BlobClient from BlobClientMetaData,
 * potentially throwing SdkHydrationException if reflection or environment
 * variables are invalid. Supports both connection strings and managed identity.
 */
public class BlobClientHydrator extends BaseBlobHydrator<BlobClientMetaData> {

    @Override
    protected Object buildWithConnectionString(BlobClientMetaData metaData, String connStr) throws Exception {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        final Method connMethod = builderClass.getMethod("connectionString", String.class);
        connMethod.invoke(builder, connStr);

        final Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        final Method bNameMethod = builderClass.getMethod("blobName", String.class);
        bNameMethod.invoke(builder, metaData.getBlobName());

        final Method buildM = builderClass.getMethod("buildClient");
        final Object blobClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobClient using connection string approach.");
        return blobClient;
    }

    @Override
    protected Object buildWithManagedIdentity(BlobClientMetaData metaData, String endpoint, Object credential) throws Exception {
        LOGGER.info("buildWithManagedIdentity for container: " + metaData.getContainerName() + ", blob: " + metaData.getBlobName() + " endpoint: " + endpoint);

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        final Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        final Method credMethod = builderClass.getMethod("credential", tokenCredClass);
        credMethod.invoke(builder, credential);

        final Method endpointMethod = builderClass.getMethod("endpoint", String.class);
        endpointMethod.invoke(builder, endpoint);

        final Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        final Method bNameMethod = builderClass.getMethod("blobName", String.class);
        bNameMethod.invoke(builder, metaData.getBlobName());

        final Method buildM = builderClass.getMethod("buildClient");
        final Object blobClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobClient using managed identity approach.");
        return blobClient;
    }
}
