/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;
import com.microsoft.azure.functions.sdktype.blob.BlobClientSdkTypeFactory;
import com.microsoft.azure.functions.sdktype.blob.BlobContainerSdkTypeFactory;
import com.microsoft.azure.functions.sdktype.exceptions.SdkRegistryException;

import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry that knows about recognized SDK client FQCNs and can create SdkType objects.
 * Throws SdkRegistryException if asked to create a descriptor or an SdkType for an unrecognized FQCN.
 * The registry is not modified after construction, so it is safe to share between threads.
 */
public final class SdkTypeRegistry {
    // Maps FQCN -> Class<? extends SdkType>
    private final Map<String, SdkTypeFactory> knownTypes;

    public SdkTypeRegistry() {
        final Map<String, SdkTypeFactory> types = new HashMap<>();
        types.put("com.azure.storage.blob.BlobClient", new BlobClientSdkTypeFactory());
        types.put("com.azure.storage.blob.BlobContainerClient", new BlobContainerSdkTypeFactory());
        this.knownTypes = Collections.unmodifiableMap(types);
    }

    /**
     * Check if we support a param type.
     *
     * @param fqcn the fully qualified class name to check
     * @return true if the type is supported, false otherwise
     */
    public boolean isTypeSupported(String fqcn) {
        return knownTypes.containsKey(fqcn);
    }

    /**
     * Build-time usage: create an immutable SdkTypeDescriptor from the recognized fqcn + param.
     *
     * @param fqcn the fully qualified class name of the SDK type
     * @param param the parameter being analyzed
     * @return the created SdkTypeDescriptor
     * @throws Exception if descriptor creation fails
     */
    public SdkTypeDescriptor createDescriptor(String fqcn, Parameter param) throws Exception {
        final SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new SdkRegistryException("Unrecognized SdkType: " + fqcn);
        }
        return factory.createDescriptor(fqcn, param);
    }

    /**
     * Runtime usage: produce the final SdkType from the immutable metaData of one invocation.
     * The type has no cache, so it builds a new instance every time. Use
     * {@link #createSdkType(SdkTypeMetaData, ObjectCache)} to let it cache what it builds.
     *
     * @param metaData the metadata containing configuration details
     * @return the created SdkType
     * @throws Exception if SdkType creation fails
     */
    public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
        return factoryFor(metaData).createSdkType(metaData);
    }

    /**
     * Runtime usage: like {@link #createSdkType(SdkTypeMetaData)}, and also lets the SdkType cache what it builds,
     * such as its client or a credential that its clients share, through the worker's object cache. The worker then
     * only calls {@code buildInstance()}, and each SdkType decides for itself what is cached.
     *
     * <p>The factory is not given the cache itself but a view that is bound to the namespace of this registry.
     * The view supports only {@code computeIfAbsent}, so an SdkType can use the cache for its own objects but
     * cannot read or remove entries of the worker or of other libraries. The worker keeps control of the size,
     * the expiry and the reference strength.</p>
     *
     * @param metaData the metadata containing configuration details
     * @param cache the worker's object cache, or null to cache nothing
     * @return the created SdkType
     * @throws Exception if SdkType creation fails
     */
    public SdkType<?> createSdkType(SdkTypeMetaData metaData, ObjectCache<CacheKey, Object> cache) throws Exception {
        final SdkTypeFactory factory = factoryFor(metaData);
        if (cache == null) {
            return factory.createSdkType(metaData, null);
        }
        return factory.createSdkType(metaData, new NamespacedObjectCache(cache, SdkTypeRegistry.class));
    }

    private SdkTypeFactory factoryFor(SdkTypeMetaData metaData) {
        final String fqcn = metaData.getFqcn(); // see below how we store this
        final SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new SdkRegistryException("Unrecognized SdkType: " + fqcn);
        }
        return factory;
    }
}
