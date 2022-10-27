/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.spi.inject;

/**
 * The instance factory used by DI framework to initialize function instance.
 *
 * @since 1.0.0
 */
public interface FunctionInstanceInjector {
    /**
     * This method is used by DI framework to initialize the function instance. This method takes in the customer class and returns
     * an instance create by the DI framework, later customer functions will be invoked on this instance.
     * @param functionClass the class that contains customer functions
     * @param <T> customer functions class type
     * @return the instance that will be invoked on by azure functions java worker
     * @throws Exception any exception that is thrown by the DI framework during instance creation
     */
    <T> T getInstance(Class<T> functionClass) throws Exception;
}
