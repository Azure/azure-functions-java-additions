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

        public void twoRecognizedMethod(com.azure.storage.blob.BlobClient blobClient,
                                        com.azure.storage.blob.BlobContainerClient containerClient) {
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
        assertEquals(1, result.getSdkTypeDescriptors().size());
        SdkTypeDescriptor descriptor = result.getSdkTypeDescriptors().get(0);
        assertEquals("com.azure.storage.blob.BlobClient", descriptor.getFqcn());
        assertSame(m.getParameters()[0].getType(), descriptor.getParam().getType());
        assertEquals(m.getParameters()[0], descriptor.getParam());
    }

    @Test
    void testAnalyzeKeepsOneDescriptorPerRecognizedParameter() throws NoSuchMethodException {
        SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
        Method m = TestFunctions.class.getMethod("twoRecognizedMethod",
                com.azure.storage.blob.BlobClient.class, com.azure.storage.blob.BlobContainerClient.class);

        SdkParameterAnalysisResult result = analyzer.analyze(m);

        assertEquals(2, result.getSdkTypeDescriptors().size());
        assertEquals(m.getParameters()[0], result.getSdkTypeDescriptors().get(0).getParam());
        assertEquals(m.getParameters()[1], result.getSdkTypeDescriptors().get(1).getParam());
        assertNotSame(result.getSdkTypeDescriptors().get(0), result.getSdkTypeDescriptors().get(1));
    }

    @Test
    void testAnalyzeUnrecognizedMethod() throws NoSuchMethodException {
        SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
        Method m = TestFunctions.class.getMethod("unrecognizedMethod", String.class);

        SdkParameterAnalysisResult result = analyzer.analyze(m);
        assertFalse(result.hasAnySdkTypes(), "Expected no recognized param");
        assertEquals(0, result.getSdkTypeDescriptors().size());
    }
}
