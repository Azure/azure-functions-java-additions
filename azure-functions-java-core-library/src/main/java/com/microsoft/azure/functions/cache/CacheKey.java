/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.cache;

/**
 * Marker interface representing a typed key object for cached data.
 *
 * <p>Implementing classes (e.g., BlobClientCacheKey) must define
 * {@code equals} and {@code hashCode} to ensure distinct config combos
 * map to distinct keys.</p>
 */
public interface CacheKey {
    // Typically implement equals/hashCode in the implementing class
}
