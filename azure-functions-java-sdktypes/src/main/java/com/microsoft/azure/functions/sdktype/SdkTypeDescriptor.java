/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, per-function description of a recognized SDK-type parameter.
 * It holds only data that never changes after analysis: the recognized fqcn,
 * the reflected {@link Parameter} and the names of the raw values needed to
 * build the SDK object.
 *
 * <p><b>Thread safety.</b> Implementations must be immutable, so a single descriptor
 * can be shared by any number of concurrent invocations. {@link #bind(Map)} must
 * not read or write any state other than the descriptor's final fields.</p>
 *
 * <p>Per-invocation values never live on the descriptor. Each invocation calls
 * {@link #bind(Map)} with its own raw values and receives a new, immutable
 * {@link SdkTypeMetaData}.</p>
 */
public interface SdkTypeDescriptor {
    /**
     * The recognized fqcn, used by the registry to pick the matching {@link SdkTypeFactory}.
     *
     * @return the fully qualified class name
     */
    String getFqcn();

    /**
     * The parameter of the user function that uses this SDK type.
     *
     * @return the parameter
     */
    Parameter getParam();

    /**
     * The names of the raw values that {@link #bind(Map)} requires.
     *
     * @return an unmodifiable set of required field names
     */
    Set<String> getRequiredFields();

    /**
     * Validates the raw values of one invocation and creates a new, immutable
     * {@link SdkTypeMetaData} from them. Values are copied during the call; the map
     * is neither retained nor modified, so the caller may reuse it afterwards.
     *
     * @param rawValues the raw values of one invocation, keyed by the names in {@link #getRequiredFields()}
     * @return a new immutable metadata object for this invocation
     * @throws IllegalArgumentException if rawValues is null or a required value is missing or empty
     */
    SdkTypeMetaData bind(Map<String, String> rawValues);
}
