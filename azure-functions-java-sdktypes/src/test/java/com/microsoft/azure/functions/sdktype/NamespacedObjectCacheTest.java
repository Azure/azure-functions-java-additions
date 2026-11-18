/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the view that the registry hands to the factories instead of the worker's cache itself.
 */
class NamespacedObjectCacheTest {
    /** Namespace of the middleware of the worker. */
    private static final class WorkerNamespace {
    }

    private static final class BoundNamespace {
    }

    private static final class Key implements CacheKey {
        private final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && Objects.equals(name, ((Key) o).name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    /** Keeps entries per namespace and key, like the worker's cache, and counts the other calls. */
    private static final class RecordingCache implements ObjectCache<CacheKey, Object> {
        final Map<List<Object>, Object> entries = new HashMap<>();
        final List<Class<?>> namespacesUsed = new ArrayList<>();
        int getCalls;
        int removeCalls;

        @Override
        public Object computeIfAbsent(Class<?> namespace, CacheKey key, Supplier<Object> creator) {
            namespacesUsed.add(namespace);
            final List<Object> composite = Arrays.<Object>asList(namespace, key);
            Object value = entries.get(composite);
            if (value == null) {
                value = creator.get();
                entries.put(composite, value);
            }
            return value;
        }

        @Override
        public Object get(Class<?> namespace, CacheKey key) {
            getCalls++;
            return entries.get(Arrays.<Object>asList(namespace, key));
        }

        @Override
        public Object remove(Class<?> namespace, CacheKey key) {
            removeCalls++;
            return entries.remove(Arrays.<Object>asList(namespace, key));
        }

        Set<Class<?>> namespacesOfEntries() {
            final Set<Class<?>> namespaces = new HashSet<>();
            for (List<Object> composite : entries.keySet()) {
                namespaces.add((Class<?>) composite.get(0));
            }
            return namespaces;
        }
    }

    @Test
    void storesEveryEntryInTheBoundNamespaceWhateverNamespaceTheCallerPasses() {
        final RecordingCache cache = new RecordingCache();
        final ObjectCache<CacheKey, Object> view = new NamespacedObjectCache(cache, BoundNamespace.class);

        view.computeIfAbsent(WorkerNamespace.class, new Key("a"), () -> "A");
        view.computeIfAbsent(BoundNamespace.class, new Key("b"), () -> "B");
        view.computeIfAbsent(null, new Key("c"), () -> "C");

        assertEquals(Arrays.<Class<?>>asList(BoundNamespace.class, BoundNamespace.class, BoundNamespace.class),
                cache.namespacesUsed);
        assertEquals(new HashSet<Class<?>>(Arrays.<Class<?>>asList(BoundNamespace.class)), cache.namespacesOfEntries());
        assertEquals(3, cache.entries.size());
    }

    @Test
    void neverReturnsOrChangesTheEntriesOfTheWorker() {
        final RecordingCache cache = new RecordingCache();
        cache.computeIfAbsent(WorkerNamespace.class, new Key("k"), () -> "worker value");
        final ObjectCache<CacheKey, Object> view = new NamespacedObjectCache(cache, BoundNamespace.class);

        final Object own = view.computeIfAbsent(WorkerNamespace.class, new Key("k"), () -> "own value");

        assertEquals("own value", own);
        assertEquals("worker value", cache.entries.get(Arrays.<Object>asList(WorkerNamespace.class, new Key("k"))));
        assertEquals("own value", cache.entries.get(Arrays.<Object>asList(BoundNamespace.class, new Key("k"))));
    }

    @Test
    void doesNotSupportReadingOrRemoving() {
        final RecordingCache cache = new RecordingCache();
        cache.computeIfAbsent(BoundNamespace.class, new Key("k"), () -> "value");
        final ObjectCache<CacheKey, Object> view = new NamespacedObjectCache(cache, BoundNamespace.class);

        assertThrows(UnsupportedOperationException.class, () -> view.get(BoundNamespace.class, new Key("k")));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(BoundNamespace.class, new Key("k")));

        assertEquals(0, cache.getCalls);
        assertEquals(0, cache.removeCalls);
        assertEquals(1, cache.entries.size());
    }

    @Test
    void runsTheCreatorOnceAndReturnsTheSameValue() {
        final RecordingCache cache = new RecordingCache();
        final ObjectCache<CacheKey, Object> view = new NamespacedObjectCache(cache, BoundNamespace.class);
        final AtomicInteger created = new AtomicInteger();

        final Object first = view.computeIfAbsent(BoundNamespace.class, new Key("k"), () -> created.incrementAndGet());
        final Object second = view.computeIfAbsent(BoundNamespace.class, new Key("k"), () -> created.incrementAndGet());

        assertSame(first, second);
        assertEquals(1, created.get());
    }

    @Test
    void passesTheFailureOfTheCreatorThroughAndStoresNothing() {
        final RecordingCache cache = new RecordingCache();
        final ObjectCache<CacheKey, Object> view = new NamespacedObjectCache(cache, BoundNamespace.class);
        final IllegalStateException failure = new IllegalStateException("boom");

        final IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                view.computeIfAbsent(BoundNamespace.class, new Key("k"), () -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertTrue(cache.entries.isEmpty());
    }

    @Test
    void rejectsAMissingCacheOrNamespace() {
        assertThrows(NullPointerException.class, () -> new NamespacedObjectCache(null, BoundNamespace.class));
        assertThrows(NullPointerException.class, () -> new NamespacedObjectCache(new RecordingCache(), null));
    }
}
