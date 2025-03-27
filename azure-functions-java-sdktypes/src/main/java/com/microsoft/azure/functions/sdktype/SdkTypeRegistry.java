package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.sdktype.blob.BlobClientSdkType;
import com.microsoft.azure.functions.sdktype.blob.BlobClientSdkTypeFactory;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry that knows about recognized SDK client FQCNs and can create SdkType objects.
 */
public class SdkTypeRegistry {
    // Maps FQCN -> Class<? extends SdkType>
    private final Map<String, SdkTypeFactory> knownTypes = new HashMap<>();

    public SdkTypeRegistry() {
        knownTypes.put("com.azure.storage.blob.BlobClient", new BlobClientSdkTypeFactory());
    }

    /** Check if we recognize a param type */
    public boolean isRecognizedType(String fqcn) {
        return knownTypes.containsKey(fqcn);
    }

    /**
     * Build-time usage: create a minimal SdkTypeMetaData from the recognized fqcn + param.
     */
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new IllegalArgumentException("Unrecognized SdkType: " + fqcn);
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
            throw new IllegalArgumentException("Unrecognized SdkType: " + fqcn);
        }
        return factory.createSdkType(metaData);
    }
}
