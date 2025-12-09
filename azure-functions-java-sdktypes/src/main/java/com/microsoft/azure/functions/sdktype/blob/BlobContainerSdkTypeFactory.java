package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException;

import java.lang.reflect.Parameter;

/**
 * SdkTypeFactory for BlobContainerClient scenario.
 */
public class BlobContainerSdkTypeFactory implements SdkTypeFactory {

    @Override
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws SdkTypeCreationException {
        // Return a minimal metaData object
        return new BlobContainerMetaData(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws SdkTypeCreationException {
        final BlobContainerMetaData containerMeta = (BlobContainerMetaData) metaData;
        return new BlobContainerSdkType(containerMeta);
    }
}
