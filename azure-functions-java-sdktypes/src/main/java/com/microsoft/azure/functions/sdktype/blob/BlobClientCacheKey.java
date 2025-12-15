/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import java.util.Objects;

/**
 * POJO that implements CacheKey for storing BlobClient config.
 */
public class BlobClientCacheKey implements CacheKey {
    private final String containerName;
    private final String blobName;
    private final String envVarForConnection;

    public BlobClientCacheKey(BlobClientMetaData metaData) {
        this.containerName = metaData.getContainerName();
        this.blobName = metaData.getBlobName();
        this.envVarForConnection = metaData.getConnectionEnvVar();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlobClientCacheKey)) {
            return false;
        }
        final BlobClientCacheKey that = (BlobClientCacheKey) o;
        return Objects.equals(containerName, that.containerName)
                && Objects.equals(blobName, that.blobName)
                && Objects.equals(envVarForConnection, that.envVarForConnection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerName, blobName, envVarForConnection);
    }

    @Override
    public String toString() {
        return "BlobClientCacheKey{"
                + "containerName='" + containerName + '\''
                + ", blobName='" + blobName + '\''
                + ", envVarForConnection='" + envVarForConnection + '\''
                + '}';
    }
}
