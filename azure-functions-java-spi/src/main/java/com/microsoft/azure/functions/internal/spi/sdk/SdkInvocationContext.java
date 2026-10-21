/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.internal.spi.sdk;

import java.net.URI;
import java.util.List;

/**
 * Invocation-local data for SDK bindings. Implementations must not be shared
 * between invocations.
 */
public interface SdkInvocationContext {
    String getInvocationId();

    /**
     * Returns immutable, ordered payloads for a host binding, or an empty list.
     *
     * @param bindingName host binding name, not the Java parameter name
     * @return immutable payload list
     */
    List<ModelBindingData> getModelBindingData(String bindingName);

    /**
     * @return host auxiliary endpoint from worker startup, or null if unavailable
     */
    URI getHostEndpoint();

    /**
     * The worker invalidates this context after invocation middleware exits,
     * including on cancellation and failure.
     *
     * @return whether this invocation can still perform host operations
     */
    boolean isActive();

    /**
     * Registers invocation-owned state for cleanup on every exit path.
     * Shared transports must not be registered here.
     * Registration must be atomic with invalidation, and must reject an
     * inactive context. Cleanup callbacks run outside the registration lock.
     *
     * @param resource resource closed by the worker when the invocation exits
     * @throws IllegalStateException if the invocation has ended
     */
    void registerCloseable(AutoCloseable resource);
}
