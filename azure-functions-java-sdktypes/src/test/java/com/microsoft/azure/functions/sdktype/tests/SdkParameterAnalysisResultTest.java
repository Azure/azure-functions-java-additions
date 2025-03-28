package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.SdkParameterAnalysisResult;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;

class SdkParameterAnalysisResultTest {

    static class FakeMetaData implements SdkTypeMetaData {
        private final String fqcn;
        FakeMetaData(String fqcn) { this.fqcn = fqcn; }

        @Override public Object getFieldValue(String key) { return null; }
        @Override public void setFieldValue(String key, Object value) {}
        @Override public void parseAndVerify() {}
        @Override public Parameter getParam() { return null; }
        @Override public String getFqcn() { return fqcn; }
    }

    @Test
    void testAddAndCheck() {
        SdkParameterAnalysisResult result = new SdkParameterAnalysisResult();
        assertFalse(result.hasAnySdkTypes());

        result.addSdkTypeMetaData(new FakeMetaData("com.example.MockFqcn"));
        assertTrue(result.hasAnySdkTypes());
        assertEquals(1, result.getSdkTypesMetaData().size());
        assertEquals("com.example.MockFqcn",
                result.getSdkTypesMetaData().get(0).getFqcn());
    }
}
