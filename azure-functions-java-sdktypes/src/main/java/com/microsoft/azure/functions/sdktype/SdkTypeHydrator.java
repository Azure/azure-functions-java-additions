/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

/**
 * A hydrator that builds the final client instance
 * from a given SdkTypeMetaData object.
 *
 * @param <M> the type of MetaData used
 */
public interface SdkTypeHydrator<M extends SdkTypeMetaData> {
    /**
     * Build the final object using data from the metaData.
     */
    Object createInstance(M metaData) throws Exception;
}
