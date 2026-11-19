/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

/**
 * A recognized SDK type that:
 *   - Holds the immutable metadata of one invocation
 *   - Has a reference to a hydrator
 *   - Has default method buildInstance() that calls the hydrator
 *     with the metaData.
 *
 * <p>An SdkType decides for itself whether anything is cached. The worker hands its object cache to the
 * factory, see {@link SdkTypeFactory#createSdkType(SdkTypeMetaData, com.microsoft.azure.functions.cache.ObjectCache)},
 * and always calls {@link #buildInstance()}. A type that needs no cache just does not use the one it is given.</p>
 *
 * <p>The metadata is already validated by {@link SdkTypeDescriptor#bind(java.util.Map)}
 * and is immutable, so an SdkType is safe to share between threads as long as its
 * hydrator is.</p>
 *
 * @param <M> The concrete type implementing SdkTypeMetaData
 */
public interface SdkType<M extends SdkTypeMetaData> {

    /**
     * Return the immutable metadata object of this invocation.
     *
     * @return the metadata object
     */
    M getMetaData();

    /**
     * Return the hydrator that builds the final instance using the metaData.
     *
     * @return the hydrator
     */
    SdkTypeHydrator<M> getHydrator();

    /**
     * Build or retrieve a final instance of the SDK object.
     * Calls the hydrator with the metaData.
     *
     * @return The fully built client or SDK object.
     * @throws Exception if creation fails inside the hydrator.
     */
    default Object buildInstance() throws Exception {
        final M metaData = getMetaData();
        final SdkTypeHydrator<M> hydrator = getHydrator();
        try {
            return hydrator.createInstance(metaData);
        } catch (Exception ex) {
            // Convert any reflection error to SdkTypeCreationException
            throw new com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException(
                    "Failed to build instance for fqcn = " + metaData.getFqcn(), ex
            );
        }
    }
}
