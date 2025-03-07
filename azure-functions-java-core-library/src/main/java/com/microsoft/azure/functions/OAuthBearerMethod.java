/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package com.microsoft.azure.functions;

public enum OAuthBearerMethod {
    Default(0),
    Oidc(1);

    private int value;
    OAuthBearerMethod(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
