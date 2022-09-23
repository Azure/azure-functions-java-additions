/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.internal.MiddlewareContext;

public interface FunctionWorkerChain {
    /**
     * This method invoke next middleware, usually used at the end of middleware to invoke next middleware in the chain
     * @param context - Execution context that pass to along middlewares.
     */
    void doNext(MiddlewareContext context) throws Exception;
}
