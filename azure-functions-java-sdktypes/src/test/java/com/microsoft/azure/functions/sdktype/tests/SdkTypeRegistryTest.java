package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.exceptions.SdkRegistryException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

class SdkTypeRegistryTest {

    static class ExampleFunctions {
        public void exampleBlobClientMethod(com.azure.storage.blob.BlobClient bc) {}
    }

    @Test
    void testIsRecognizedType() {
        SdkTypeRegistry registry = new SdkTypeRegistry();
        assertTrue(registry.isRecognizedType("com.azure.storage.blob.BlobClient"));
        assertFalse(registry.isRecognizedType("com.example.UnrecognizedClient"));
    }

    @Test
    void testCreateMetaDataRecognized() throws Exception {
        SdkTypeRegistry registry = new SdkTypeRegistry();
        Method m = ExampleFunctions.class
                .getMethod("exampleBlobClientMethod", com.azure.storage.blob.BlobClient.class);
        Parameter p = m.getParameters()[0];

        SdkTypeMetaData meta = registry.createMetaData("com.azure.storage.blob.BlobClient", p);
        assertNotNull(meta);
        assertEquals("com.azure.storage.blob.BlobClient", meta.getFqcn());
        assertEquals(p, meta.getParam());
    }

    @Test
    void testCreateMetaDataUnrecognizedThrows() {
        SdkTypeRegistry registry = new SdkTypeRegistry();
        Parameter dummyParam = null;
        try {
            Method m = ExampleFunctions.class.getMethod("exampleBlobClientMethod",
                    com.azure.storage.blob.BlobClient.class);
            dummyParam = m.getParameters()[0];
        } catch (Exception ex) {
            fail("Should not fail retrieving method param");
        }

        // try an unrecognized FQCN
        Parameter finalDummyParam = dummyParam;
        assertThrows(SdkRegistryException.class, () -> {
            registry.createMetaData("com.example.Unrecognized", finalDummyParam);
        });
    }
}
