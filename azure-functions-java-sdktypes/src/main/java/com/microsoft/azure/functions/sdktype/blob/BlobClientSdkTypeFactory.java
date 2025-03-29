package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException;

import java.lang.reflect.Parameter;

public class BlobClientSdkTypeFactory implements SdkTypeFactory {
    @Override
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        return new BlobClientMetaData(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        try {
            BlobClientMetaData blobClientMetaData = (BlobClientMetaData) metaData;
            return new BlobClientSdkType(blobClientMetaData, new BlobClientHydrator());
        } catch (ClassCastException cce) {
            throw new SdkTypeCreationException("Failed to cast metaData to BlobClientMetaData", cce);
        } catch (Exception ex) {
            throw new SdkTypeCreationException("Failed to create BlobClientSdkType", ex);
        }
    }
}
