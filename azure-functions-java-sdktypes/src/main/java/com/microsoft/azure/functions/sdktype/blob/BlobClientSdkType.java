/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;

/**
 * SdkType for building a BlobClient. The parseMetadata method obtains
 * containerName, blobName, and envVarForConnection from the invocation context.
 */
public final class BlobClientSdkType implements SdkType<BlobClientMetaData> {
    private final BlobClientHydrator hydrator;
    private final BlobClientMetaData metaData;

    public BlobClientSdkType(BlobClientMetaData metaData, BlobClientHydrator hydrator) {
        this.hydrator = hydrator;
        this.metaData = metaData;
    }

    @Override
    public BlobClientMetaData getMetaData() {
        return metaData;
    }

    @Override
    public SdkTypeHydrator<BlobClientMetaData> getHydrator() {
        return hydrator;
    }
}
