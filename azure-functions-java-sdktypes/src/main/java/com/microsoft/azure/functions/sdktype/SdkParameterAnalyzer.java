/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.sdktype.exceptions.SdkAnalysisException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class SdkParameterAnalyzer {
    private final SdkTypeRegistry registry = new SdkTypeRegistry();

    public SdkParameterAnalysisResult analyze(Method method) {
        final List<SdkTypeDescriptor> descriptors = new ArrayList<>();
        for (final Parameter param : method.getParameters()) {
            final String fqcn = param.getType().getName();
            if (registry.isTypeSupported(fqcn)) {
                try {
                    descriptors.add(registry.createDescriptor(fqcn, param));
                } catch (Exception ex) {
                    // Wrap the underlying issue in a custom SdkAnalysisException for clarity
                    throw new SdkAnalysisException("Failed to create descriptor for recognized type: " + fqcn, ex);
                }
            }
        }

        return new SdkParameterAnalysisResult(descriptors);
    }

    public SdkTypeRegistry getRegistry() {
        return this.registry;
    }
}
