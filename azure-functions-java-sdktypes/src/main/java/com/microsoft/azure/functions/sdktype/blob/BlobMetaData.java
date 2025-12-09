package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

/**
 * Common interface for blob-related metadata that includes connection configuration.
 * This allows the BaseBlobHydrator to access the connection environment variable
 * generically across all blob metadata types.
 */
public interface BlobMetaData extends SdkTypeMetaData {
    /**
     * Gets the environment variable name used for connection string or managed identity prefix.
     *
     * @return the connection environment variable name
     */
    String getConnectionEnvVar();
}
