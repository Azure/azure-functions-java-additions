package com.microsoft.azure.functions.sdktype;

import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.Set;

/**
 * A generic interface for storing & verifying raw fields needed
 * to build an SDK client object.
 * Typically:
 *   - setFieldValue(key, value)
 *   - parseAndVerify() to finalize typed fields & do checks
 *   - buildCacheKey() if relevant
 */
public interface SdkTypeMetaData {
    /**
     * If code wants to do a fully generic approach with
     * metadata keys, we can have a method for a set of required keys.
     */
    default Set<String> getRequiredFields() {
        return Collections.emptySet();
    }

    /**
     * The worker or SdkType might call this to retrieve a raw field value.
     */
    Object getFieldValue(String key);

    /**
     * The worker or SdkType might call this to store a raw field value.
     */
    void setFieldValue(String key, Object value);

    /**
     * A method to finalize fields & do validations.
     * If something is missing or invalid, throw an exception.
     */
    void parseAndVerify();

    /**
     * Return a Parameter object for the argument that uses this SDK type.
     */
    Parameter getParam();

    /**
     * The recognized fqcn, used so the registry can do createSdkType(...)
     * at runtime without re-checking param type.
     */
    String getFqcn();
}

