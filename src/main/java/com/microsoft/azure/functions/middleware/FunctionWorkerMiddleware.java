/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

public interface FunctionWorkerMiddleware {
    void invoke(MiddlewareExecutionContext context, FunctionWorkerChain next) throws Exception;
}
