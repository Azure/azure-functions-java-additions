package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.sdktype.CachableSdkType;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.blob.BlobClientCacheKey;
import com.microsoft.azure.functions.sdktype.blob.BlobClientDescriptor;
import com.microsoft.azure.functions.sdktype.blob.BlobClientMetaData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BlobClientDescriptorTest {

    private static final String FQCN = "com.azure.storage.blob.BlobClient";

    static class Functions {
        public void blobFunction(com.azure.storage.blob.BlobClient client) {}
    }

    private static Parameter blobParameter() throws NoSuchMethodException {
        final Method m = Functions.class.getMethod("blobFunction", com.azure.storage.blob.BlobClient.class);
        return m.getParameters()[0];
    }

    private static Map<String, String> raw(String container, String blob, String connection) {
        final Map<String, String> values = new HashMap<>();
        values.put("ContainerName", container);
        values.put("BlobName", blob);
        values.put("Connection", connection);
        return values;
    }

    @Test
    void bindReturnsMetaDataWithTheGivenValues() throws Exception {
        final Parameter param = blobParameter();
        final SdkTypeDescriptor descriptor = new BlobClientDescriptor(FQCN, param);

        final SdkTypeMetaData bound = descriptor.bind(raw("container-a", "blob-a", "CONN_A"));

        assertTrue(bound instanceof BlobClientMetaData);
        final BlobClientMetaData meta = (BlobClientMetaData) bound;
        assertEquals("container-a", meta.getContainerName());
        assertEquals("blob-a", meta.getBlobName());
        assertEquals("CONN_A", meta.getConnectionEnvVar());
        assertEquals(FQCN, meta.getFqcn());
        assertSame(param, meta.getParam());
    }

    @Test
    void bindReturnsANewObjectEachTime() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobClientDescriptor(FQCN, blobParameter());

        final SdkTypeMetaData first = descriptor.bind(raw("c1", "b1", "CONN1"));
        final SdkTypeMetaData second = descriptor.bind(raw("c2", "b2", "CONN2"));
        final SdkTypeMetaData sameValuesAgain = descriptor.bind(raw("c1", "b1", "CONN1"));

        assertNotSame(first, second);
        assertNotSame(first, sameValuesAgain);

        final BlobClientMetaData firstTyped = (BlobClientMetaData) first;
        final BlobClientMetaData secondTyped = (BlobClientMetaData) second;
        assertEquals("c1", firstTyped.getContainerName());
        assertEquals("b1", firstTyped.getBlobName());
        assertEquals("CONN1", firstTyped.getConnectionEnvVar());
        assertEquals("c2", secondTyped.getContainerName());
        assertEquals("b2", secondTyped.getBlobName());
        assertEquals("CONN2", secondTyped.getConnectionEnvVar());
    }

    @Test
    void bindDoesNotChangeTheDescriptor() throws Exception {
        final Parameter param = blobParameter();
        final BlobClientDescriptor descriptor = new BlobClientDescriptor(FQCN, param);
        final List<String> requiredBefore = new ArrayList<>(descriptor.getRequiredFields());

        descriptor.bind(raw("c1", "b1", "CONN1"));
        descriptor.bind(raw("c2", "b2", "CONN2"));

        assertEquals(FQCN, descriptor.getFqcn());
        assertSame(param, descriptor.getParam());
        assertEquals(requiredBefore, new ArrayList<>(descriptor.getRequiredFields()));
        for (Field field : BlobClientDescriptor.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()), "Descriptor field must be final: " + field.getName());
            }
        }
    }

    @Test
    void bindCopiesTheRawValues() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobClientDescriptor(FQCN, blobParameter());
        final Map<String, String> values = raw("c1", "b1", "CONN1");

        final BlobClientMetaData bound = (BlobClientMetaData) descriptor.bind(values);
        values.put("ContainerName", "changed");
        values.put("BlobName", "changed");
        values.put("Connection", "changed");

        assertEquals("c1", bound.getContainerName());
        assertEquals("b1", bound.getBlobName());
        assertEquals("CONN1", bound.getConnectionEnvVar());
    }

    @Test
    void bindRejectsMissingOrEmptyValues() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobClientDescriptor(FQCN, blobParameter());

        assertEquals("containerName is required",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw(null, "b", "C"))).getMessage());
        assertEquals("containerName is required",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("", "b", "C"))).getMessage());
        assertEquals("blobName is required",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("c", null, "C"))).getMessage());
        assertEquals("blobName is required",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("c", "", "C"))).getMessage());
        assertEquals("connectionEnvVar is required",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("c", "b", null))).getMessage());
        assertEquals("connectionEnvVar is required",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("c", "b", ""))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> descriptor.bind(new HashMap<String, String>()));
        assertThrows(IllegalArgumentException.class, () -> descriptor.bind(null));
    }

    @Test
    void requiredFieldsAreTheExpectedNamesAndUnmodifiable() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobClientDescriptor(FQCN, blobParameter());

        assertEquals(new java.util.HashSet<>(Arrays.asList("ContainerName", "BlobName", "Connection")),
                descriptor.getRequiredFields());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getRequiredFields().add("Extra"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getRequiredFields().remove("BlobName"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getRequiredFields().clear());
    }

    @Test
    void metaDataIsImmutable() {
        for (Method method : BlobClientMetaData.class.getMethods()) {
            assertFalse(method.getName().startsWith("set"),
                    "Metadata must not expose a setter: " + method.getName());
        }
        assertTrue(Modifier.isFinal(BlobClientMetaData.class.getModifiers()));
        for (Field field : BlobClientMetaData.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()), "Metadata field must be final: " + field.getName());
            }
        }
    }

    @Test
    void cacheKeyAndBuiltTypeComeFromTheBoundValues() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobClientDescriptor(FQCN, blobParameter());
        final SdkTypeRegistry registry = new SdkTypeRegistry();

        final SdkTypeMetaData first = descriptor.bind(raw("c1", "b1", "CONN1"));
        final SdkTypeMetaData second = descriptor.bind(raw("c2", "b2", "CONN2"));
        final SdkType<?> firstType = registry.createSdkType(first);
        final SdkType<?> secondType = registry.createSdkType(second);

        assertSame(first, firstType.getMetaData());
        assertSame(second, secondType.getMetaData());
        final CacheKey firstKey = ((CachableSdkType<?>) firstType).buildCacheKey();
        final CacheKey secondKey = ((CachableSdkType<?>) secondType).buildCacheKey();
        assertEquals(new BlobClientCacheKey((BlobClientMetaData) first), firstKey);
        assertEquals(new BlobClientCacheKey((BlobClientMetaData) second), secondKey);
        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void concurrentBindsFromOneSharedDescriptorSeeOnlyTheirOwnValues() throws Exception {
        final int threads = 32;
        final int iterations = 200;
        final SdkTypeDescriptor shared = new BlobClientDescriptor(FQCN, blobParameter());
        final SdkTypeRegistry registry = new SdkTypeRegistry();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        start.await();
                        int mismatches = 0;
                        for (int i = 0; i < iterations; i++) {
                            final String container = "container-" + id + "-" + i;
                            final String blob = "blob-" + id + "-" + i;
                            final String connection = "CONN_" + id + "_" + i;

                            final SdkTypeMetaData bound = shared.bind(raw(container, blob, connection));
                            final SdkType<?> sdkType = registry.createSdkType(bound);
                            final CacheKey key = ((CachableSdkType<?>) sdkType).buildCacheKey();
                            final BlobClientMetaData seen = (BlobClientMetaData) sdkType.getMetaData();

                            final BlobClientCacheKey expectedKey = new BlobClientCacheKey(
                                    new BlobClientMetaData(FQCN, shared.getParam(), container, blob, connection));
                            if (!container.equals(seen.getContainerName())
                                    || !blob.equals(seen.getBlobName())
                                    || !connection.equals(seen.getConnectionEnvVar())
                                    || !expectedKey.equals(key)) {
                                mismatches++;
                            }
                        }
                        return mismatches;
                    }
                }));
            }
            start.countDown();
            int totalMismatches = 0;
            for (Future<Integer> future : futures) {
                totalMismatches += future.get(60, TimeUnit.SECONDS);
            }
            assertEquals(0, totalMismatches);
        } finally {
            pool.shutdownNow();
        }
    }
}
