package com.microsoft.azure.functions.sdktype.exceptions;

/**
 * Thrown by SdkTypeRegistry when it fails to find or create a recognized type.
 */
public class SdkRegistryException extends RuntimeException {
    public SdkRegistryException(String message) {
        super(message);
    }
    public SdkRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
