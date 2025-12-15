/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

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
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobContainerClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        final Method connMethod = builderClass.getMethod("connectionString", String.class);
        connMethod.invoke(builder, connStr);

        final Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        final Method buildM = builderClass.getMethod("buildClient");
        final Object containerClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobContainerClient using connection string approach.");
        return containerClient;
    }

    @Override
    protected Object buildWithManagedIdentity(BlobContainerMetaData metaData, String endpoint, Object credential) throws Exception {
        LOGGER.info("buildWithManagedIdentity for container: " + metaData.getContainerName() + " endpoint: " + endpoint);

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobContainerClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        final Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        final Method credMethod = builderClass.getMethod("credential", tokenCredClass);
        credMethod.invoke(builder, credential);

        final Method endpointMethod = builderClass.getMethod("endpoint", String.class);
        endpointMethod.invoke(builder, endpoint);

        final Method contMethod = builderClass.getMethod("containerName", String.class);
        contMethod.invoke(builder, metaData.getContainerName());

        final Method buildM = builderClass.getMethod("buildClient");
        final Object containerClient = buildM.invoke(builder);
        LOGGER.info("Successfully built BlobContainerClient using managed identity approach.");
        return containerClient;
    }
}
