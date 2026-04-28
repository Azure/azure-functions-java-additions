/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.opentelemetry;

import com.microsoft.azure.functions.TraceContext;
import io.opentelemetry.context.propagation.TextMapGetter;

/**
 * TextMapGetter that extracts trace context from Azure Functions TraceContext.
 * Uses enum singleton pattern for performance.
 */
public enum TraceContextTextMapGetter implements TextMapGetter<TraceContext> {

    /** Singleton instance. */
    INSTANCE;

    @Override
    public Iterable<String> keys(final TraceContext carrier) {
        return carrier.getAttributes().keySet();
    }

    @Override
    public String get(final TraceContext carrier, final String key) {
        if (carrier == null || key == null) {
            return null;
        }
        // Check W3C headers first
        if ("traceparent".equalsIgnoreCase(key)) {
            return carrier.getTraceparent();
        }
        if ("tracestate".equalsIgnoreCase(key)) {
            return carrier.getTracestate();
        }
        // Fall back to custom attributes
        return carrier.getAttributes().get(key);
    }
}
