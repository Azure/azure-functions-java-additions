/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.internal.MiddlewareContext;

public interface FunctionWorkerMiddleware {
    /**
     * TODO
     */
    void invoke(MiddlewareContext context, FunctionWorkerChain next) throws Exception;
}
