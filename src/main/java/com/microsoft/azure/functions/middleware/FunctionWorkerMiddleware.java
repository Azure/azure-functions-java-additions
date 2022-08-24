package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.ExecutionContext;

@FunctionalInterface
public interface FunctionWorkerMiddleware {
    public void invoke (ExecutionContext context, FunctionWorkerChain next);
}
