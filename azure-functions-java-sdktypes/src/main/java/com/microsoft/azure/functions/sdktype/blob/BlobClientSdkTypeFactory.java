package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException;

import java.lang.reflect.Parameter;

/**
 * SdkTypeFactory for building a BlobClientSdkType from BlobClientMetaData.
 * Potentially throws SdkTypeCreationException if metaData is invalid or reflection fails.
 */
public class BlobClientSdkTypeFactory implements SdkTypeFactory {
    @Override
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws SdkTypeCreationException {
        return new BlobClientMetaData(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws SdkTypeCreationException {
        BlobClientMetaData blobClientMetaData = (BlobClientMetaData) metaData;
        return new BlobClientSdkType(blobClientMetaData, new BlobClientHydrator());
    }
}
