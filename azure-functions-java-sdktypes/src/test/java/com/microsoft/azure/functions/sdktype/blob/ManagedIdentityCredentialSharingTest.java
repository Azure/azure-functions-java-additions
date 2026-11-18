/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;
import com.microsoft.azure.functions.sdktype.exceptions.SdkHydrationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how the hydrators share the managed identity credential through the cache of the worker.
 */
class ManagedIdentityCredentialSharingTest {
    private static final String CONN = "SHARED_RES_CONN";
    private static final BlobClientMetaData META = blobMeta("blob");

    /** Namespace of the middleware of the worker, which caches the built clients. */
    private static final class WorkerNamespace {
    }

    private static final class Builds {
        final AtomicInteger count = new AtomicInteger();
        final List<String> clientIds = Collections.synchronizedList(new ArrayList<String>());
        volatile Exception failure;
    }

    private static final class Client {
        final String endpoint;
        final Object credential;

        Client(String endpoint, Object credential) {
            this.endpoint = endpoint;
            this.credential = credential;
        }
    }

    private static final class RecordingHydrator extends BaseBlobHydrator<BlobClientMetaData> {
        private final Builds builds;

        RecordingHydrator(ObjectCache<CacheKey, Object> cache, Builds builds) {
            super(cache);
            this.builds = builds;
        }

        @Override
        protected Object buildManagedIdentityCredential(String clientId) throws Exception {
            builds.count.incrementAndGet();
            builds.clientIds.add(clientId);
            if (builds.failure != null) {
                throw builds.failure;
            }
            return new Object();
        }

        @Override
        protected Object buildWithManagedIdentity(BlobClientMetaData metaData, String endpoint, Object credential) {
            return new Client(endpoint, credential);
        }

        @Override
        protected Object buildWithConnectionString(BlobClientMetaData metaData, String connectionString) {
            return new Client(connectionString, null);
        }
    }

    private static BlobClientMetaData blobMeta(String blobName) {
        return new BlobClientMetaData("com.azure.storage.blob.BlobClient", null, "container", blobName, CONN);
    }

    private static Map<String, String> managedIdentityEnv(String clientId) {
        final Map<String, String> env = new HashMap<>();
        env.put(CONN + "__accountName", "acct");
        if (clientId != null) {
            env.put(CONN + "__clientId", clientId);
        }
        return env;
    }

    private static Client hydrate(BaseBlobHydrator<BlobClientMetaData> hydrator, Map<String, String> env)
            throws Exception {
        return (Client) hydrator.createInstance(META, CONN, env::get);
    }

    @Test
    void sharesOneCredentialAcrossHydrators() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        final Client first = hydrate(new RecordingHydrator(cache, builds), env);
        final Client second = hydrate(new RecordingHydrator(cache, builds), env);
        final Client third = hydrate(new RecordingHydrator(cache, builds), env);

        assertEquals(1, builds.count.get());
        assertSame(first.credential, second.credential);
        assertSame(first.credential, third.credential);
        assertEquals("https://acct.blob.core.windows.net", first.endpoint);
    }

    @Test
    void separatesCredentialsByClientId() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final RecordingHydrator hydrator = new RecordingHydrator(cache, builds);

        final Client system = hydrate(hydrator, managedIdentityEnv(null));
        final Client systemEmpty = hydrate(hydrator, managedIdentityEnv(""));
        final Client a1 = hydrate(hydrator, managedIdentityEnv("client-a"));
        final Client a2 = hydrate(hydrator, managedIdentityEnv("client-a"));
        final Client b = hydrate(hydrator, managedIdentityEnv("client-b"));

        assertEquals(Arrays.asList(null, "client-a", "client-b"), builds.clientIds);
        assertSame(system.credential, systemEmpty.credential);
        assertSame(a1.credential, a2.credential);
        assertNotSame(system.credential, a1.credential);
        assertNotSame(a1.credential, b.credential);
    }

    @Test
    void separatesCredentialsByConnectionSetting() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final RecordingHydrator hydrator = new RecordingHydrator(cache, builds);
        final Map<String, String> env = new HashMap<>();
        env.put("FIRST__accountName", "acct");
        env.put("FIRST__clientId", "same-client");
        env.put("SECOND__accountName", "acct");
        env.put("SECOND__clientId", "same-client");

        final Client first = (Client) hydrator.createInstance(META, "FIRST", env::get);
        final Client second = (Client) hydrator.createInstance(META, "SECOND", env::get);

        assertEquals(2, builds.count.get());
        assertNotSame(first.credential, second.credential);
    }

    @Test
    void connectionStringPathNeverTouchesTheCache() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = new HashMap<>();
        env.put(CONN, "DefaultEndpointsProtocol=https;AccountName=acct;AccountKey=fake-key-for-tests");

        final Client client = hydrate(new RecordingHydrator(cache, builds), env);

        assertNull(client.credential);
        assertEquals(0, cache.calls());
        assertEquals(0, builds.count.get());
    }

    @Test
    void missingEndpointBuildsNoCredentialAndLeavesNoEntry() {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();

        assertThrows(SdkHydrationException.class,
                () -> hydrate(new RecordingHydrator(cache, builds), new HashMap<String, String>()));

        assertEquals(0, builds.count.get());
        assertEquals(0, cache.calls());
        assertEquals(0, cache.size());
    }

    @Test
    void failedBuildIsNotCached() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        builds.failure = new ClassNotFoundException("com.azure.identity.DefaultAzureCredentialBuilder");
        assertThrows(ClassNotFoundException.class, () -> hydrate(new RecordingHydrator(cache, builds), env));
        assertEquals(0, cache.size());

        builds.failure = null;
        final Client recovered = hydrate(new RecordingHydrator(cache, builds), env);
        final Client again = hydrate(new RecordingHydrator(cache, builds), env);

        assertEquals(2, builds.count.get());
        assertNotNull(recovered.credential);
        assertSame(recovered.credential, again.credential);
    }

    @Test
    void theOriginalExceptionSurfacesWithAndWithoutCache() {
        final Exception[] failures = {
            new ClassNotFoundException("missing"),
            new InvocationTargetException(new IllegalStateException("boom")),
            new IllegalStateException("runtime failure")
        };
        final List<ObjectCache<CacheKey, Object>> caches = new ArrayList<>();
        caches.add(new FakeObjectCache(true));
        caches.add(new FakeObjectCache(false));
        caches.add(null);

        for (ObjectCache<CacheKey, Object> cache : caches) {
            for (Exception failure : failures) {
                final Builds builds = new Builds();
                builds.failure = failure;
                final Exception thrown = assertThrows(Exception.class,
                        () -> hydrate(new RecordingHydrator(cache, builds), managedIdentityEnv(null)));
                assertSame(failure, thrown);
            }
        }
    }

    @Test
    void withoutCacheEveryHydrationBuildsItsOwnCredential() throws Exception {
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        final Client first = hydrate(new RecordingHydrator(null, builds), env);
        final Client second = hydrate(new RecordingHydrator(null, builds), env);

        assertEquals(2, builds.count.get());
        assertNotSame(first.credential, second.credential);
    }

    @Test
    void concurrentFirstUseBuildsTheCredentialOnce() throws Exception {
        final int threads = 16;
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<Client>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return hydrate(new RecordingHydrator(cache, builds), env);
                }));
            }
            start.countDown();
            final Object credential = results.get(0).get(60, TimeUnit.SECONDS).credential;
            for (Future<Client> result : results) {
                assertSame(credential, result.get(60, TimeUnit.SECONDS).credential);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, builds.count.get());
    }

    @Test
    void worksInsideTheCacheLookupOfTheWorkerClient() {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        for (BlobClientMetaData meta : Arrays.asList(blobMeta("one"), blobMeta("two"), blobMeta("one"))) {
            final Object client = cache.computeIfAbsent(WorkerNamespace.class, new BlobClientCacheKey(meta), () -> {
                try {
                    return new RecordingHydrator(cache, builds).createInstance(meta, CONN, env::get);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertNotNull(client);
        }

        assertEquals(1, builds.count.get());
        assertEquals(3, cache.size());
        assertTrue(cache.namespaces().contains(WorkerNamespace.class));
        assertTrue(cache.namespaces().contains(ManagedIdentityCredentialCacheKey.class));
    }

    @Test
    void keyIsEqualByConnectionSettingAndNormalizedClientId() {
        final ManagedIdentityCredentialCacheKey system = new ManagedIdentityCredentialCacheKey("A", null);

        assertEquals(system, new ManagedIdentityCredentialCacheKey("A", ""));
        assertEquals(system.hashCode(), new ManagedIdentityCredentialCacheKey("A", "").hashCode());
        assertEquals(new ManagedIdentityCredentialCacheKey("A", "x"), new ManagedIdentityCredentialCacheKey("A", "x"));
        assertNotEquals(new ManagedIdentityCredentialCacheKey("A", "x"), new ManagedIdentityCredentialCacheKey("A", "y"));
        assertNotEquals(new ManagedIdentityCredentialCacheKey("A", "x"), new ManagedIdentityCredentialCacheKey("B", "x"));
        assertNotEquals(system, new ManagedIdentityCredentialCacheKey("A", "x"));
    }
}
