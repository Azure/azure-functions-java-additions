/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.functions.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpToolResultMiddleware#wrapReturnValue(Object)}.
 */
class McpToolResultMiddlewareTest {

    private static final Gson GSON = new Gson();

    // ========================================================================
    // Tests for null / non-wrappable return values
    // ========================================================================

    @Test
    void wrapReturnValue_null_returnsNull() {
        assertNull(McpToolResultMiddleware.wrapReturnValue(null));
    }

    @Test
    void wrapReturnValue_plainString_returnsNull() {
        assertNull(McpToolResultMiddleware.wrapReturnValue("Hello, World!"));
    }

    @Test
    void wrapReturnValue_plainPojo_returnsNull() {
        assertNull(McpToolResultMiddleware.wrapReturnValue(new PlainPojo("test")));
    }

    @Test
    void wrapReturnValue_primitiveInteger_returnsNull() {
        assertNull(McpToolResultMiddleware.wrapReturnValue(42));
    }

    // ========================================================================
    // Tests for McpToolResult pass-through
    // ========================================================================

    @Test
    void wrapReturnValue_mcpToolResult_returnsSameInstance() {
        McpToolResult original = McpToolResult.text("Hello");
        McpToolResult result = McpToolResultMiddleware.wrapReturnValue(original);

        assertSame(original, result);
    }

    // ========================================================================
    // Tests for MCP SDK Content type wrapping
    // ========================================================================

    @Test
    void wrapReturnValue_textContent_wrapsCorrectly() {
        TextContent block = new TextContent("Hello");
        McpToolResult result = McpToolResultMiddleware.wrapReturnValue(block);

        assertNotNull(result);
        assertEquals("text", result.getType());
        assertNull(result.getStructuredContent());
        assertNotNull(result.getContent());
    }

    @Test
    void wrapReturnValue_imageContent_wrapsCorrectly() {
        ImageContent block = new ImageContent(null, "base64data", "image/png");
        McpToolResult result = McpToolResultMiddleware.wrapReturnValue(block);

        assertNotNull(result);
        assertEquals("image", result.getType());
        assertNull(result.getStructuredContent());
        assertNotNull(result.getContent());
    }

    // ========================================================================
    // Tests for List<Content> wrapping
    // ========================================================================

    @Test
    void wrapReturnValue_listOfContent_wrapsAsMultiContent() {
        List<Content> blocks = Arrays.asList(
                new TextContent("Hello"),
                new ImageContent(null, "data", "image/jpeg")
        );

        McpToolResult result = McpToolResultMiddleware.wrapReturnValue(blocks);

        assertNotNull(result);
        assertEquals("multi_content_result", result.getType());
        assertNull(result.getStructuredContent());
        assertNotNull(result.getContent());
    }

    @Test
    void wrapReturnValue_emptyList_returnsNull() {
        List<Content> blocks = List.of();
        assertNull(McpToolResultMiddleware.wrapReturnValue(blocks));
    }

    @Test
    void wrapReturnValue_listOfStrings_returnsNull() {
        List<String> strings = Arrays.asList("a", "b");
        assertNull(McpToolResultMiddleware.wrapReturnValue(strings));
    }

    // ========================================================================
    // Tests for @McpContent-annotated POJO wrapping
    // ========================================================================

    @Test
    void wrapReturnValue_mcpContentAnnotatedPojo_wrapsWithStructuredContent() {
        AnnotatedSnippet snippet = new AnnotatedSnippet("test", "Hello, World!");
        McpToolResult result = McpToolResultMiddleware.wrapReturnValue(snippet);

        assertNotNull(result);
        assertEquals("text", result.getType());

        // Should have structured content
        assertNotNull(result.getStructuredContent());

        // Verify structured content is the serialized POJO
        JsonObject structured = GSON.fromJson(result.getStructuredContent(), JsonObject.class);
        assertEquals("test", structured.get("name").getAsString());
        assertEquals("Hello, World!", structured.get("content").getAsString());
    }

    // ========================================================================
    // Test for McpToolResult factory methods
    // ========================================================================

    @Test
    void mcpToolResult_text_createsCorrectEnvelope() {
        McpToolResult result = McpToolResult.text("Hello");

        assertEquals("text", result.getType());
        assertNull(result.getStructuredContent());
        assertNotNull(result.getContent());
    }

    @Test
    void mcpToolResult_fromStructuredContent_createsDualContent() {
        AnnotatedSnippet snippet = new AnnotatedSnippet("name", "value");
        McpToolResult result = McpToolResult.fromStructuredContent(snippet);

        assertEquals("text", result.getType());
        assertNotNull(result.getStructuredContent());
        assertNotNull(result.getContent());

        // Structured content should be the raw JSON of the object
        JsonObject structured = GSON.fromJson(result.getStructuredContent(), JsonObject.class);
        assertEquals("name", structured.get("name").getAsString());
    }

    @Test
    void mcpToolResult_fromContentList_createsMultiContent() {
        McpToolResult result = McpToolResult.fromContentList(Arrays.asList(
                new TextContent("text1"),
                new TextContent("text2")
        ));

        assertEquals("multi_content_result", result.getType());
        assertNull(result.getStructuredContent());
        assertNotNull(result.getContent());
    }

    // ========================================================================
    // Regression: list elements must include the polymorphic "type" discriminator
    // so the host extension can deserialize as IEnumerable<ContentBlock>.
    // ========================================================================

    @Test
    void mcpToolResult_fromContentList_each_element_has_type_discriminator() {
        McpToolResult result = McpToolResult.fromContentList(Arrays.asList(
                new TextContent("hello"),
                new ImageContent(null, "base64data", "image/png")
        ));

        // Inner content is a JSON array. Each element must carry "type".
        com.google.gson.JsonArray array = com.google.gson.JsonParser
                .parseString(result.getContent())
                .getAsJsonArray();
        assertEquals(2, array.size());

        JsonObject first = array.get(0).getAsJsonObject();
        assertTrue(first.has("type"), "first element missing \"type\" discriminator: " + first);
        assertEquals("text", first.get("type").getAsString());

        JsonObject second = array.get(1).getAsJsonObject();
        assertTrue(second.has("type"), "second element missing \"type\" discriminator: " + second);
        assertEquals("image", second.get("type").getAsString());
    }

    @Test
    void mcpToolResult_fromContent_single_image_has_type_discriminator() {
        McpToolResult result = McpToolResult.fromContent(new ImageContent(null, "x", "image/png"));

        JsonObject obj = com.google.gson.JsonParser.parseString(result.getContent()).getAsJsonObject();
        assertTrue(obj.has("type"), "single content missing \"type\" discriminator: " + obj);
        assertEquals("image", obj.get("type").getAsString());
    }

    // ========================================================================
    // Helper types
    // ========================================================================

    @McpContent
    static class AnnotatedSnippet {
        private String name;
        private String content;

        AnnotatedSnippet(String name, String content) {
            this.name = name;
            this.content = content;
        }

        public String getName() { return name; }
        public String getContent() { return content; }
    }

    static class PlainPojo {
        private String value;

        PlainPojo(String value) {
            this.value = value;
        }

        public String getValue() { return value; }
    }
}
