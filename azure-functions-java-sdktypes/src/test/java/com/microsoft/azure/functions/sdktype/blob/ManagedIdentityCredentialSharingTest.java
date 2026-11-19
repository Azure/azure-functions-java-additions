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
 * Tests how the hydrators cache the managed identity credential and, when they have an instance key, their clients,
 * through the cache of the worker.
 */
class ManagedIdentityCredentialSharingTest {
    private static final String CONN = "SHARED_RES_CONN";
    private static final BlobClientMetaData META = blobMeta("blob");

    private static final class Builds {
        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger clients = new AtomicInteger();
        final List<String> clientIds = Collections.synchronizedList(new ArrayList<String>());
        volatile Exception failure;
        volatile Exception clientFailure;
    }

    private static final class Client {
        final String blobName;
        final String endpoint;
        final Object credential;

        Client(String blobName, String endpoint, Object credential) {
            this.blobName = blobName;
            this.endpoint = endpoint;
            this.credential = credential;
        }
    }

    private static final class RecordingHydrator extends BaseBlobHydrator<BlobClientMetaData> {
        private final Builds builds;
        private final boolean cacheClients;

        RecordingHydrator(ObjectCache<CacheKey, Object> cache, Builds builds) {
            this(cache, builds, false);
        }

        RecordingHydrator(ObjectCache<CacheKey, Object> cache, Builds builds, boolean cacheClients) {
            super(cache);
            this.builds = builds;
            this.cacheClients = cacheClients;
        }

        @Override
        protected CacheKey instanceKey(BlobClientMetaData metaData) {
            return cacheClients ? new BlobClientCacheKey(metaData) : null;
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
        protected Object buildWithManagedIdentity(BlobClientMetaData metaData, String endpoint, Object credential)
                throws Exception {
            return newClient(metaData, endpoint, credential);
        }

        @Override
        protected Object buildWithConnectionString(BlobClientMetaData metaData, String connectionString)
                throws Exception {
            return newClient(metaData, connectionString, null);
        }

        private Client newClient(BlobClientMetaData metaData, String endpoint, Object credential) throws Exception {
            builds.clients.incrementAndGet();
            if (builds.clientFailure != null) {
                throw builds.clientFailure;
            }
            return new Client(metaData.getBlobName(), endpoint, credential);
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
        // without an instance key only the credential is cached
        assertEquals(1, cache.size());
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
    void connectionStringPathBuildsNoCredential() throws Exception {
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
    void connectionStringClientIsCachedUnderItsInstanceKey() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = new HashMap<>();
        env.put(CONN, "DefaultEndpointsProtocol=https;AccountName=acct;AccountKey=fake-key-for-tests");

        final Client first = hydrate(new RecordingHydrator(cache, builds, true), env);
        final Client second = hydrate(new RecordingHydrator(cache, builds, true), env);

        assertSame(first, second);
        assertEquals(1, builds.clients.get());
        assertEquals(0, builds.count.get());
        assertEquals(1, cache.size());
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
    void aFailedClientBuildIsNotCachedAndKeepsTheCredential() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        builds.clientFailure = new IllegalStateException("client build failed");
        final Exception thrown = assertThrows(IllegalStateException.class,
                () -> hydrate(new RecordingHydrator(cache, builds, true), env));
        assertSame(builds.clientFailure, thrown);
        // the credential was built and cached, the failed client was not
        assertEquals(1, cache.size());

        builds.clientFailure = null;
        final Client recovered = hydrate(new RecordingHydrator(cache, builds, true), env);
        final Client again = hydrate(new RecordingHydrator(cache, builds, true), env);

        assertSame(recovered, again);
        assertEquals(1, builds.count.get());
        assertEquals(2, builds.clients.get());
        assertEquals(2, cache.size());
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

        // the credential is built inside the cache lookup of the client when the hydrator caches clients
        for (ObjectCache<CacheKey, Object> cache : caches) {
            for (boolean cacheClients : new boolean[] {false, true}) {
                for (Exception failure : failures) {
                    final Builds builds = new Builds();
                    builds.failure = failure;
                    final Exception thrown = assertThrows(Exception.class,
                            () -> hydrate(new RecordingHydrator(cache, builds, cacheClients), managedIdentityEnv(null)));
                    assertSame(failure, thrown);
                }
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
    void withoutCacheNothingIsCachedEvenWithAnInstanceKey() throws Exception {
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        final Client first = hydrate(new RecordingHydrator(null, builds, true), env);
        final Client second = hydrate(new RecordingHydrator(null, builds, true), env);

        assertNotSame(first, second);
        assertEquals(2, builds.clients.get());
        assertEquals(2, builds.count.get());
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
    void theHydratorCachesTheClientNextToTheCredential() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Builds builds = new Builds();
        final Map<String, String> env = managedIdentityEnv(null);

        final Client one = (Client) new RecordingHydrator(cache, builds, true)
                .createInstance(blobMeta("one"), CONN, env::get);
        final Client two = (Client) new RecordingHydrator(cache, builds, true)
                .createInstance(blobMeta("two"), CONN, env::get);
        final Client oneAgain = (Client) new RecordingHydrator(cache, builds, true)
                .createInstance(blobMeta("one"), CONN, env::get);

        assertSame(one, oneAgain);
        assertNotSame(one, two);
        assertSame(one.credential, two.credential);
        assertEquals("one", one.blobName);
        assertEquals("two", two.blobName);
        assertEquals(1, builds.count.get());
        assertEquals(2, builds.clients.get());
        assertEquals(3, cache.size());
        assertTrue(cache.namespaces().contains(BlobClientCacheKey.class));
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
