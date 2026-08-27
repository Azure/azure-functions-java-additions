/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.spi.inject;

/**
 * The instance factory used by Azure Functions Java Worker to initialize customer function instances.
 * Implementations can optionally manage resources that require explicit cleanup.
 */
public interface FunctionInstanceInjector extends AutoCloseable {

    /**
     * This method is called by the Azure Functions Java Worker to create an instance of the class
     * containing customer-defined Azure Functions. Functions defined in the provided class will be invoked
     * on the returned instance.
     *
     * @param functionClass The class containing customer-defined functions.
     * @param <T>           The type of the customer functions class.
     * @return An instance created by the injector to invoke functions on.
     * @throws Exception if instance creation fails for any reason.
     */
    <T> T getInstance(Class<T> functionClass) throws Exception;

    /**
     * Closes this injector and releases any resources managed by it.
     * <p>
     * This method is called automatically by the Azure Functions Java Worker at worker shutdown.
     * Override this method if the injector manages resources (e.g., database connections, thread pools)
     * that need explicit closure to avoid resource leaks.
     *
     * <p>Default implementation is a no-op, preserving backward compatibility.</p>
     *
     * @throws Exception if cleanup fails for any reason.
     */
    default void close() throws Exception {
        // No-op default implementation.
    }
}
