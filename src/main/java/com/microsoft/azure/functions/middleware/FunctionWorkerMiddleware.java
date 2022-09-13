package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.ExecutionContext;

public interface FunctionWorkerMiddleware {
    public void invoke (MiddlewareExecutionContext context, FunctionWorkerChain next) throws Exception;
}
