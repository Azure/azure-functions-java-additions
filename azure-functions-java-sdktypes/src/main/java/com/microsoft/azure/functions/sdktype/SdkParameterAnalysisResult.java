package com.microsoft.azure.functions.sdktype;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Holds discovered SdkTypeMetaData objects from analyzing a method's parameters.
 * Typically used by the SdkParameterAnalyzer to store recognized SDK-based parameters.
 */
public class SdkParameterAnalysisResult {
    private final List<SdkTypeMetaData> sdkTypeMetaDatas = new ArrayList<>();

    public void addSdkTypeMetaData(SdkTypeMetaData SdkTypeMetaData) {
        this.sdkTypeMetaDatas.add(SdkTypeMetaData);
    }

    public List<SdkTypeMetaData> getSdkTypesMetaData() {
        return Collections.unmodifiableList(sdkTypeMetaDatas);
    }

    public boolean hasAnySdkTypes() {
        return !sdkTypeMetaDatas.isEmpty();
    }
}