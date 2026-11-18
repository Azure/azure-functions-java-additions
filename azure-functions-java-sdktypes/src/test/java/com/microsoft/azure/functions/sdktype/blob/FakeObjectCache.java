/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Test cache that behaves like the worker's cache where it matters here: one creator runs per key,
 * a creator may use the cache again for another key, a failed creator leaves no entry,
 * and the failure of a creator surfaces wrapped in a RuntimeException.
 */
final class FakeObjectCache implements ObjectCache<CacheKey, Object> {
    private final Map<List<Object>, Object> entries = new HashMap<>();
    private final boolean wrapFailures;
    private int calls;

    FakeObjectCache(boolean wrapFailures) {
        this.wrapFailures = wrapFailures;
    }

    @Override
    public synchronized Object computeIfAbsent(Class<?> namespace, CacheKey key, Supplier<Object> creator) {
        calls++;
        final List<Object> composite = Arrays.<Object>asList(namespace, key);
        Object value = entries.get(composite);
        if (value == null) {
            try {
                value = creator.get();
            } catch (RuntimeException e) {
                if (wrapFailures) {
                    throw new RuntimeException("Failed to computeIfAbsent for " + composite,
                            new RuntimeException(e));
                }
                throw e;
            }
            entries.put(composite, value);
        }
        return value;
    }

    synchronized int calls() {
        return calls;
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized Set<Class<?>> namespaces() {
        final Set<Class<?>> namespaces = new HashSet<>();
        for (List<Object> composite : entries.keySet()) {
            namespaces.add((Class<?>) composite.get(0));
        }
        return namespaces;
    }
}
