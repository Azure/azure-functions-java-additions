/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.sdktype.exceptions.SdkAnalysisException;
import com.microsoft.azure.functions.sdktype.servicebus.ServiceBusSdkTypeFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public class SdkParameterAnalyzer {
    private final SdkTypeRegistry registry = new SdkTypeRegistry();

    public SdkParameterAnalysisResult analyze(Method method) {
        final SdkParameterAnalysisResult result = new SdkParameterAnalysisResult();
        for (final Parameter param : method.getParameters()) {
            final String fqcn = getSdkTypeName(param);
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

    private String getSdkTypeName(Parameter parameter) {
        final Class<?> type = parameter.getType();
        if (type.isArray() && ServiceBusSdkTypeFactory.MESSAGE.equals(type.getComponentType().getName())) {
            return ServiceBusSdkTypeFactory.MESSAGE;
        }
        final Type genericType = parameter.getParameterizedType();
        if (type == List.class && genericType instanceof ParameterizedType) {
            final Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class
                && ServiceBusSdkTypeFactory.MESSAGE.equals(((Class<?>) arguments[0]).getName())) {
                return ServiceBusSdkTypeFactory.MESSAGE;
            }
        }
        return type.getName();
    }
}
