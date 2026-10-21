package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvocationHydrationTest {
    @Test
    void modelDataDefensivelyCopiesBinaryContent() {
        byte[] content = {(byte) 0xff, 0, 16};
        ModelBindingData data = new ModelBindingData("1.0", "source", "application/octet-stream", content);
        content[0] = 0;
        byte[] returned = data.getContent();
        returned[1] = 1;
        assertArrayEquals(new byte[] {(byte) 0xff, 0, 16}, data.getContent());
        assertThrows(NullPointerException.class, () -> new ModelBindingData("1", "s", "t", null));
    }

    @Test
    void legacyHydratorStillBuildsWithoutContext() throws Exception {
        SdkTypeMetaData metadata = new SdkTypeRegistry().createMetaData(
            "com.azure.storage.blob.BlobClient", null);
        metadata.setFieldValue("ContainerName", "container");
        metadata.setFieldValue("BlobName", "blob");
        metadata.setFieldValue("Connection", "storage");
        SdkType<SdkTypeMetaData> type = new SdkType<SdkTypeMetaData>() {
            public SdkTypeMetaData getMetaData() { return metadata; }
            public SdkTypeHydrator<SdkTypeMetaData> getHydrator() { return ignored -> "legacy"; }
        };
        assertEquals("legacy", type.buildInstance());
        assertEquals("legacy", type.buildInstance(context()));
    }

    @Test
    void contextAwareHydratorReceivesExactInvocationContext() throws Exception {
        SdkTypeMetaData metadata = new SdkTypeRegistry().createMetaData(
            "com.azure.storage.blob.BlobClient", null);
        metadata.setFieldValue("ContainerName", "container");
        metadata.setFieldValue("BlobName", "blob");
        metadata.setFieldValue("Connection", "storage");
        SdkInvocationContext expected = context();
        SdkType<SdkTypeMetaData> type = new SdkType<SdkTypeMetaData>() {
            public SdkTypeMetaData getMetaData() { return metadata; }
            public SdkTypeHydrator<SdkTypeMetaData> getHydrator() {
                return new SdkTypeHydrator<SdkTypeMetaData>() {
                    public Object createInstance(SdkTypeMetaData ignored) { fail("Legacy overload called"); return null; }
                    public Object createInstance(SdkTypeMetaData ignored, SdkInvocationContext actual) {
                        return actual;
                    }
                };
            }
        };
        assertSame(expected, type.buildInstance(expected));
    }

    @Test
    void metadataIsFreshAndNotCopiedFromAnalysisState() throws Exception {
        Parameter parameter = InvocationHydrationTest.class.getDeclaredMethod("fixture", String.class)
            .getParameters()[0];
        SdkTypeRegistry registry = new SdkTypeRegistry();
        SdkTypeMetaData analysis = registry.createMetaData(
            "com.azure.messaging.servicebus.ServiceBusReceivedMessage", parameter);
        analysis.setFieldValue("invocation", "previous");
        SdkTypeMetaData invocation = registry.createMetaData(analysis.getFqcn(), analysis.getParam());
        assertNotSame(analysis, invocation);
        assertNull(invocation.getFieldValue("invocation"));
        assertSame(parameter, invocation.getParam());
        assertTrue(invocation.getRequiredFields().isEmpty());
        assertFalse(registry.createSdkType(invocation) instanceof com.microsoft.azure.functions.sdktype.CachableSdkType);
    }

    private void fixture(String ignored) { }

    private static SdkInvocationContext context() {
        return new SdkInvocationContext() {
            public String getInvocationId() { return "invocation"; }
            public List<ModelBindingData> getModelBindingData(String name) { return Collections.emptyList(); }
            public URI getHostEndpoint() { return null; }
            public boolean isActive() { return true; }
            public void registerCloseable(AutoCloseable resource) { }
        };
    }
}
