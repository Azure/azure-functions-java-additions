/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests the real hydrators and the way the registry and the factories hand the cache to them.
 * The hydrators load the azure-identity and azure-storage-blob builders by name through the context
 * class loader, as in the worker, so the test serves small fake builders through its own loader.
 */
class BlobHydratorCredentialSharingTest {
    private static final String CONN = "SHARED_RES_REAL_CONN";
    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";

    public interface FakeCredential {
    }

    public static final class FakeClient {
        final String endpoint;
        final String containerName;
        final String blobName;
        final Object credential;

        FakeClient(String endpoint, String containerName, String blobName, Object credential) {
            this.endpoint = endpoint;
            this.containerName = containerName;
            this.blobName = blobName;
            this.credential = credential;
        }
    }

    public static final class FakeIdentityBuilder {
        static final AtomicInteger BUILDS = new AtomicInteger();
        static final List<String> CLIENT_IDS = new CopyOnWriteArrayList<>();

        private String clientId;

        public FakeIdentityBuilder managedIdentityClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public FakeCredential build() {
            BUILDS.incrementAndGet();
            CLIENT_IDS.add(String.valueOf(clientId));
            return new FakeCredential() {
            };
        }
    }

    public static final class FakeBlobClientBuilder {
        private FakeCredential credential;
        private String endpoint;
        private String containerName;
        private String blobName;

        public FakeBlobClientBuilder credential(FakeCredential credential) {
            this.credential = credential;
            return this;
        }

        public FakeBlobClientBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public FakeBlobClientBuilder containerName(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public FakeBlobClientBuilder blobName(String blobName) {
            this.blobName = blobName;
            return this;
        }

        public FakeClient buildClient() {
            return new FakeClient(endpoint, containerName, blobName, credential);
        }
    }

    public static final class FakeContainerClientBuilder {
        private FakeCredential credential;
        private String endpoint;
        private String containerName;

        public FakeContainerClientBuilder credential(FakeCredential credential) {
            this.credential = credential;
            return this;
        }

        public FakeContainerClientBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public FakeContainerClientBuilder containerName(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public FakeClient buildClient() {
            return new FakeClient(endpoint, containerName, null, credential);
        }
    }

    /** Serves the fake builders under the names that the hydrators load. */
    private static final class FakeSdkLoader extends ClassLoader {
        private final Map<String, Class<?>> fakes = new HashMap<>();

        FakeSdkLoader() {
            super(BlobHydratorCredentialSharingTest.class.getClassLoader());
            fakes.put("com.azure.identity.DefaultAzureCredentialBuilder", FakeIdentityBuilder.class);
            fakes.put("com.azure.core.credential.TokenCredential", FakeCredential.class);
            fakes.put("com.azure.storage.blob.BlobClientBuilder", FakeBlobClientBuilder.class);
            fakes.put("com.azure.storage.blob.BlobContainerClientBuilder", FakeContainerClientBuilder.class);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            final Class<?> fake = fakes.get(name);
            return fake != null ? fake : super.loadClass(name);
        }
    }

    private ClassLoader originalLoader;

    private static BlobClientMetaData blobMeta(String blobName) {
        return new BlobClientMetaData("com.azure.storage.blob.BlobClient", null, "container", blobName, CONN);
    }

    private static BlobContainerMetaData containerMeta() {
        return new BlobContainerMetaData("com.azure.storage.blob.BlobContainerClient", null, "container", CONN);
    }

    private static Map<String, String> env() {
        final Map<String, String> env = new HashMap<>();
        env.put(CONN + "__accountName", "acct");
        env.put(CONN + "__clientId", CLIENT_ID);
        return env;
    }

    @BeforeEach
    void useFakeSdkLoader() {
        FakeIdentityBuilder.BUILDS.set(0);
        FakeIdentityBuilder.CLIENT_IDS.clear();
        originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new FakeSdkLoader());
    }

    @AfterEach
    void restoreLoader() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void blobAndContainerClientsShareOneCredential() throws Exception {
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Map<String, String> env = env();

        final FakeClient one = (FakeClient) new BlobClientHydrator(cache).createInstance(blobMeta("one"), CONN, env::get);
        final FakeClient two = (FakeClient) new BlobClientHydrator(cache).createInstance(blobMeta("two"), CONN, env::get);
        final FakeClient container = (FakeClient) new BlobContainerHydrator(cache)
                .createInstance(containerMeta(), CONN, env::get);

        assertEquals(1, FakeIdentityBuilder.BUILDS.get());
        assertEquals(Collections.singletonList(CLIENT_ID), FakeIdentityBuilder.CLIENT_IDS);
        assertSame(one.credential, two.credential);
        assertSame(one.credential, container.credential);
        assertEquals("https://acct.blob.core.windows.net", one.endpoint);
        assertEquals("one", one.blobName);
        assertEquals("two", two.blobName);
        assertEquals("container", container.containerName);
    }

    @Test
    void withoutCacheEveryClientBuildsItsOwnCredential() throws Exception {
        final Map<String, String> env = env();

        final FakeClient one = (FakeClient) new BlobClientHydrator().createInstance(blobMeta("one"), CONN, env::get);
        final FakeClient container = (FakeClient) new BlobContainerHydrator(null)
                .createInstance(containerMeta(), CONN, env::get);

        assertEquals(2, FakeIdentityBuilder.BUILDS.get());
        assertNotSame(one.credential, container.credential);
    }

    @Test
    void registryHandsTheCacheToTheHydratorsOfBothBlobTypes() throws Exception {
        final SdkTypeRegistry registry = new SdkTypeRegistry();
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Map<String, String> env = env();

        for (String blobName : new String[] {"one", "two", "three"}) {
            final BlobClientMetaData meta = blobMeta(blobName);
            final BlobClientHydrator hydrator = (BlobClientHydrator) registry.createSdkType(meta, cache).getHydrator();
            hydrator.createInstance(meta, CONN, env::get);
        }
        final BlobContainerMetaData containerMeta = containerMeta();
        final BlobContainerHydrator containerHydrator =
                (BlobContainerHydrator) registry.createSdkType(containerMeta, cache).getHydrator();
        containerHydrator.createInstance(containerMeta, CONN, env::get);

        assertEquals(1, FakeIdentityBuilder.BUILDS.get());
        assertEquals(1, cache.size());
        assertEquals(Collections.<Class<?>>singleton(SdkTypeRegistry.class), cache.namespaces());
    }

    @Test
    void registryGivesTheHydratorsNoAccessToTheEntriesOfOtherNamespaces() throws Exception {
        final SdkTypeRegistry registry = new SdkTypeRegistry();
        final FakeObjectCache cache = new FakeObjectCache(true);
        final Object foreign = new Object();
        // the namespace that a hydrator would pick itself when it was handed the worker's cache
        cache.computeIfAbsent(ManagedIdentityCredentialCacheKey.class,
                new ManagedIdentityCredentialCacheKey(CONN, CLIENT_ID), () -> foreign);
        final Map<String, String> env = env();

        final BlobClientMetaData meta = blobMeta("one");
        final BlobClientHydrator hydrator = (BlobClientHydrator) registry.createSdkType(meta, cache).getHydrator();
        final FakeClient client = (FakeClient) hydrator.createInstance(meta, CONN, env::get);

        assertEquals(1, FakeIdentityBuilder.BUILDS.get());
        assertNotSame(foreign, client.credential);
        assertEquals(2, cache.size());
    }

    @Test
    void registryAcceptsANullCache() throws Exception {
        final SdkTypeRegistry registry = new SdkTypeRegistry();
        final Map<String, String> env = env();

        for (String blobName : new String[] {"one", "two"}) {
            final BlobClientMetaData meta = blobMeta(blobName);
            final BlobClientHydrator hydrator = (BlobClientHydrator) registry.createSdkType(meta, null).getHydrator();
            hydrator.createInstance(meta, CONN, env::get);
        }

        assertEquals(2, FakeIdentityBuilder.BUILDS.get());
    }

    @Test
    void registryWithoutCacheKeepsBuildingACredentialPerClient() throws Exception {
        final SdkTypeRegistry registry = new SdkTypeRegistry();
        final Map<String, String> env = env();

        for (String blobName : new String[] {"one", "two"}) {
            final BlobClientMetaData meta = blobMeta(blobName);
            final BlobClientHydrator hydrator = (BlobClientHydrator) registry.createSdkType(meta).getHydrator();
            hydrator.createInstance(meta, CONN, env::get);
        }

        assertEquals(2, FakeIdentityBuilder.BUILDS.get());
    }

    @Test
    void aFactoryWithoutCacheSupportKeepsWorking() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final SdkTypeFactory legacy = new SdkTypeFactory() {
            @Override
            public SdkTypeDescriptor createDescriptor(String fqcn, Parameter param) {
                return null;
            }

            @Override
            public SdkType<?> createSdkType(SdkTypeMetaData metaData) {
                calls.incrementAndGet();
                return null;
            }
        };

        assertNull(legacy.createSdkType(blobMeta("one"), new FakeObjectCache(true)));
        assertEquals(1, calls.get());
    }
}
