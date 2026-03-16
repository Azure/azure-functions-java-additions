/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.functions.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a POJO type as an MCP result type that should be serialized as structured content.
 * When a function returns an object of a type decorated with this annotation,
 * the result will be serialized as both text content (for backwards compatibility)
 * and structured content (for clients that support it).
 *
 * <p>This annotation can be applied to classes and records.</p>
 *
 * <p>Example:</p>
 * <pre>
 * {@literal @}McpContent
 * public class Snippet {
 *     private String name;
 *     private String content;
 *     // getters and setters
 * }
 * </pre>
 *
 * <p>When a function returns a {@code Snippet} instance, it will be serialized as both
 * a text content block (JSON string) and as structured content (JSON object), enabling
 * MCP clients to parse the result programmatically.</p>
 *
 * <p><b>Note:</b> This annotation requires Java 17 or later, as the underlying MCP Java SDK
 * uses sealed interfaces and records.</p>
 *
 * @since 1.0.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface McpContent {
}
