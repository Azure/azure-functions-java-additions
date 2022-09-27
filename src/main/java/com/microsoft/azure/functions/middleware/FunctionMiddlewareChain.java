/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.internal.MiddlewareContext;

/**
 * The function middleware chain.
 *
 * @since 1.1.0
 */
public interface FunctionMiddlewareChain {
    /**
     * Invokes next middleware, usually used at the end of middleware to invoke next middleware in the middleware chain.
     * @param context execution context that pass along middleware chain
     * @throws Exception any exception that happen along middleware chain
     */
    void doNext(MiddlewareContext context) throws Exception;
}
