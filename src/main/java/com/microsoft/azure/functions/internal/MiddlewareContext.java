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
     * Returns the name of parameter defined in customer function. The input name is the simple name of desired trigger class.
     * @param name - The simple name of desired trigger.
     * @return The name of parameter defined in customer function.
     */
    Optional<String> getParameterName(String name);

    /**
     * Returns corresponding parameter payload sent from host by the given the parameter name. The return type is Object
     * but the real type is String. Make it return Object to avoid break this API in the future.
     * @param name - The name of parameter
     * @return An object which will be String type that represents parameter payload of customer function.
     */
    Object getParameterPayloadByName(String name);

    /**
     * Updates the parameter payload by parameter name. This payload will be the actual parameter payload
     * used when invoke customer function. This API give middleware ability to update function input.
     * @param key - The name of parameter to be updated
     * @param value - The value of parameter to be updated
     */
    void updateParameterPayloadByName(String key, Object value);

    /**
     * Returns the return value from customer function invocation.
     * @return An object that is the return value of customer functions.
     */
    Object getReturnValue();

    /**
     * Updates the return value that will eventually be sent back to host.
     * @param value - Middleware output value that will eventually be sent back to host
     */
    void setMiddlewareOutput(Object value);
}
