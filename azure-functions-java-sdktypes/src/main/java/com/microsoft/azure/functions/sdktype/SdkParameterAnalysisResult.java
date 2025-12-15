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
 * Holds discovered SdkTypeMetaData objects from analyzing a method's parameters.
 * Typically used by the SdkParameterAnalyzer to store recognized SDK-based parameters.
 */
public class SdkParameterAnalysisResult {
    private final List<SdkTypeMetaData> sdkTypeMetaDatas = new ArrayList<>();

    public void addSdkTypeMetaData(SdkTypeMetaData sdkTypeMetaData) {
        this.sdkTypeMetaDatas.add(sdkTypeMetaData);
    }

    public List<SdkTypeMetaData> getSdkTypesMetaData() {
        return Collections.unmodifiableList(sdkTypeMetaDatas);
    }

    public boolean hasAnySdkTypes() {
        return !sdkTypeMetaDatas.isEmpty();
    }
}
