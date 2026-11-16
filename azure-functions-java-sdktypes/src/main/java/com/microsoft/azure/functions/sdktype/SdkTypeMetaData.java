/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import java.lang.reflect.Parameter;

/**
 * Immutable, validated values for one invocation of a recognized SDK type.
 * Instances are produced by {@link SdkTypeDescriptor#bind(java.util.Map)}.
 *
 * <p><b>Thread safety.</b> Implementations must be immutable: all fields final,
 * no setters and no mutable state exposed through getters. An instance can then be
 * passed freely between threads, held by an {@link SdkType} and used as the source
 * of a cache key and of the built SDK object without any further synchronization.</p>
 */
public interface SdkTypeMetaData {
    /**
     * Return a Parameter object for the argument that uses this SDK type.
     *
     * @return the parameter
     */
    Parameter getParam();

    /**
     * The recognized fqcn, used so the registry can do createSdkType(...)
     * at runtime without re-checking param type.
     *
     * @return the fully qualified class name
     */
    String getFqcn();
}

