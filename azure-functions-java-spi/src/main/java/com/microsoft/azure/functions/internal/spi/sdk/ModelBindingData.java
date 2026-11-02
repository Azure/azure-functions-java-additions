/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.internal.spi.sdk;

import java.util.Objects;

/**
 * An opaque host model binding payload, preserved without decoding.
 */
public final class ModelBindingData {
    private final String version;
    private final String source;
    private final String contentType;
    private final byte[] content;

    public ModelBindingData(String version, String source, String contentType, byte[] content) {
        this.version = Objects.requireNonNull(version, "version");
        this.source = Objects.requireNonNull(source, "source");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.content = Objects.requireNonNull(content, "content").clone();
    }

    public String getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content.clone();
    }
}
