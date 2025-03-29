package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.sdktype.exceptions.SdkAnalysisException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class SdkParameterAnalyzer {
    private final SdkTypeRegistry registry = new SdkTypeRegistry();

    public SdkParameterAnalysisResult analyze(Method method) {
        SdkParameterAnalysisResult result = new SdkParameterAnalysisResult();
        for (Parameter param : method.getParameters()) {
            String fqcn = param.getType().getName();
            if (registry.isRecognizedType(fqcn)) {
                try {
                    SdkTypeMetaData sdkTypeMetaData = registry.createMetaData(fqcn, param);
                    result.addSdkTypeMetaData(sdkTypeMetaData);
                } catch (Exception ex) {
                    // Wrap the underlying issue in a custom SdkAnalysisException for clarity
                    throw new SdkAnalysisException("Failed to create metadata for recognized type: " + fqcn, ex);
                }
            }
        }

        return result;
    }

    public SdkTypeRegistry getRegistry() { return this.registry; }
}