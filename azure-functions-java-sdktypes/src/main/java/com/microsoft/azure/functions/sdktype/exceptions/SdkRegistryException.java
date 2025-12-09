/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

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
