package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

import java.lang.reflect.Parameter;

/**
 * SdkTypeFactory for BlobContainerClient scenario.
 */
public class BlobContainerSdkTypeFactory implements SdkTypeFactory {

    @Override
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        // Return a minimal metaData object
        return new BlobContainerMetaData(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        // Cast to BlobContainerMetaData, produce a BlobContainerSdkType
        if (!(metaData instanceof BlobContainerMetaData)) {
            throw new IllegalArgumentException("Expected BlobContainerMetaData, got: " + metaData.getClass());
        }
        BlobContainerMetaData containerMeta = (BlobContainerMetaData) metaData;
        return new BlobContainerSdkType(containerMeta);
    }
}
