/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.servicebus;

import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import java.lang.reflect.Parameter;

/**
 * The parameter and type a Service Bus binding is built for. It has no required fields, because the
 * message bytes travel in the invocation context.
 */
public final class ServiceBusMetaData implements SdkTypeMetaData {
    private final String fqcn;
    private final Parameter parameter;

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
        return null;
    }

    @Override
    public void setFieldValue(String key, Object value) {
        throw new UnsupportedOperationException("Service Bus bindings have no metadata fields");
    }

    @Override
    public void parseAndVerify() {
        if (parameter == null) {
            throw new IllegalArgumentException("Service Bus binding requires a reflected parameter");
        }
    }
}
