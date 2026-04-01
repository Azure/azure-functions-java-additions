/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

/**
 * Builds a BlobClient by deriving it from the cached BlobServiceClient.
 * Both the intermediate BlobContainerClient and the final BlobClient are
 * free to create — just URL construction, no new HTTP pipeline.
 */
public class BlobClientHydrator extends BaseBlobHydrator<BlobClientMetaData> {

    @Override
    public Object createInstance(BlobClientMetaData metaData) throws Exception {
        final Object serviceClient = getOrCreateServiceClient(metaData);
        final Object containerClient = serviceClient.getClass()
                .getMethod("getBlobContainerClient", String.class)
                .invoke(serviceClient, metaData.getContainerName());
        return containerClient.getClass()
                .getMethod("getBlobClient", String.class)
                .invoke(containerClient, metaData.getBlobName());
    }
}
