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
 * SdkTypeFactory for BlobContainerClient scenario.
 */
public final class BlobContainerSdkTypeFactory implements SdkTypeFactory {

    @Override
    public SdkTypeDescriptor createDescriptor(String fqcn, Parameter param) throws SdkTypeCreationException {
        return new BlobContainerDescriptor(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws SdkTypeCreationException {
        final BlobContainerMetaData containerMeta = (BlobContainerMetaData) metaData;
        return new BlobContainerSdkType(containerMeta);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData, ObjectCache<CacheKey, Object> cache)
            throws SdkTypeCreationException {
        final BlobContainerMetaData containerMeta = (BlobContainerMetaData) metaData;
        return new BlobContainerSdkType(containerMeta, new BlobContainerHydrator(cache));
    }
}
