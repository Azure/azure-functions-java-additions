/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base class for Blob hydrators that handles common logic for connection string
 * vs managed identity authentication. Subclasses override buildWithConnectionString
 * and buildWithManagedIdentity to configure their specific builder types.
 * 
 * This class implements the Template Method pattern, where the overall algorithm
 * structure is defined in createInstance(), but specific steps are delegated to
 * subclass implementations.
 * 
 * Maintains an internal cache of BlobServiceClient objects keyed by the connection
 * environment variable name. Since BlobServiceClient holds the HttpPipeline (HTTP
 * client, retry policies, auth), caching at this level means all containers and
 * blobs under the same storage account share a single pipeline. Deriving
 * BlobContainerClient and BlobClient from a BlobServiceClient is free — just URL
 * construction, no new HTTP connections.
 */
public abstract class BaseBlobHydrator<T extends BlobMetaData> implements SdkTypeHydrator<T> {
    protected static final Logger LOGGER = Logger.getLogger(BaseBlobHydrator.class.getName());

    /** Max number of cached BlobServiceClient instances (keyed by connection env var). */
    private static final int MAX_SERVICE_CLIENTS = 8;

    /**
     * LRU cache of BlobServiceClient objects, keyed by the connection environment
     * variable name (e.g. "AzureWebJobsStorage"). Typically a function app has 1-3
     * storage connections, so a small cache suffices.
     */
    private static final Map<String, Object> SERVICE_CLIENT_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, Object>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > MAX_SERVICE_CLIENTS;
                }
            });

    /**
     * Implements the SdkTypeHydrator interface method. Extracts the connection environment variable
     * from metadata and delegates to the template method.
     * 
     * @param metaData the metadata containing configuration details
     * @return the built client instance
     * @throws Exception if client creation fails
     */
    @Override
    public Object createInstance(T metaData) throws Exception {
        LOGGER.info("Starting " + this.getClass().getSimpleName() + ".createInstance()");
        return createInstance(metaData, metaData.getConnectionEnvVar());
    }

    /**
     * Main orchestration method that determines authentication type and delegates to subclass methods.
     * This is the template method that defines the algorithm structure.
     * 
     * @param metaData the metadata containing configuration details
     * @param envVar the environment variable name or prefix for authentication
     * @return the built client instance
     * @throws Exception if client creation fails
     */
    private Object createInstance(T metaData, String envVar) throws Exception {
        LOGGER.info("Starting hydration with environment variable: " + envVar);

        final String maybeConnString = System.getenv(envVar);
        
        if (maybeConnString != null && isConnectionString(maybeConnString)) {
            LOGGER.info("Detected connection string usage from environment variable: " + envVar);
            return buildWithConnectionString(metaData, maybeConnString);
        } else {
            LOGGER.info("Detected Managed Identity usage. Prefix: " + envVar);

            final String accountName = System.getenv(envVar + "__accountName");
            final String serviceUri = System.getenv(envVar + "__serviceUri");
            final String blobServiceUri = System.getenv(envVar + "__blobServiceUri");
            final String clientId = System.getenv(envVar + "__clientId");

            final String endpoint = resolveEndpoint(accountName, serviceUri, blobServiceUri);
            final Object credential = buildManagedIdentityCredential(clientId);

            return buildWithManagedIdentity(metaData, endpoint, credential);
        }
    }

    /**
     * Gets or creates a cached BlobServiceClient for the given connection.
     * The BlobServiceClient holds the HttpPipeline and is the most expensive object
     * to create. All container and blob clients derived from it share the pipeline.
     *
     * @param metaData the metadata containing connection info
     * @return a BlobServiceClient instance (cached per connection env var)
     * @throws Exception if service client creation fails
     */
    protected Object getOrCreateServiceClient(BlobMetaData metaData) throws Exception {
        final String cacheKey = metaData.getConnectionEnvVar();
        Object cached = SERVICE_CLIENT_CACHE.get(cacheKey);
        if (cached != null) {
            LOGGER.fine("Service client cache hit for: " + cacheKey);
            return cached;
        }

        LOGGER.info("Service client cache miss for: " + cacheKey + ". Building new BlobServiceClient.");
        final Object serviceClient = buildServiceClient(metaData.getConnectionEnvVar());
        SERVICE_CLIENT_CACHE.put(cacheKey, serviceClient);
        return serviceClient;
    }

    /**
     * Gets or creates a BlobContainerClient by deriving it from the cached BlobServiceClient.
     * This is a cheap operation — no new HTTP pipeline, just URL construction.
     *
     * @param metaData the metadata containing connection and container info
     * @return a BlobContainerClient instance
     * @throws Exception if creation fails
     */
    protected Object getOrCreateContainerClient(BlobMetaData metaData) throws Exception {
        final Object serviceClient = getOrCreateServiceClient(metaData);
        return serviceClient.getClass()
                .getMethod("getBlobContainerClient", String.class)
                .invoke(serviceClient, metaData.getContainerName());
    }

    /**
     * Builds a BlobServiceClient using reflection, handling both connection string
     * and managed identity scenarios.
     *
     * @param envVar the environment variable name for the connection
     * @return a new BlobServiceClient instance
     * @throws Exception if client creation fails
     */
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
            final Object credential = buildManagedIdentityCredential(clientId);
            return buildServiceClientWithManagedIdentity(cl, endpoint, credential);
        }
    }

    private Object buildServiceClientWithConnectionString(ClassLoader cl, String connStr) throws Exception {
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobServiceClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        builderClass.getMethod("connectionString", String.class).invoke(builder, connStr);

        final Object serviceClient = builderClass.getMethod("buildClient").invoke(builder);
        LOGGER.info("Built BlobServiceClient using connection string.");
        return serviceClient;
    }

    private Object buildServiceClientWithManagedIdentity(ClassLoader cl, String endpoint, Object credential) throws Exception {
        final Class<?> builderClass = cl.loadClass("com.azure.storage.blob.BlobServiceClientBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        final Class<?> tokenCredClass = cl.loadClass("com.azure.core.credential.TokenCredential");
        builderClass.getMethod("credential", tokenCredClass).invoke(builder, credential);
        builderClass.getMethod("endpoint", String.class).invoke(builder, endpoint);

        final Object serviceClient = builderClass.getMethod("buildClient").invoke(builder);
        LOGGER.info("Built BlobServiceClient using managed identity.");
        return serviceClient;
    }

    /**
     * Derives a BlobClient from a cached BlobContainerClient via getBlobClient(blobName).
     * This is essentially free — no HTTP pipeline build, just URL construction.
     *
     * @param containerClient a BlobContainerClient instance
     * @param blobName the blob name
     * @return a BlobClient pointing at the specific blob
     * @throws Exception if reflection fails
     */
    protected Object deriveBlobClient(Object containerClient, String blobName) throws Exception {
        return containerClient.getClass()
                .getMethod("getBlobClient", String.class)
                .invoke(containerClient, blobName);
    }

    /**
     * Subclasses override to build their specific client using connection string authentication.
     * 
     * @param metaData the metadata containing configuration details
     * @param connectionString the connection string from environment variable
     * @return the built client instance
     * @throws Exception if client creation fails
     */
    protected abstract Object buildWithConnectionString(T metaData, String connectionString) throws Exception;

    /**
     * Subclasses override to build their specific client using managed identity authentication.
     * 
     * @param metaData the metadata containing configuration details
     * @param endpoint the resolved endpoint URL
     * @param credential the DefaultAzureCredential instance
     * @return the built client instance
     * @throws Exception if client creation fails
     */
    protected abstract Object buildWithManagedIdentity(T metaData, String endpoint, Object credential) throws Exception;

    /**
     * Decide if configValue is likely a connection string by checking for well-known keywords.
     * 
     * @param val the value to check
     * @return true if the value appears to be a connection string
     */
    protected boolean isConnectionString(String val) {
        return val.contains("AccountKey=")
                || val.contains("DefaultEndpointsProtocol=")
                || val.contains("UseDevelopmentStorage=true");
    }

    /**
     * Resolves the endpoint for managed identity from environment variables, or throws if none found.
     * Checks accountName, blobServiceUri, and serviceUri in order.
     * 
     * @param accountName the storage account name
     * @param serviceUri the generic service URI
     * @param blobServiceUri the blob-specific service URI
     * @return the resolved endpoint URL
     * @throws SdkHydrationException if no endpoint can be resolved
     */
    protected String resolveEndpoint(String accountName, String serviceUri, String blobServiceUri) {
        if (accountName != null && !accountName.isEmpty()) {
            final String ep = String.format("https://%s.blob.core.windows.net", accountName);
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

    /**
     * Build the DefaultAzureCredential reflectively, including user-assigned clientId if present.
     * 
     * @param clientId optional client ID for user-assigned managed identity
     * @return the DefaultAzureCredential instance
     * @throws Exception if credential creation fails
     */
    protected Object buildManagedIdentityCredential(String clientId) throws Exception {
        LOGGER.info("Building DefaultAzureCredential for managed identity.");
        
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final Class<?> builderClass = cl.loadClass("com.azure.identity.DefaultAzureCredentialBuilder");
        final Object builder = builderClass.getDeclaredConstructor().newInstance();

        if (clientId != null && !clientId.isEmpty()) {
            LOGGER.info("Using user-assigned managed identity: " + clientId);
            final Method micidMethod = builderClass.getMethod("managedIdentityClientId", String.class);
            micidMethod.invoke(builder, clientId);
        } else {
            LOGGER.info("Using system-assigned managed identity (no clientId).");
        }

        final Method buildMethod = builderClass.getMethod("build");
        return buildMethod.invoke(builder);
    }
}
