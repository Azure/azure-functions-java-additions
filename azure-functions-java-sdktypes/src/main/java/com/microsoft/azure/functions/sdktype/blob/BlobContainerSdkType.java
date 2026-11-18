/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.sdktype.CachableSdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;

/**
 * Cachable SdkType for building a BlobContainerClient.
 */
public final class BlobContainerSdkType implements CachableSdkType<BlobContainerMetaData> {
    private final BlobContainerMetaData metaData;
    private final BlobContainerHydrator hydrator;

    public BlobContainerSdkType(BlobContainerMetaData metaData) {
        this(metaData, new BlobContainerHydrator());
    }

    public BlobContainerSdkType(BlobContainerMetaData metaData, BlobContainerHydrator hydrator) {
        this.metaData = metaData;
        this.hydrator = hydrator;
    }

    @Override
    public BlobContainerMetaData getMetaData() {
        return metaData;
    }

    @Override
    public SdkTypeHydrator<BlobContainerMetaData> getHydrator() {
        return hydrator;
    }

    @Override
    public CacheKey buildCacheKey() {
        return new BlobContainerCacheKey(metaData);
    }
}
