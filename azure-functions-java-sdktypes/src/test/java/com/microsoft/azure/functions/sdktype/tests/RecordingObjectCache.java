/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;

import java.util.function.Supplier;

/**
 * Test cache that records the key of its last lookup and hands back a marker without calling the creator.
 * Nothing is built, so a test sees the key that an SdkType caches its instance under.
 */
final class RecordingObjectCache implements ObjectCache<CacheKey, Object> {
    static final Object MARKER = new Object();

    private volatile CacheKey key;

    @Override
    public Object computeIfAbsent(Class<?> namespace, CacheKey key, Supplier<Object> creator) {
        this.key = key;
        return MARKER;
    }

    CacheKey key() {
        return key;
    }
}
