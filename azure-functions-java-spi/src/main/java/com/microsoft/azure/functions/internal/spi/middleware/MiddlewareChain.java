/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.internal.spi.middleware;

/**
 * The middleware chain.
 *
 * <p>This class is internal and is hence not for public use at this time. Its APIs are unstable and can change
 * at any time.
 */
public interface MiddlewareChain {
    /**
     * Invokes next middleware in the chain.
     * @param context execution context that pass along middleware chain
     * @throws Exception any exception that happen along middleware chain
     */
    void doNext(MiddlewareContext context) throws Exception;
}
