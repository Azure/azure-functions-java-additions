package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.blob.BlobContainerCacheKey;
import com.microsoft.azure.functions.sdktype.blob.BlobContainerDescriptor;
import com.microsoft.azure.functions.sdktype.blob.BlobContainerMetaData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BlobContainerDescriptorTest {

    private static final String FQCN = "com.azure.storage.blob.BlobContainerClient";

    static class Functions {
        public void containerFunction(com.azure.storage.blob.BlobContainerClient client) {}
    }

    private static Parameter containerParameter() throws NoSuchMethodException {
        final Method m = Functions.class.getMethod("containerFunction",
                com.azure.storage.blob.BlobContainerClient.class);
        return m.getParameters()[0];
    }

    private static Map<String, String> raw(String container, String connection) {
        final Map<String, String> values = new HashMap<>();
        values.put("ContainerName", container);
        values.put("Connection", connection);
        return values;
    }

    @Test
    void bindReturnsMetaDataWithTheGivenValues() throws Exception {
        final Parameter param = containerParameter();
        final SdkTypeDescriptor descriptor = new BlobContainerDescriptor(FQCN, param);

        final SdkTypeMetaData bound = descriptor.bind(raw("container-a", "CONN_A"));

        assertTrue(bound instanceof BlobContainerMetaData);
        final BlobContainerMetaData meta = (BlobContainerMetaData) bound;
        assertEquals("container-a", meta.getContainerName());
        assertEquals("CONN_A", meta.getConnectionEnvVar());
        assertEquals(FQCN, meta.getFqcn());
        assertSame(param, meta.getParam());
    }

    @Test
    void bindReturnsANewObjectEachTime() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobContainerDescriptor(FQCN, containerParameter());

        final SdkTypeMetaData first = descriptor.bind(raw("c1", "CONN1"));
        final SdkTypeMetaData second = descriptor.bind(raw("c2", "CONN2"));
        final SdkTypeMetaData sameValuesAgain = descriptor.bind(raw("c1", "CONN1"));

        assertNotSame(first, second);
        assertNotSame(first, sameValuesAgain);

        final BlobContainerMetaData firstTyped = (BlobContainerMetaData) first;
        final BlobContainerMetaData secondTyped = (BlobContainerMetaData) second;
        assertEquals("c1", firstTyped.getContainerName());
        assertEquals("CONN1", firstTyped.getConnectionEnvVar());
        assertEquals("c2", secondTyped.getContainerName());
        assertEquals("CONN2", secondTyped.getConnectionEnvVar());
    }

    @Test
    void bindDoesNotChangeTheDescriptor() throws Exception {
        final Parameter param = containerParameter();
        final BlobContainerDescriptor descriptor = new BlobContainerDescriptor(FQCN, param);
        final List<String> requiredBefore = new ArrayList<>(descriptor.getRequiredFields());

        descriptor.bind(raw("c1", "CONN1"));
        descriptor.bind(raw("c2", "CONN2"));

        assertEquals(FQCN, descriptor.getFqcn());
        assertSame(param, descriptor.getParam());
        assertEquals(requiredBefore, new ArrayList<>(descriptor.getRequiredFields()));
        for (Field field : BlobContainerDescriptor.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()), "Descriptor field must be final: " + field.getName());
            }
        }
    }

    @Test
    void bindCopiesTheRawValues() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobContainerDescriptor(FQCN, containerParameter());
        final Map<String, String> values = raw("c1", "CONN1");

        final BlobContainerMetaData bound = (BlobContainerMetaData) descriptor.bind(values);
        values.put("ContainerName", "changed");
        values.put("Connection", "changed");

        assertEquals("c1", bound.getContainerName());
        assertEquals("CONN1", bound.getConnectionEnvVar());
    }

    @Test
    void bindRejectsMissingOrEmptyValues() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobContainerDescriptor(FQCN, containerParameter());

        assertEquals("containerName is required for BlobContainerClient.",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw(null, "C"))).getMessage());
        assertEquals("containerName is required for BlobContainerClient.",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("", "C"))).getMessage());
        assertEquals("connectionEnvVar is required for BlobContainerClient.",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("c", null))).getMessage());
        assertEquals("connectionEnvVar is required for BlobContainerClient.",
                assertThrows(IllegalArgumentException.class, () -> descriptor.bind(raw("c", ""))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> descriptor.bind(new HashMap<String, String>()));
        assertThrows(IllegalArgumentException.class, () -> descriptor.bind(null));
    }

    @Test
    void requiredFieldsAreTheExpectedNamesAndUnmodifiable() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobContainerDescriptor(FQCN, containerParameter());

        assertEquals(new HashSet<>(Arrays.asList("ContainerName", "Connection")), descriptor.getRequiredFields());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getRequiredFields().add("Extra"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getRequiredFields().remove("Connection"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getRequiredFields().clear());
    }

    @Test
    void metaDataIsImmutable() {
        for (Method method : BlobContainerMetaData.class.getMethods()) {
            assertFalse(method.getName().startsWith("set"),
                    "Metadata must not expose a setter: " + method.getName());
        }
        assertTrue(Modifier.isFinal(BlobContainerMetaData.class.getModifiers()));
        for (Field field : BlobContainerMetaData.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()), "Metadata field must be final: " + field.getName());
            }
        }
    }

    @Test
    void instanceKeyAndBuiltTypeComeFromTheBoundValues() throws Exception {
        final SdkTypeDescriptor descriptor = new BlobContainerDescriptor(FQCN, containerParameter());
        final SdkTypeRegistry registry = new SdkTypeRegistry();
        final RecordingObjectCache firstCache = new RecordingObjectCache();
        final RecordingObjectCache secondCache = new RecordingObjectCache();

        final SdkTypeMetaData first = descriptor.bind(raw("c1", "CONN1"));
        final SdkTypeMetaData second = descriptor.bind(raw("c2", "CONN2"));
        final SdkType<?> firstType = registry.createSdkType(first, firstCache);
        final SdkType<?> secondType = registry.createSdkType(second, secondCache);

        assertSame(first, firstType.getMetaData());
        assertSame(second, secondType.getMetaData());
        // the type caches its own instance, so its key shows up in the cache lookup of buildInstance()
        assertSame(RecordingObjectCache.MARKER, firstType.buildInstance());
        assertSame(RecordingObjectCache.MARKER, secondType.buildInstance());
        final CacheKey firstKey = firstCache.key();
        final CacheKey secondKey = secondCache.key();
        assertEquals(new BlobContainerCacheKey((BlobContainerMetaData) first), firstKey);
        assertEquals(new BlobContainerCacheKey((BlobContainerMetaData) second), secondKey);
        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void concurrentBindsFromOneSharedDescriptorSeeOnlyTheirOwnValues() throws Exception {
        final int threads = 32;
        final int iterations = 200;
        final SdkTypeDescriptor shared = new BlobContainerDescriptor(FQCN, containerParameter());
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
                            final String connection = "CONN_" + id + "_" + i;

                            final SdkTypeMetaData bound = shared.bind(raw(container, connection));
                            final RecordingObjectCache cache = new RecordingObjectCache();
                            final SdkType<?> sdkType = registry.createSdkType(bound, cache);
                            sdkType.buildInstance();
                            final CacheKey key = cache.key();
                            final BlobContainerMetaData seen = (BlobContainerMetaData) sdkType.getMetaData();

                            final BlobContainerCacheKey expectedKey = new BlobContainerCacheKey(
                                    new BlobContainerMetaData(FQCN, shared.getParam(), container, connection));
                            if (!container.equals(seen.getContainerName())
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
