/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions.cache;

import java.util.function.Supplier;

/**
 * A concurrency-safe object cache that uses a top-level namespace (Class)
 * plus a typed key (K extends CacheKey) to store values (V).
 *
 * <p>By default, only {@link #computeIfAbsent} is abstract and must be implemented.
 * The {@link #get} and {@link #remove} methods are default methods that throw
 * {@link UnsupportedOperationException} unless overridden.</p>
 *
 * @param <K> Type of the cache key, which must implement {@link CacheKey}.
 * @param <V> Type of the stored objects/values.
 */
public interface ObjectCache<K extends CacheKey, V> {

    /**
     * Retrieves or creates a value for (namespace, key).
     * If already cached, returns the existing value.
     * Otherwise, calls {@code creator}, stores that result, and returns it.
     *
     * @param namespace A "namespace" class (often the middleware's class).
     * @param key       A {@link CacheKey} object (must define equals/hashCode).
     * @param creator   A supplier that builds the value if absent.
     * @return The existing or newly created object.
     */
    V computeIfAbsent(Class<?> namespace, K key, Supplier<V> creator);

    /**
     * Retrieves the cached value for (namespace, key) if present,
     * or null if not found.
     * <p>
     * By default, throws {@code UnsupportedOperationException}.
     * Override if your implementation supports direct get.
     *
     * @param namespace The "namespace" class.
     * @param key       The cache key.
     * @return The cached value or null.
     */
    default V get(Class<?> namespace, K key) {
        throw new UnsupportedOperationException("get(...) not implemented by default.");
    }

    /**
     * Removes the cached value for (namespace, key) if present.
     * <p>
     * By default, throws {@code UnsupportedOperationException}.
     * Override if your implementation supports removal.
     *
     * @param namespace The "namespace" class.
     * @param key       The cache key.
     * @return The removed value, or null if it was not in the cache.
     */
    default V remove(Class<?> namespace, K key) {
        throw new UnsupportedOperationException("remove(...) not implemented by default.");
    }
}
