/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;

public interface CachableSdkType<M extends SdkTypeMetaData> extends SdkType<M> {
    /**
     * Builds a stable cache key for storing or retrieving
     * the final instance from a WorkerObjectCache.
     *
     * @return A CacheKey representing the configuration or details
     *         needed to identify this SDK object in a cache.
     */
    CacheKey buildCacheKey();
}
