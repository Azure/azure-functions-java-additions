/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;

/**
 * A hydrator that builds the final client instance
 * from a given SdkTypeMetaData object.
 *
 * @param <M> the type of MetaData used
 */
public interface SdkTypeHydrator<M extends SdkTypeMetaData> {
    /**
     * Build the final object using data from the metaData.
     *
     * @param metaData the metadata containing configuration details
     * @return the built client instance
     * @throws Exception if client creation fails
     */
    Object createInstance(M metaData) throws Exception;

    /**
     * Builds an invocation-local object. Existing hydrators retain their behavior.
     *
     * @param metaData fresh metadata owned by this invocation
     * @param context invocation data and host endpoint
     * @return hydrated object
     * @throws Exception if hydration fails
     */
    default Object createInstance(M metaData, SdkInvocationContext context) throws Exception {
        return createInstance(metaData);
    }
}
