/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.internal;

import com.microsoft.azure.functions.ExecutionContext;

import java.util.Optional;

/**
 * Middleware Execution Context
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface MiddlewareContext extends ExecutionContext {
    /**
     * Returns the name of parameter defined in customer function.
     * The input is the simple class name of target annotation.
     * @param annotationSimpleClassName the simple class name of target annotation
     * @return the name of parameter defined in customer function
     */
    Optional<String> getParameterName(String annotationSimpleClassName);

    /**
     * Returns corresponding parameter value sent from host by the given the parameter name.
     * The return type is Object but the real type is String (currently only support get String type,
     * planning to support other types in the future.)
     * Make it return Object to avoid break this API in the future.
     * @param name the name of parameter
     * @return an object which will be String type that represents parameter value of customer function
     */
    Object getParameterValue(String name);

    /**
     * Updates the parameter value by parameter name. It will be the actual parameter value
     * used when invoke customer function. This API give middleware ability to update function input.
     * @param name the name of parameter to be updated
     * @param value the value of parameter to be updated
     */
    void updateParameterValue(String name, Object value);

    /**
     * Returns the return value from customer function invocation.
     * @return an object that is the return value of customer function
     */
    Object getReturnValue();

    /**
     * Updates the return value from customer function invocation.
     * @param returnValue value that will be updated as function return value.
     */
    void setReturnValue(Object returnValue);
}
