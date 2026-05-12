/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.functions.mcp;

import com.google.gson.Gson;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Represents the result of an MCP tool execution, wrapping content in the envelope format
 * understood by the Azure Functions MCP host extension.
 *
 * <p>In most cases, you don't need to create this directly — the middleware automatically
 * wraps supported return types. Use this class when you need full manual control over
 * the result content.</p>
 *
 * <p>Factory methods:</p>
 * <pre>
 * // Plain text
 * McpToolResult.text("Hello, World!");
 *
 * // Structured content from an {@literal @}McpContent-annotated POJO
 * McpToolResult.fromStructuredContent(mySnippet);
 *
 * // Single content block (using MCP Java SDK types)
 * McpToolResult.fromContent(new McpSchema.ImageContent(null, data, "image/png"));
 *
 * // Multiple content blocks
 * McpToolResult.fromContentList(List.of(
 *     new McpSchema.TextContent("Here is an image"),
 *     new McpSchema.ImageContent(null, data, "image/png")
 * ));
 * </pre>
 *
 * @since 1.0.0
 */
public class McpToolResult {

    // Gson for serializing the McpToolResult envelope and @McpContent-annotated user POJOs.
    // MCP SDK Content types are serialized using the SDK's own JSON mapper (McpJsonDefaults).
    private static final Gson GSON = new Gson();

    static final String TYPE_TEXT = "text";
    static final String TYPE_MULTI_CONTENT = "multi_content_result";
    static final String TYPE_CALL_TOOL_RESULT = "call_tool_result";

    private String type;
    private String content;
    private String structuredContent;

    /**
     * Default constructor for deserialization.
     */
    public McpToolResult() {
    }

    /**
     * Creates an McpToolResult with the specified type, content, and structured content.
     *
     * @param type              the content type identifier
     * @param content           the serialized content
     * @param structuredContent the structured content JSON (may be null)
     */
    public McpToolResult(String type, String content, String structuredContent) {
        this.type = type;
        this.content = content;
        this.structuredContent = structuredContent;
    }

    /**
     * Creates a text-only result.
     *
     * @param text the text content
     * @return an McpToolResult wrapping the text as a TextContent
     */
    public static McpToolResult text(String text) {
        TextContent block = new TextContent(text);
        return new McpToolResult(TYPE_TEXT, serializeContent(block), null);
    }

    /**
     * Creates a result from a single MCP SDK {@link Content} block.
     *
     * @param contentBlock the content block (e.g., {@code TextContent}, {@code ImageContent}, {@code ResourceLink})
     * @return an McpToolResult wrapping the content block
     * @throws IllegalArgumentException if {@code contentBlock} is {@code null}
     */
    public static McpToolResult fromContent(Content contentBlock) {
        if (contentBlock == null) {
            throw new IllegalArgumentException("contentBlock must not be null");
        }
        String blockType = contentBlock.type();
        return new McpToolResult(blockType, serializeContent(contentBlock), null);
    }

    /**
     * Creates a result from multiple MCP SDK {@link Content} blocks.
     *
     * @param contentBlocks the list of content blocks
     * @return an McpToolResult wrapping the content blocks as a multi-content result
     * @throws IllegalArgumentException if {@code contentBlocks} is null or empty
     */
    public static McpToolResult fromContentList(List<? extends Content> contentBlocks) {
        if (contentBlocks == null) {
            throw new IllegalArgumentException("contentBlocks must not be null");
        }
        if (contentBlocks.isEmpty()) {
            throw new IllegalArgumentException("contentBlocks must not be empty");
        }
        return new McpToolResult(TYPE_MULTI_CONTENT, serializeContentList(contentBlocks), null);
    }

    /**
     * Creates a result with both text content and structured content from an object.
     * The object is serialized to JSON which is used as both the text content (for
     * backwards compatibility) and the structured content (for clients that support it).
     *
     * <p>This is typically used with {@link McpContent @McpContent}-annotated POJOs.</p>
     *
     * @param obj the object to serialize as structured content
     * @return an McpToolResult with both text and structured content
     */
    public static McpToolResult fromStructuredContent(Object obj) {
        String json = GSON.toJson(obj);
        TextContent block = new TextContent(json);
        return new McpToolResult(TYPE_TEXT, serializeContent(block), json);
    }

    /**
     * Serializes an MCP SDK Content type using the SDK's JSON mapper.
     */
    static String serializeContent(Object content) {
        try {
            return io.modelcontextprotocol.json.McpJsonDefaults.getMapper().writeValueAsString(content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize MCP content", e);
        }
    }

    /**
     * Serializes a list of Content blocks using the MCP SDK's JSON mapper.
     *
     * <p>Uses an explicit {@code List<Content>} collection type so that Jackson emits the
     * polymorphic {@code "type"} discriminator on each element (e.g. {@code "type":"text"},
     * {@code "type":"image"}). Without this, Jackson sees each list element via its concrete
     * runtime type and skips the {@code @JsonTypeInfo} property declared on the {@code Content}
     * interface, producing JSON like {@code [{"text":"hello"},{"data":"...","mimeType":"..."}]}
     * which fails polymorphic deserialization on the host side.</p>
     */
    private static String serializeContentList(List<? extends Content> contentBlocks) {
        try {
            io.modelcontextprotocol.json.McpJsonMapper sdkMapper =
                    io.modelcontextprotocol.json.McpJsonDefaults.getMapper();
            // The MCP SDK ships a Jackson-backed mapper by default; reach the underlying
            // ObjectMapper so we can declare the parameterized element type explicitly.
            if (sdkMapper instanceof io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        ((io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper) sdkMapper).getObjectMapper();
                return mapper.writerFor(
                        mapper.getTypeFactory().constructCollectionType(List.class, Content.class)
                ).writeValueAsString(contentBlocks);
            }
            // Fallback for non-Jackson SDK mappers — content may lack type discriminator.
            return sdkMapper.writeValueAsString(contentBlocks);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize MCP content list", e);
        }
    }

    /**
     * Returns the content type identifier.
     *
     * @return the type string
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the content type identifier.
     *
     * @param type the type string
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the serialized content.
     *
     * @return the content JSON string
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the serialized content.
     *
     * @param content the content JSON string
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Returns the structured content JSON.
     *
     * @return the structured content string, or null if not present
     */
    public String getStructuredContent() {
        return structuredContent;
    }

    /**
     * Sets the structured content JSON.
     *
     * @param structuredContent the structured content string
     */
    public void setStructuredContent(String structuredContent) {
        this.structuredContent = structuredContent;
    }
}
