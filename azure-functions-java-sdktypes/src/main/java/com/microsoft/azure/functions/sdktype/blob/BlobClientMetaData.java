/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import java.lang.reflect.Parameter;

/**
 * Immutable, validated values of one BlobClient invocation: containerName, blobName and
 * the environment variable name for the connection. Created by
 * {@link BlobClientDescriptor#bind(java.util.Map)}. All fields are final, so an instance
 * can be shared between threads.
 */
public final class BlobClientMetaData implements BlobMetaData {
    private final String fqcn;
    private final Parameter param;
    private final String containerName;
    private final String blobName;
    private final String connectionEnvVar;

    /**
     * Creates the metadata and validates that all values are present.
     *
     * @param fqcn the fully qualified class name of the SDK type
     * @param param the parameter of the user function
     * @param containerName the blob container name
     * @param blobName the blob name
     * @param connectionEnvVar the environment variable name for the connection
     * @throws IllegalArgumentException if containerName, blobName or connectionEnvVar is null or empty
     */
    public BlobClientMetaData(String fqcn, Parameter param,
                              String containerName, String blobName, String connectionEnvVar) {
        if (containerName == null || containerName.isEmpty()) {
            throw new IllegalArgumentException("containerName is required");
        }
        if (blobName == null || blobName.isEmpty()) {
            throw new IllegalArgumentException("blobName is required");
        }
        if (connectionEnvVar == null || connectionEnvVar.isEmpty()) {
            throw new IllegalArgumentException("connectionEnvVar is required");
        }
        this.fqcn = fqcn;
        this.param = param;
        this.containerName = containerName;
        this.blobName = blobName;
        this.connectionEnvVar = connectionEnvVar;
    }

    @Override
    public Parameter getParam() {
        return param;
    }

    @Override
    public String getFqcn() {
        return fqcn;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getBlobName() {
        return blobName;
    }

    @Override
    public String getConnectionEnvVar() {
        return connectionEnvVar;
    }
}
