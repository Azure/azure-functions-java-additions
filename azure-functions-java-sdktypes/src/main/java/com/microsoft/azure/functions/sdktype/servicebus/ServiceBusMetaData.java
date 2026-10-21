/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.servicebus;

import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * Static parameter information. Binary payloads remain in the invocation context.
 */
public final class ServiceBusMetaData implements SdkTypeMetaData {
    private final String fqcn;
    private final Parameter parameter;
    private final Map<String, Object> fields = new HashMap<>();

    public ServiceBusMetaData(String fqcn, Parameter parameter) {
        this.fqcn = fqcn;
        this.parameter = parameter;
    }

    @Override
    public String getFqcn() {
        return fqcn;
    }

    @Override
    public Parameter getParam() {
        return parameter;
    }

    @Override
    public Object getFieldValue(String key) {
        return fields.get(key);
    }

    @Override
    public void setFieldValue(String key, Object value) {
        fields.put(key, value);
    }

    @Override
    public void parseAndVerify() {
        if (parameter == null) {
            throw new IllegalArgumentException("Service Bus binding requires a reflected parameter");
        }
    }
}
