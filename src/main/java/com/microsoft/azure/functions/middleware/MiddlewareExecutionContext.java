/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.ExecutionContext;
import java.lang.reflect.Parameter;
import java.util.Map;

//TODO: think about using aggregation
public interface MiddlewareExecutionContext extends ExecutionContext {

    Object getReturnValue();

    void setMiddlewareOutput(Object value);

    void setFunctionInstance(Object functionInstance);

    Class<?> getContainingClass();

    Map<String, Parameter> getParameterMap();

    Object getParameterPayloadByName(String name);

    void updateParameterPayloadByName(String name, Object payload);
}
