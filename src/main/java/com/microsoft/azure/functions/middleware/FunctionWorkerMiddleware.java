/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.internal.MiddlewareContext;

/**
 * This interface is implemented by middlewares to include middleware core logics.
 *
 * @since 1.1.0
 */
public interface FunctionWorkerMiddleware {
    /**
     * This method contains middleware logics.
     * @param context - Execution context that pass along middleware chain.
     * @param next - Function middleware chain {@link FunctionMiddlewareChain}
     */
    void invoke(MiddlewareContext context, FunctionMiddlewareChain next) throws Exception;
}
