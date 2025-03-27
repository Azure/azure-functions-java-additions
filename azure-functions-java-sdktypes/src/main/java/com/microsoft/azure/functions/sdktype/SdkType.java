package com.microsoft.azure.functions.sdktype;

import java.lang.reflect.Parameter;

/**
 * A recognized SDK type that:
 *   - Has references to a hydrator and verifier
 *   - Knows how to parse invocation metadata
 *   - Can produce a CacheKey (if needed)
 *   - Has default methods for verify() and buildInstance() that rely on getVerifier(), getHydrator().
 *
 * @param <M> The concrete type implementing SdkTypeMetaData
 */
public interface SdkType<M extends SdkTypeMetaData> {

    /**
     * Return the associated metadata object, so
     * the worker can fill it with fields if needed.
     */
    M getMetaData();

    /**
     * Return the hydrator that builds the final instance using the metaData.
     */
    SdkTypeHydrator<M> getHydrator();

    /**
     * Build or retrieve a final instance of the SDK object.
     * Calls parseAndVerify() on metaData
     * then calls the hydrator.
     */
    default Object buildInstance() throws Exception {
        M metaData = getMetaData();
        metaData.parseAndVerify();
        SdkTypeHydrator<M> hydrator = getHydrator();
        return hydrator.createInstance(metaData);
    }
}