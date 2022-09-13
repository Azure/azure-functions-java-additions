package com.microsoft.azure.functions.middleware;

import com.microsoft.azure.functions.ExecutionContext;

/**
 * Expands {@link ExecutionContext} so that {@link FunctionWorkerMiddleware} implementations
 * can modify target function instance or get more information about the target function.
 */
public interface MiddlewareExecutionContext extends ExecutionContext {
    public ClassLoader getFunctionClassLoader();
    public Class getFunctionClass();

    public Object getFunctionInstance();

    /**
     * Allows middleware to override target function instance for this invocation.
     * The default behavior is for the instance to be creatd by the Azure Functions runtime.
     *
     * @param obj
     */
    public void setFunctionInstance(Object obj);
}
