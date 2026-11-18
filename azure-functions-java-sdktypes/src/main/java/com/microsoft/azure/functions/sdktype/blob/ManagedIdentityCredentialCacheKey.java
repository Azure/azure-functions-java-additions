/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;

import java.util.Objects;

/**
 * Cache key of the managed identity credential that is shared by every client of one connection.
 * It holds only the connection setting name and the optional client ID, never a secret.
 * The credential does not depend on the endpoint, the container or the blob.
 */
final class ManagedIdentityCredentialCacheKey implements CacheKey {
    private final String connectionEnvVar;
    private final String clientId;

    ManagedIdentityCredentialCacheKey(String connectionEnvVar, String clientId) {
        this.connectionEnvVar = connectionEnvVar;
        // an empty client ID selects the system-assigned identity, exactly like a missing one
        this.clientId = (clientId == null || clientId.isEmpty()) ? null : clientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ManagedIdentityCredentialCacheKey)) {
            return false;
        }
        final ManagedIdentityCredentialCacheKey that = (ManagedIdentityCredentialCacheKey) o;
        return Objects.equals(connectionEnvVar, that.connectionEnvVar)
                && Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionEnvVar, clientId);
    }

    @Override
    public String toString() {
        return "ManagedIdentityCredentialCacheKey{"
                + "connectionEnvVar='" + connectionEnvVar + '\''
                + ", clientId='" + clientId + '\''
                + '}';
    }
}
