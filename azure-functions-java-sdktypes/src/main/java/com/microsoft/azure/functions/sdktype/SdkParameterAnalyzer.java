/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.sdktype.exceptions.SdkAnalysisException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class SdkParameterAnalyzer {
    private final SdkTypeRegistry registry = new SdkTypeRegistry();

    public SdkParameterAnalysisResult analyze(Method method) {
        final SdkParameterAnalysisResult result = new SdkParameterAnalysisResult();
        for (final Parameter param : method.getParameters()) {
            final String fqcn = param.getType().getName();
            if (registry.isTypeSupported(fqcn)) {
                try {
                    final SdkTypeMetaData sdkTypeMetaData = registry.createMetaData(fqcn, param);
                    result.addSdkTypeMetaData(sdkTypeMetaData);
                } catch (Exception ex) {
                    // Wrap the underlying issue in a custom SdkAnalysisException for clarity
                    throw new SdkAnalysisException("Failed to create metadata for recognized type: " + fqcn, ex);
                }
            }
        }

        return result;
    }

    public SdkTypeRegistry getRegistry() {
        return this.registry;
    }
}
