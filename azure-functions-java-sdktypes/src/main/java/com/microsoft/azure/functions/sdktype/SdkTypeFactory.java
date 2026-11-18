/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

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
}
