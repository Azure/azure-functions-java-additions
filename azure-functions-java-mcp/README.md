# Azure Functions Java MCP Support

Rich content and structured content support for [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) tool functions in Azure Functions Java.

## Overview

This library extends Azure Functions Java with middleware that enables MCP tool functions to return rich content types — images, resource links, multi-content responses, and structured content — instead of plain text strings.

It uses the official [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) types (`TextContent`, `ImageContent`, `ResourceLink`, etc.) and integrates with the Azure Functions Java worker via the SPI middleware mechanism.

## Prerequisites

- **Java 17 or later** — required by the [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) which uses
  sealed interfaces and records. Function apps using Java 11 or earlier cannot use this library.
  Ensure your `pom.xml` targets Java 17+ and your Azure Functions runtime is configured for Java 17:
  ```xml
  <properties>
      <java.version>17</java.version>
  </properties>
  ```
  ```xml
  <runtime>
      <os>Linux</os>
      <javaVersion>17</javaVersion>
  </runtime>
  ```
- Azure Functions Java Library (`azure-functions-java-library`)
- An MCP JSON implementation on the classpath (e.g., `mcp-json-jackson2`)

## Setup

Add the following dependencies to your `pom.xml`:

```xml
<dependency>
    <groupId>com.microsoft.azure.functions</groupId>
    <artifactId>azure-functions-java-mcp</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-json-jackson2</artifactId>
    <version>1.1.0</version>
</dependency>
```

The middleware is auto-discovered by the Azure Functions Java worker — no additional configuration needed.

## Usage

### Structured content with `@McpContent`

Annotate a POJO with `@McpContent` to automatically serialize it as both text content (for backward compatibility) and structured content (for clients that support it):

```java
import com.microsoft.azure.functions.mcp.McpContent;

@McpContent
public class Snippet {
    private String name;
    private String content;
    // getters and setters
}

@FunctionName("GetSnippet")
public Snippet getSnippet(
        @McpToolTrigger(name = "getSnippet", description = "Gets a snippet")
        String context,
        @McpToolProperty(name = "name", propertyType = "string",
                         description = "Snippet name", isRequired = true)
        String name) {
    return new Snippet(name, "Hello, World!");
}
```

### Rich content blocks

Return MCP SDK content types directly for images, resource links, and other rich content:

```java
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Content;

// Single image
@FunctionName("RenderImage")
public ImageContent renderImage(
        @McpToolTrigger(name = "renderImage", description = "Returns an image")
        String context,
        @McpToolProperty(name = "data", propertyType = "string",
                         description = "Base64 image data", isRequired = true)
        String data) {
    return new ImageContent(null, data, "image/png");
}

// Multiple content blocks
@FunctionName("GetMultiContent")
public List<Content> getMultiContent(
        @McpToolTrigger(name = "getMultiContent",
                        description = "Returns text and an image")
        String context) {
    return List.of(
        new TextContent("Here is the image:"),
        new ImageContent(null, imageData, "image/png")
    );
}
```

### Manual control with `McpToolResult`

For full control over the result envelope:

```java
import com.microsoft.azure.functions.mcp.McpToolResult;

@FunctionName("CustomResult")
public McpToolResult customResult(
        @McpToolTrigger(name = "customResult", description = "Custom result")
        String context) {
    return McpToolResult.text("Hello from a custom result!");
}
```

### Plain strings (no middleware needed)

Functions that return `String` continue to work as before — the middleware does not modify them:

```java
@FunctionName("Hello")
public String hello(
        @McpToolTrigger(name = "hello", description = "Says hello")
        String context) {
    return "Hello, World!";
}
```

## How it works

1. The middleware is discovered by the Azure Functions Java worker via Java's `ServiceLoader` mechanism
2. After each MCP tool function executes, the middleware inspects the return value
3. If the return type is a supported rich type (`Content`, `List<Content>`, `@McpContent` POJO, or `McpToolResult`), the middleware wraps it in the envelope format expected by the host extension
4. The Azure Functions Maven Plugin automatically sets `useResultSchema=true` in `function.json` for functions with rich return types, telling the host to use the envelope-aware result binder
5. Plain string returns are left untouched — the host handles them with its default binder

## JSON implementation

This library uses the MCP Java SDK's `McpJsonDefaults.getMapper()` to serialize content types. The SDK discovers the JSON implementation via `ServiceLoader` — you choose which one to include:

| Artifact | JSON library |
|---|---|
| `mcp-json-jackson2` | Jackson 2.x |
| `mcp-json-jackson3` | Jackson 3.x |

The `azure-functions-java-mcp` module declares `mcp-json-jackson2` as a `provided` dependency, so it compiles against it but doesn't force it on you. Add whichever implementation you prefer to your project.

## License

MIT License. See [LICENSE](../../LICENSE) for details.
