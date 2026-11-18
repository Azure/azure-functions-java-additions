/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;
import com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException;

import java.lang.reflect.Parameter;

public interface SdkTypeFactory {
    /**
     * Called by the build tool's analyzer, returning an immutable descriptor
     * that references the recognized FQCN and parameter. The descriptor is
     * created once per function and shared by all invocations.
     *
     * @param fqcn the fully qualified class name of the SDK type
     * @param param the parameter being analyzed
     * @return the created SdkTypeDescriptor
     * @throws com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException
     *         if something prevents descriptor creation (e.g., reflection issues).
     */
    SdkTypeDescriptor createDescriptor(String fqcn, Parameter param) throws SdkTypeCreationException;

    /**
     * Called at runtime in the worker. Takes the immutable metaData of one
     * invocation (created by {@link SdkTypeDescriptor#bind(java.util.Map)}) and returns the final SdkType.
     *
     * @param metaData the metadata containing configuration details
     * @return the created SdkType
     * @throws com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException
     *         if building the final SdkType fails due to reflection or invalid metaData.
     */
    SdkType<?> createSdkType(SdkTypeMetaData metaData) throws SdkTypeCreationException;

    /**
     * Like {@link #createSdkType(SdkTypeMetaData)}, and also hands over a cache so that the SdkType can share
     * expensive objects, such as a credential, between builds. The registry passes a view of the worker's cache
     * that is bound to one namespace, supports only {@code computeIfAbsent} and cannot reach the entries of the
     * worker. Use a key class of your own for each kind of object. The default implementation ignores the cache.
     *
     * @param metaData the metadata containing configuration details
     * @param cache a cache scoped to the sdktypes library, or null when there is none
     * @return the created SdkType
     * @throws com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException
     *         if building the final SdkType fails due to reflection or invalid metaData.
     */
    default SdkType<?> createSdkType(SdkTypeMetaData metaData, ObjectCache<CacheKey, Object> cache)
            throws SdkTypeCreationException {
        return createSdkType(metaData);
    }
}
