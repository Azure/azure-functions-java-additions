/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Base class for Blob hydrators. Owns BlobServiceClient caching and auth
 * detection (connection string vs managed identity). Subclasses override
 * {@link #createInstance} to derive their specific client type
 * (BlobClient, BlobContainerClient) from the cached service client.
 *
 * <p>The BlobServiceClient holds the HTTP pipeline (HttpClient, retry policies,
 * auth provider). Caching it per connection means all containers and blobs
 * under the same storage account share a single pipeline. Deriving
 * BlobContainerClient or BlobClient from it is free — just URL construction.</p>
 */
public abstract class BaseBlobHydrator<T extends BlobMetaData> implements SdkTypeHydrator<T> {
    protected static final Logger LOGGER = Logger.getLogger(BaseBlobHydrator.class.getName());

    /**
     * Cache of BlobServiceClient objects, keyed by the connection environment
     * variable name (e.g. "AzureWebJobsStorage"). Uses ConcurrentHashMap for
     * lock-free reads under high throughput. Typically a function app has 1-3
     * storage connections, so unbounded growth is not a concern.
     */
    private static final Map<String, Object> SERVICE_CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * Gets or creates a cached BlobServiceClient for the given connection.
     * Subclasses call this and derive container/blob clients from the result.
     *
     * @param metaData the metadata containing connection info
     * @return a BlobServiceClient instance (cached per connection env var)
     * @throws Exception if service client creation fails
     */
    protected Object getOrCreateServiceClient(BlobMetaData metaData) throws Exception {
        final String cacheKey = metaData.getConnectionEnvVar();
        Object cached = SERVICE_CLIENT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        LOGGER.info("Service client cache miss for: " + cacheKey + ". Building new BlobServiceClient.");
        final Object serviceClient = buildServiceClient(cacheKey);
        Object existing = SERVICE_CLIENT_CACHE.putIfAbsent(cacheKey, serviceClient);
        return existing != null ? existing : serviceClient;
    }

    // ---- Service client construction (private) ----

    private Object buildServiceClient(String envVar) throws Exception {
        final String maybeConnString = System.getenv(envVar);
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();

        if (maybeConnString != null && isConnectionString(maybeConnString)) {
            return buildServiceClientWithConnectionString(cl, maybeConnString);
        } else {
            final String accountName = System.getenv(envVar + "__accountName");
            final String serviceUri = System.getenv(envVar + "__serviceUri");
            final String blobServiceUri = System.getenv(envVar + "__blobServiceUri");
            final String clientId = System.getenv(envVar + "__clientId");

            final String endpoint = resolveEndpoint(accountName, serviceUri, blobServiceUri);
            final Object credential = buildManagedIdentityCredential(cl, clientId);
            return buildServiceClientWithManagedIdentity(cl, endpoint, credential);
        }
    }

    private Object buildServiceClientWithConnectionString(ClassLoader cl, String connStr) throws Exception {
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobServiceClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();
        builderClass.getMethod("connectionString", String.class).invoke(builder, connStr);
        return builderClass.getMethod("buildClient").invoke(builder);
    }

    private Object buildServiceClientWithManagedIdentity(ClassLoader cl, String endpoint, Object credential) throws Exception {
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobServiceClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();
        final Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        builderClass.getMethod("credential", tokenCredClass).invoke(builder, credential);
        builderClass.getMethod("endpoint", String.class).invoke(builder, endpoint);
        return builderClass.getMethod("buildClient").invoke(builder);
    }

    // ---- Auth utilities ----

    private boolean isConnectionString(String val) {
        return val.contains("AccountKey=")
                || val.contains("DefaultEndpointsProtocol=")
                || val.contains("UseDevelopmentStorage=true");
    }

    private String resolveEndpoint(String accountName, String serviceUri, String blobServiceUri) {
        if (accountName != null && !accountName.isEmpty()) {
            return String.format("https://%s.blob.core.windows.net", accountName);
        }
        if (blobServiceUri != null && !blobServiceUri.isEmpty()) {
            return blobServiceUri;
        }
        if (serviceUri != null && !serviceUri.isEmpty()) {
            return serviceUri;
        }
        throw new SdkHydrationException(
                "Missing accountName, blobServiceUri, or serviceUri for managed identity scenario.");
    }

    private Object buildManagedIdentityCredential(ClassLoader cl, String clientId) throws Exception {
        final Class<?> builderClass = cl.loadClass("com.azure.identity.DefaultAzureCredentialBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        if (clientId != null && !clientId.isEmpty()) {
            builderClass.getMethod("managedIdentityClientId", String.class).invoke(builder, clientId);
        }

        return builderClass.getMethod("build").invoke(builder);
    }
}
