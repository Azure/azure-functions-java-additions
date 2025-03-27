package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

import java.lang.reflect.Parameter;

public class BlobClientSdkTypeFactory implements SdkTypeFactory {
    @Override
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        return new BlobClientMetaData(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        BlobClientMetaData blobClientMetaData = (BlobClientMetaData) metaData;
        return new BlobClientSdkType(blobClientMetaData, new BlobClientHydrator());
    }
}
