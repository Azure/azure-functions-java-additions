/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.function.Function;
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
 * <p>The hydrator decides for itself what to cache, through the cache it is created with. The managed identity
 * credential is built once per connection setting and client ID and shared by every client built with it.
 * A client is cached too when the subclass returns a key from {@link #instanceKey(BlobMetaData)}.
 * Without a cache every call builds a new client and a new credential. A failed build is never cached.
 * The connection string path never builds a credential.</p>
 */
public abstract class BaseBlobHydrator<T extends BlobMetaData> implements SdkTypeHydrator<T> {
    protected static final Logger LOGGER = Logger.getLogger(BaseBlobHydrator.class.getName());

    private final ObjectCache<CacheKey, Object> cache;

    /**
     * Creates a hydrator that caches nothing. Every call builds a new client and a new managed identity credential.
     */
    public BaseBlobHydrator() {
        this(null);
    }

    /**
     * Creates a hydrator that caches through the given cache, the managed identity credential and, when the
     * subclass has an {@link #instanceKey(BlobMetaData) instance key}, the clients.
     *
     * @param cache the cache to use, usually the view that the registry hands out,
     *              or null to cache nothing
     */
    protected BaseBlobHydrator(ObjectCache<CacheKey, Object> cache) {
        this.cache = cache;
    }

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
        return createInstance(metaData, metaData.getConnectionEnvVar(), System::getenv);
    }

    /**
     * Returns the client of the metadata from the cache and builds it first when it is not there. Without a
     * cache, or when {@link #instanceKey(BlobMetaData)} returns null, a new client is built for every call.
     *
     * @param metaData the metadata containing configuration details
     * @param envVar the environment variable name or prefix for authentication
     * @param env reads an environment variable, null when it is not set
     * @return the client instance
     * @throws Exception if client creation fails
     */
    Object createInstance(T metaData, String envVar, Function<String, String> env) throws Exception {
        return cached(instanceKey(metaData), () -> hydrate(metaData, envVar, env));
    }

    /**
     * Returns the key to cache the client under, or null to build a new client for every call, which is the default.
     * The key may only hold connection configuration, never a value of one invocation and never a secret.
     *
     * @param metaData the metadata containing configuration details
     * @return the cache key of the client, or null to not cache it
     */
    protected CacheKey instanceKey(T metaData) {
        return null;
    }

    /**
     * Main orchestration method that determines authentication type and delegates to subclass methods.
     * This is the template method that defines the algorithm structure.
     * 
     * @param metaData the metadata containing configuration details
     * @param envVar the environment variable name or prefix for authentication
     * @param env reads an environment variable, null when it is not set
     * @return the built client instance
     * @throws Exception if client creation fails
     */
    private Object hydrate(T metaData, String envVar, Function<String, String> env) throws Exception {
        LOGGER.info("Starting " + this.getClass().getSimpleName() + ".createInstance()");
        LOGGER.info("Starting hydration with environment variable: " + envVar);

        final String maybeConnString = env.apply(envVar);
        
        if (maybeConnString != null && isConnectionString(maybeConnString)) {
            LOGGER.info("Detected connection string usage from environment variable: " + envVar);
            return buildWithConnectionString(metaData, maybeConnString);
        } else {
            LOGGER.info("Detected Managed Identity usage. Prefix: " + envVar);

            final String accountName = env.apply(envVar + "__accountName");
            final String serviceUri = env.apply(envVar + "__serviceUri");
            final String blobServiceUri = env.apply(envVar + "__blobServiceUri");
            final String clientId = env.apply(envVar + "__clientId");

            final String endpoint = resolveEndpoint(accountName, serviceUri, blobServiceUri);
            final Object credential = getManagedIdentityCredential(envVar, clientId);

            return buildWithManagedIdentity(metaData, endpoint, credential);
        }
    }

    /**
     * Returns the managed identity credential of a connection. With a cache it is built once and shared,
     * without a cache it is built for every call. A failed build is not cached and surfaces unchanged.
     *
     * @param envVar the environment variable name or prefix of the connection
     * @param clientId optional client ID for user-assigned managed identity
     * @return the DefaultAzureCredential instance
     * @throws Exception if credential creation fails
     */
    private Object getManagedIdentityCredential(String envVar, String clientId) throws Exception {
        final CacheKey key = new ManagedIdentityCredentialCacheKey(envVar, clientId);
        return cached(key, () -> buildManagedIdentityCredential(clientId));
    }

    /**
     * Returns the object of the key from the cache and builds it first when it is not there. A failed build is not
     * cached and surfaces unchanged, whatever the cache wraps it in. Without a cache, or without a key, the
     * object is built for every call.
     */
    private Object cached(CacheKey key, Callable<Object> builder) throws Exception {
        if (cache == null || key == null) {
            return builder.call();
        }
        try {
            return cache.computeIfAbsent(key.getClass(), key, () -> build(builder));
        } catch (RuntimeException e) {
            // caches wrap the exception of the supplier, hand the original one back to the caller
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof BuildFailure) {
                    throw (Exception) t.getCause();
                }
            }
            throw e;
        }
    }

    private static Object build(Callable<Object> builder) {
        try {
            return builder.call();
        } catch (Exception e) {
            throw new BuildFailure(e);
        }
    }

    private static final class BuildFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BuildFailure(Exception cause) {
            super(cause);
        }
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
