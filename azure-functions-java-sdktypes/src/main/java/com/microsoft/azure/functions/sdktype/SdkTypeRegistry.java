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

    /** Check if we support a param type */
    public boolean isTypeSupported(String fqcn) {
        return knownTypes.containsKey(fqcn);
    }

    /**
     * Build-time usage: create a minimal SdkTypeMetaData from the recognized fqcn + param.
     */
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new SdkRegistryException("Unrecognized SdkType: " + fqcn);
        }
        return factory.createMetaData(fqcn, param);
    }

    /**
     * Runtime usage: produce the final SdkType from the stored metaData.
     */
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        String fqcn = metaData.getFqcn(); // see below how we store this
        SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new SdkRegistryException("Unrecognized SdkType: " + fqcn);
        }
        return factory.createSdkType(metaData);
    }
}
