/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.dihook;

/**
 * The instance factory that used by DI framework to initialize function instance.
 *
 * @since 1.1.0
 */
public interface FunctionInstanceFactory {
    /**
     * This method is used by DI framework to initialize DI container. This method takes in the customer class and return
     * an instance create by the DI framework, later customer function will be invoked base on this class instance.
     * @param functionClass - The class that contains customer functions
     * @return The function instance that will be invoked on by azure functions java worker
     */
    <T> T getInstance(Class<T> functionClass) throws Exception;
}
