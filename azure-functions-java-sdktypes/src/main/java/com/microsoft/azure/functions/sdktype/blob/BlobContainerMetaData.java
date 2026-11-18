/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import java.lang.reflect.Parameter;

/**
 * Immutable, validated values of one BlobContainerClient invocation: containerName and
 * the environment variable name used for connection string or managed identity prefix.
 * Created by {@link BlobContainerDescriptor#bind(java.util.Map)}. All fields are final,
 * so an instance can be shared between threads.
 */
public final class BlobContainerMetaData implements BlobMetaData {
    private final String fqcn;
    private final Parameter param;
    private final String containerName;
    private final String connectionEnvVar;

    /**
     * Creates the metadata and validates that all values are present.
     *
     * @param fqcn the fully qualified class name of the SDK type
     * @param param the parameter of the user function
     * @param containerName the blob container name
     * @param connectionEnvVar the environment variable name for the connection
     * @throws IllegalArgumentException if containerName or connectionEnvVar is null or empty
     */
    public BlobContainerMetaData(String fqcn, Parameter param,
                                 String containerName, String connectionEnvVar) {
        if (containerName == null || containerName.isEmpty()) {
            throw new IllegalArgumentException("containerName is required for BlobContainerClient.");
        }
        if (connectionEnvVar == null || connectionEnvVar.isEmpty()) {
            throw new IllegalArgumentException("connectionEnvVar is required for BlobContainerClient.");
        }
        this.fqcn = fqcn;
        this.param = param;
        this.containerName = containerName;
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

    // Typed getters for usage in hydrator/SDKType
    public String getContainerName() {
        return containerName;
    }

    @Override
    public String getConnectionEnvVar() {
        return connectionEnvVar;
    }
}
