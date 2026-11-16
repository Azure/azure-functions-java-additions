/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Holds discovered SdkTypeDescriptor objects from analyzing a method's parameters.
 * Typically used by the SdkParameterAnalyzer to store recognized SDK-based parameters.
 * The result is immutable, and so are the descriptors, so both can be shared by
 * concurrent invocations.
 */
public final class SdkParameterAnalysisResult {
    private final List<SdkTypeDescriptor> sdkTypeDescriptors;

    public SdkParameterAnalysisResult(List<SdkTypeDescriptor> sdkTypeDescriptors) {
        this.sdkTypeDescriptors = Collections.unmodifiableList(new ArrayList<>(sdkTypeDescriptors));
    }

    public List<SdkTypeDescriptor> getSdkTypeDescriptors() {
        return sdkTypeDescriptors;
    }

    public boolean hasAnySdkTypes() {
        return !sdkTypeDescriptors.isEmpty();
    }
}
