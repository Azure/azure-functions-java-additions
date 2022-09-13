package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.ExecutionContext;

public interface FunctionWorkerChain {
    public void doNext(MiddlewareExecutionContext context) throws Exception;
}
