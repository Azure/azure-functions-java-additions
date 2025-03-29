package com.microsoft.azure.functions.sdktype;

import java.lang.reflect.Parameter;

/**
 * A recognized SDK type that:
 -  - Has references to a hydrator and verifier
 +  - Has references to a hydrator
 *   - Knows how to parse invocation metadata
 *   - Can produce a CacheKey (if needed)
 *   - Has default method buildInstance() that calls parseAndVerify()
 *     on the metaData, then calls the hydrator.
 *
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
     * Calls parseAndVerify() on metaData then calls the hydrator.
     *
     * @throws com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException
     *         if creation fails inside the hydrator or parseAndVerify throws an error.
     * @return The fully built client or SDK object.
     */
    default Object buildInstance() throws Exception {
        M metaData = getMetaData();
        metaData.parseAndVerify();
        SdkTypeHydrator<M> hydrator = getHydrator();
        try {
            return hydrator.createInstance(metaData);
        } catch (Exception ex) {
            // Convert any reflection or parse error to SdkTypeCreationException
            throw new com.microsoft.azure.functions.sdktype.exceptions.SdkTypeCreationException(
                    "Failed to build instance for fqcn = " + metaData.getFqcn(), ex
            );
        }
    }
}