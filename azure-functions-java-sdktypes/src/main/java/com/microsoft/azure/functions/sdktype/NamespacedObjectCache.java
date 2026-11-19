/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A view of the worker's object cache that is bound to one namespace.
 *
 * <p>The view only supports {@code computeIfAbsent}, and always in the bound namespace. The namespace that a
 * caller passes is ignored, and {@code get} and {@code remove} keep the default of
 * {@link ObjectCache}, which throws. So a holder of the view cannot reach the entries of the worker or of other
 * libraries, cannot remove anything, and has no say over size, expiry or reference strength, which stay with the
 * worker.</p>
 */
final class NamespacedObjectCache implements ObjectCache<CacheKey, Object> {
    private final ObjectCache<CacheKey, Object> cache;
    private final Class<?> namespace;

    NamespacedObjectCache(ObjectCache<CacheKey, Object> cache, Class<?> namespace) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    @Override
    public Object computeIfAbsent(Class<?> ignoredNamespace, CacheKey key, Supplier<Object> creator) {
        return cache.computeIfAbsent(namespace, key, creator);
    }
}
