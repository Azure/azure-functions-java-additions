/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException;

import java.lang.reflect.Parameter;

/**
 * SdkTypeFactory for building a BlobClientSdkType from BlobClientMetaData.
 * Potentially throws SdkTypeCreationException if metaData is invalid or reflection fails.
 */
public final class BlobClientSdkTypeFactory implements SdkTypeFactory {
    @Override
    public SdkTypeDescriptor createDescriptor(String fqcn, Parameter param) throws SdkTypeCreationException {
        return new BlobClientDescriptor(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws SdkTypeCreationException {
        final BlobClientMetaData blobClientMetaData = (BlobClientMetaData) metaData;
        return new BlobClientSdkType(blobClientMetaData, new BlobClientHydrator());
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData, ObjectCache<CacheKey, Object> cache)
            throws SdkTypeCreationException {
        final BlobClientMetaData blobClientMetaData = (BlobClientMetaData) metaData;
        return new BlobClientSdkType(blobClientMetaData, new BlobClientHydrator(cache));
    }
}
