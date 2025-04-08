package com.microsoft.azure.functions.sdktype.exceptions;

/**
 * Thrown by SdkTypeFactory or SdkType if building the final SdkType fails.
 */
public class SdkTypeCreationException extends RuntimeException {
    public SdkTypeCreationException(String message) {
        super(message);
    }
    public SdkTypeCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
