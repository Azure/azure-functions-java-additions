/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.internal.spi.middleware;

/**
 * This interface is implemented by middlewares to include middleware core logics.
 *
 * <p>This class is internal and is hence not for public use at this time. Its APIs are unstable and can change
 * at any time.
 */
public interface Middleware {
    /**
     * Middlewares will override this method to include their own logics.
     * @param context execution context that pass along middleware chain
     * @param chain middleware chain {@link MiddlewareChain}
     * @throws Exception any exception that is thrown out in next middleware
     */
    void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception;
}
