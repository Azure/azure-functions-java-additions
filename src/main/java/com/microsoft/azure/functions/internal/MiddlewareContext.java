/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.internal;

import com.microsoft.azure.functions.ExecutionContext;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Middleware Execution Context
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface MiddlewareContext extends ExecutionContext {
    // Used by durable middleware.
    /**
     * Returns the map with name field in parameter's annotation as key, and java.lang.reflect.Parameter type as object.
     * The map usually is used for middleware to check the right function method to intercept.
     * @return A hash map that contains parameter meta-data of customer function.
     */
    Map<String, Parameter> getParameterMap();

    /**
     * Returns corresponding parameter payload sent from host by checking the parameter name. The return type is Object
     * but the true type is String. Make it return Object to avoid break this API in the future.
     * @param name - The name of parameter
     * @return An object which will be String type that represents parameter payload of customer function.
     */
    Object getParameterPayloadByName(String name);

    /**
     * Updates the parameter payload by parameter name. This payload will be the actual payload used when invoke customer function.
     * This API give middleware ability to update function input.
     * @param key - The name of parameter being updated
     * @param value - The value of parameter being updated
     */
    void updateParameterPayloadByName(String key, Object value);

    /**
     * Returns the return value from customer function invocation.
     * @return An object that is the customer function return value.
     */
    Object getReturnValue();

    /**
     * Updates the return value that will be eventually sent back to host.
     * @param value - Middleware output value
     */
    void setMiddlewareOutput(Object value);
}
