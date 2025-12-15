/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;

import java.util.Objects;

/**
 * A cache key for the BlobContainerClient scenario.
 * We store containerName + connectionEnvVar from the metaData.
 */
public class BlobContainerCacheKey implements CacheKey {
    private final String containerName;
    private final String connectionEnvVar;

    public BlobContainerCacheKey(BlobContainerMetaData metaData) {
        this.containerName = metaData.getContainerName();
        this.connectionEnvVar = metaData.getConnectionEnvVar();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlobContainerCacheKey)) {
            return false;
        }
        final BlobContainerCacheKey that = (BlobContainerCacheKey) o;
        return Objects.equals(containerName, that.containerName)
                && Objects.equals(connectionEnvVar, that.connectionEnvVar);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerName, connectionEnvVar);
    }

    @Override
    public String toString() {
        return "BlobContainerCacheKey{"
                + "containerName='" + containerName + '\''
                + ", connectionEnvVar='" + connectionEnvVar + '\''
                + '}';
    }
}
