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
    public SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception {
        // Return a minimal metaData object
        return new BlobContainerMetaData(fqcn, param);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        try {
            BlobContainerMetaData containerMeta = (BlobContainerMetaData) metaData;
            return new BlobContainerSdkType(containerMeta);
        } catch (ClassCastException cce) {
            throw new SdkTypeCreationException("Failed to cast metaData to BlobContainerMetaData", cce);
        } catch (Exception ex) {
            throw new SdkTypeCreationException("Failed to create BlobContainerClientSdkType", ex);
        }
    }
}
