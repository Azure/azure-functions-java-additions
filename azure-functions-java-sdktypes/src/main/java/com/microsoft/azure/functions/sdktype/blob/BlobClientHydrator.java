/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

/**
 * Reflection logic for building a BlobClient from BlobClientMetaData.
 * Derives BlobClient from the cached BlobServiceClient in the base class,
 * sharing the HTTP pipeline across all containers and blobs under the same
 * storage account connection.
 */
public class BlobClientHydrator extends BaseBlobHydrator<BlobClientMetaData> {

    @Override
    protected Object buildWithConnectionString(BlobClientMetaData metaData, String connStr) throws Exception {
        final Object containerClient = getOrCreateContainerClient(metaData);
        final Object blobClient = deriveBlobClient(containerClient, metaData.getBlobName());
        LOGGER.info("Derived BlobClient from cached service client (connection string).");
        return blobClient;
    }

    @Override
    protected Object buildWithManagedIdentity(BlobClientMetaData metaData, String endpoint, Object credential) throws Exception {
        final Object containerClient = getOrCreateContainerClient(metaData);
        final Object blobClient = deriveBlobClient(containerClient, metaData.getBlobName());
        LOGGER.info("Derived BlobClient from cached service client (managed identity).");
        return blobClient;
    }
}
