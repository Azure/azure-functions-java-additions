package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

class SdkParameterAnalyzerTest {

    static class TestFunctions {
        public void recognizedMethod(com.azure.storage.blob.BlobClient blobClient,
                                     String someOtherParam) {
            // no-op
        }

        public void unrecognizedMethod(String onlyString) {
            // no-op
        }
    }

    @Test
    void testAnalyzeRecognizedMethod() throws NoSuchMethodException {
        SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
        Method m = TestFunctions.class.getMethod("recognizedMethod",
                com.azure.storage.blob.BlobClient.class, String.class);

        SdkParameterAnalysisResult result = analyzer.analyze(m);

        assertTrue(result.hasAnySdkTypes(), "Expected at least one recognized param");
        assertEquals(1, result.getSdkTypesMetaData().size());
        SdkTypeMetaData meta = result.getSdkTypesMetaData().get(0);
        assertEquals("com.azure.storage.blob.BlobClient", meta.getFqcn());
    }

    @Test
    void testAnalyzeUnrecognizedMethod() throws NoSuchMethodException {
        SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
        Method m = TestFunctions.class.getMethod("unrecognizedMethod", String.class);

        SdkParameterAnalysisResult result = analyzer.analyze(m);
        assertFalse(result.hasAnySdkTypes(), "Expected no recognized param");
        assertEquals(0, result.getSdkTypesMetaData().size());
    }
}
