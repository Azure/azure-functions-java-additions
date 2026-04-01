/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

/**
 * Reflection logic for building a BlobContainerClient from BlobContainerMetaData.
 * Derives BlobContainerClient from the cached BlobServiceClient in the base class,
 * sharing the HTTP pipeline across all containers under the same storage account.
 */
public class BlobContainerHydrator extends BaseBlobHydrator<BlobContainerMetaData> {

    @Override
    protected Object buildWithConnectionString(BlobContainerMetaData metaData, String connStr) throws Exception {
        return getOrCreateContainerClient(metaData);
    }

    @Override
    protected Object buildWithManagedIdentity(BlobContainerMetaData metaData, String endpoint, Object credential) throws Exception {
        return getOrCreateContainerClient(metaData);
    }
}
