package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;

public interface CachableSdkType <M extends SdkTypeMetaData> extends SdkType<M>{
    /**
     * Allows the worker to build a stable cache key for storing
     * or retrieving the final instance from a WorkerObjectCache.
     */
    CacheKey buildCacheKey();
}
