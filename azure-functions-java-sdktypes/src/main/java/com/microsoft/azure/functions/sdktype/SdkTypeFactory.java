package com.microsoft.azure.functions.sdktype;

import java.lang.reflect.Parameter;

public interface SdkTypeFactory {
    /**
     * Called by the build tool’s analyzer.
     * Returns a minimal SdkTypeMetaData that references the recognized FQCN internally.
     */
    SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception;

    /**
     * Called at runtime in the worker. Takes the already-created metaData
     * (which has the FQCN or typeId stored) and returns the final SdkType.
     */
    SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception;
}