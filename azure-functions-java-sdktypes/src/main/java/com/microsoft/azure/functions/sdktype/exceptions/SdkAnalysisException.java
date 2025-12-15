/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.exceptions;

/**
 * Thrown when the SdkParameterAnalyzer fails to analyze parameters
 * (e.g., reflection problem, multiple recognized types).
 */
public class SdkAnalysisException extends RuntimeException {
    public SdkAnalysisException(String message) {
        super(message);
    }
    public SdkAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
