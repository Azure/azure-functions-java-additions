package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.SdkParameterAnalysisResult;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SdkParameterAnalysisResultTest {

    static class FakeDescriptor implements SdkTypeDescriptor {
        private final String fqcn;
        FakeDescriptor(String fqcn) { this.fqcn = fqcn; }

        @Override public String getFqcn() { return fqcn; }
        @Override public Parameter getParam() { return null; }
        @Override public Set<String> getRequiredFields() { return Collections.emptySet(); }
        @Override public SdkTypeMetaData bind(Map<String, String> rawValues) { return null; }
    }

    @Test
    void testEmptyAndNonEmpty() {
        assertFalse(new SdkParameterAnalysisResult(Collections.<SdkTypeDescriptor>emptyList()).hasAnySdkTypes());

        SdkParameterAnalysisResult result = new SdkParameterAnalysisResult(
                Collections.<SdkTypeDescriptor>singletonList(new FakeDescriptor("com.example.MockFqcn")));
        assertTrue(result.hasAnySdkTypes());
        assertEquals(1, result.getSdkTypeDescriptors().size());
        assertEquals("com.example.MockFqcn",
                result.getSdkTypeDescriptors().get(0).getFqcn());
    }

    @Test
    void testResultIsNotAffectedByLaterChangesToTheInputList() {
        List<SdkTypeDescriptor> input = new ArrayList<>();
        input.add(new FakeDescriptor("com.example.First"));

        SdkParameterAnalysisResult result = new SdkParameterAnalysisResult(input);
        input.add(new FakeDescriptor("com.example.Second"));

        assertEquals(1, result.getSdkTypeDescriptors().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getSdkTypeDescriptors().add(new FakeDescriptor("com.example.Third")));
    }
}
