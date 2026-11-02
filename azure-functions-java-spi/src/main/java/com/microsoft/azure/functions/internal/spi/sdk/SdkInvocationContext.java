/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.internal.spi.sdk;

import java.net.URI;
import java.util.List;

/**
 * What the worker hands to an SDK type while it builds the object for one function parameter.
 */
public interface SdkInvocationContext {
    /**
     * Returns the raw host payloads for the parameter being built.
     *
     * @return one payload for a single binding, several for a batch, or an empty list when the
     *         host sent none, for example for a type that is resolved from the context alone
     */
    List<ModelBindingData> getModelBindingData();

    /**
     * Returns the host auxiliary gRPC endpoint taken from the worker startup arguments.
     *
     * @return the endpoint, or null when the worker was not given one
     */
    URI getHostEndpoint();
}
