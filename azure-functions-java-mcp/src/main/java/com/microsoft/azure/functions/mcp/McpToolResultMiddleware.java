/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.functions.mcp;

import com.google.gson.Gson;
import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import io.modelcontextprotocol.spec.McpSchema.Content;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Middleware that automatically wraps MCP tool function return values for rich content support.
 *
 * <p>When added to the classpath, this middleware is auto-discovered by the Azure Functions
 * Java worker via Java's {@code ServiceLoader} mechanism. It intercepts return values from
 * functions annotated with {@code @McpToolTrigger} and wraps them in the envelope format
 * expected by the host extension.</p>
 *
 * <p>Supported return types:</p>
 * <ul>
 *   <li>{@link McpToolResult} — passed through as-is (full manual control)</li>
 *   <li>MCP SDK {@code Content} types ({@code TextContent}, {@code ImageContent},
 *       {@code ResourceLink}, etc.) — wrapped as a single content block</li>
 *   <li>{@code List<Content>} — wrapped as a multi-content result</li>
 *   <li>{@link McpContent @McpContent}-annotated POJOs — serialized as both text content
 *       (for backward compatibility) and structured content (for clients that support it)</li>
 *   <li>{@code String} and other types — not wrapped, passed to the host's default handler</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class McpToolResultMiddleware implements Middleware {

    private static final Logger LOGGER = Logger.getLogger(McpToolResultMiddleware.class.getName());
    private static final Gson GSON = new Gson();
    private static final String MCP_TOOL_TRIGGER_ANNOTATION = "McpToolTrigger";

    // The Azure Functions Java worker resets the Thread Context ClassLoader (TCCL)
    // to the system classloader after function execution. The MCP SDK's
    // McpJsonDefaults.getMapper() relies on ServiceLoader (which uses the TCCL)
    // to discover the JSON mapper implementation. We capture the customer classloader
    // at construction time and restore it before processing return values.
    private final ClassLoader customerClassLoader = this.getClass().getClassLoader();

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        chain.doNext(context);

        String triggerParamName = context.getParameterName(MCP_TOOL_TRIGGER_ANNOTATION);
        if (triggerParamName == null) {
            return;
        }

        Object returnValue = context.getReturnValue();
        if (returnValue == null) {
            return;
        }

        // Restore the customer classloader as TCCL for ServiceLoader discovery
        Thread currentThread = Thread.currentThread();
        ClassLoader previousTccl = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(customerClassLoader);
            McpToolResult result = wrapReturnValue(returnValue);
            if (result != null) {
                context.updateReturnValue(GSON.toJson(result));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to wrap MCP tool result, returning raw value", e);
        } finally {
            currentThread.setContextClassLoader(previousTccl);
        }
    }

    @SuppressWarnings("unchecked")
    static McpToolResult wrapReturnValue(Object returnValue) {
        if (returnValue == null) {
            return null;
        }

        if (returnValue instanceof McpToolResult) {
            return (McpToolResult) returnValue;
        }

        // Direct instanceof check for MCP SDK Content types
        if (returnValue instanceof Content) {
            return McpToolResult.fromContent((Content) returnValue);
        }

        if (returnValue instanceof List<?>) {
            List<?> list = (List<?>) returnValue;
            // Intentional: we only check the first element's type, matching the Python SDK's
            // approach. A mixed-type list (e.g., Content + String) would fail during
            // serialization and be caught by the caller's error handling.
            if (!list.isEmpty() && list.get(0) instanceof Content) {
                return McpToolResult.fromContentList((List<Content>) list);
            }
        }

        if (returnValue.getClass().isAnnotationPresent(McpContent.class)) {
            return McpToolResult.fromStructuredContent(returnValue);
        }

        return null;
    }
}
