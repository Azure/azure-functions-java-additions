/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions;

public enum KafkaMessageKeyType {
    Int(0),
    Long(1),
    String(2),
    Binary(3);

    private int value;

    KafkaMessageKeyType(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
