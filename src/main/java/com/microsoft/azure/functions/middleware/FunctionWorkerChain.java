/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.middleware;

public interface FunctionWorkerChain {
    void doNext(MiddlewareExecutionContext context) throws Exception;
}
