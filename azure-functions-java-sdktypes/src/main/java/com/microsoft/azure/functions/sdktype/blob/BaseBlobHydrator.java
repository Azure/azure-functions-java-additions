/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Base class for Blob hydrators that handles common logic for connection string
 * vs managed identity authentication. Subclasses override buildWithConnectionString
 * and buildWithManagedIdentity to configure their specific builder types.
 * 
 * This class implements the Template Method pattern, where the overall algorithm
 * structure is defined in createInstance(), but specific steps are delegated to
 * subclass implementations.
 */
public abstract class BaseBlobHydrator<T extends BlobMetaData> implements SdkTypeHydrator<T> {
    protected static final Logger LOGGER = Logger.getLogger(BaseBlobHydrator.class.getName());

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
