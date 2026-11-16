package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
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
    void testIsTypeSupported() {
        SdkTypeRegistry registry = new SdkTypeRegistry();
        assertTrue(registry.isTypeSupported("com.azure.storage.blob.BlobClient"));
        assertFalse(registry.isTypeSupported("com.example.UnrecognizedClient"));
    }

    @Test
    void testCreateDescriptorRecognized() throws Exception {
        SdkTypeRegistry registry = new SdkTypeRegistry();
        Method m = ExampleFunctions.class
                .getMethod("exampleBlobClientMethod", com.azure.storage.blob.BlobClient.class);
        Parameter p = m.getParameters()[0];

        SdkTypeDescriptor descriptor = registry.createDescriptor("com.azure.storage.blob.BlobClient", p);
        assertNotNull(descriptor);
        assertEquals("com.azure.storage.blob.BlobClient", descriptor.getFqcn());
        assertEquals(p, descriptor.getParam());
    }

    @Test
    void testCreateDescriptorUnrecognizedThrows() {
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
            registry.createDescriptor("com.example.Unrecognized", finalDummyParam);
        });
    }
}
