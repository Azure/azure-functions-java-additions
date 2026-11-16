/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable descriptor of a BlobClient parameter. It is created once per function and
 * can be shared by concurrent invocations. Each invocation calls {@link #bind(Map)}
 * to get its own immutable {@link BlobClientMetaData}.
 */
public final class BlobClientDescriptor implements SdkTypeDescriptor {
    static final String CONTAINER_NAME = "ContainerName";
    static final String BLOB_NAME = "BlobName";
    static final String CONNECTION = "Connection";

    private static final Set<String> REQUIRED_FIELDS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(CONTAINER_NAME, BLOB_NAME, CONNECTION)));

    private final String fqcn;
    private final Parameter param;

    public BlobClientDescriptor(String fqcn, Parameter param) {
        this.fqcn = fqcn;
        this.param = param;
    }

    @Override
    public String getFqcn() {
        return fqcn;
    }

    @Override
    public Parameter getParam() {
        return param;
    }

    @Override
    public Set<String> getRequiredFields() {
        return REQUIRED_FIELDS;
    }

    @Override
    public SdkTypeMetaData bind(Map<String, String> rawValues) {
        if (rawValues == null) {
            throw new IllegalArgumentException("rawValues is required");
        }
        return new BlobClientMetaData(fqcn, param,
                rawValues.get(CONTAINER_NAME),
                rawValues.get(BLOB_NAME),
                rawValues.get(CONNECTION));
    }
}
