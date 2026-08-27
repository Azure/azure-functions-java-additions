/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

/**
 * Builds a BlobContainerClient by deriving it from the cached BlobServiceClient.
 * Free to create — just URL construction, no new HTTP pipeline.
 */
public class BlobContainerHydrator extends BaseBlobHydrator<BlobContainerMetaData> {

    @Override
    public Object createInstance(BlobContainerMetaData metaData) throws Exception {
        final Object serviceClient = getOrCreateServiceClient(metaData);
        return serviceClient.getClass()
                .getMethod("getBlobContainerClient", String.class)
                .invoke(serviceClient, metaData.getContainerName());
    }
}

