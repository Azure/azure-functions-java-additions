/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

/**
 * Thrown when the Functions host does not complete a settlement request.
 */
public final class ServiceBusSettlementException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String operation;
    private final String statusCode;

    /**
     * Creates the exception.
     *
     * @param operation the settlement operation, such as {@code Complete} or {@code Deadletter}
     * @param statusCode the name of the gRPC status code the host returned, such as {@code FAILED_PRECONDITION}
     * @param detail a short explanation from the host, or null
     */
    public ServiceBusSettlementException(String operation, String statusCode, String detail) {
        super("Service Bus " + operation + " failed with status " + statusCode
            + (detail == null || detail.isEmpty() ? "" : ": " + detail));
        this.operation = operation;
        this.statusCode = statusCode;
    }

    /**
     * Gets the settlement operation that failed.
     *
     * @return the operation name, such as {@code Complete}
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Gets the gRPC status code the host returned. {@code FAILED_PRECONDITION} means the host does not hold a valid
     * lock for the message. {@code UNKNOWN} means the broker rejected the request.
     *
     * @return the status code name
     */
    public String getStatusCode() {
        return statusCode;
    }
}
