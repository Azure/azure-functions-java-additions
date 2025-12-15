/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.sdktype.blob.BlobClientSdkTypeFactory;
import com.microsoft.azure.functions.sdktype.blob.BlobContainerSdkTypeFactory;
import com.microsoft.azure.functions.sdktype.exceptions.SdkRegistryException;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry that knows about recognized SDK client FQCNs and can create SdkType objects.
 * Throws SdkRegistryException if asked to create metadata or an SdkType for an unrecognized FQCN.
 */
public class SdkTypeRegistry {
    // Maps FQCN -> Class<? extends SdkType>
    private final Map<String, SdkTypeFactory> knownTypes = new HashMap<>();

    public SdkTypeRegistry() {
        knownTypes.put("com.azure.storage.blob.BlobClient", new BlobClientSdkTypeFactory());
        knownTypes.put("com.azure.storage.blob.BlobContainerClient", new BlobContainerSdkTypeFactory());
    }

    /**
     * Check if we support a param type.
     *
     * @param fqcn the fully qualified class name to check
     * @return true if the type is supported, false otherwise
     */
    public boolean isTypeSupported(String fqcn) {
        return knownTypes.containsKey(fqcn);
    }

    /**
     * Build-time usage: create a minimal SdkTypeMetaData from the recognized fqcn + param.
     *
     * @param fqcn the fully qualified class name of the SDK type
     * @param param the parameter being analyzed
     * @return the created SdkTypeMetaData
     * @throws Exception if metadata creation fails
     */
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        final SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new SdkRegistryException("Unrecognized SdkType: " + fqcn);
        }
        return factory.createMetaData(fqcn, param);
    }

    /**
     * Runtime usage: produce the final SdkType from the stored metaData.
     *
     * @param metaData the metadata containing configuration details
     * @return the created SdkType
     * @throws Exception if SdkType creation fails
     */
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        final String fqcn = metaData.getFqcn(); // see below how we store this
        final SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new SdkRegistryException("Unrecognized SdkType: " + fqcn);
        }
        return factory.createSdkType(metaData);
    }
}
