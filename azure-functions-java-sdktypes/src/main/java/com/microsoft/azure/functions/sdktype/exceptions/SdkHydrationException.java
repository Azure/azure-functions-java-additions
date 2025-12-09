/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.exceptions;

/**
 * Thrown by SdkTypeHydrator when reflection-based creation fails,
 * environment variables are invalid, etc.
 */
public class SdkHydrationException extends RuntimeException {
    public SdkHydrationException(String message) {
        super(message);
    }
    public SdkHydrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
