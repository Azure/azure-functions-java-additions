![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

# Core library for Azure Java Functions
This repo contains core library for building Azure Java Functions. `azure-functions-java-core-library` contains base class for building Azure Java Functions. 
However, you don't need to include `azure-functions-java-core-library` as a dependency when you build your function app, because it comes with [azure-functions-java-library](https://github.com/Azure/azure-functions-java-library)
which has a transitive dependency on `azure-functions-java-core-library`

For more information about Azure Java Functions please visit the [complete documentation of Azure Functions - Java Developer Guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java).

## azure-functions-maven plugin
[How to use azure-functions-maven plugin to create, update, deploy and test azure java functions](https://docs.microsoft.com/en-us/java/api/overview/azure/maven/azure-functions-maven-plugin/readme?view=azure-java-stable)

## Prerequisites

* Java 8

## HTTP response streaming (unreleased)

The proposed `1.4.0` API adds two default methods to
`HttpResponseMessage.Builder`. Both preserve the body object and delegate to
`body(Object)`, so existing builder implementations need no new methods.

Use `bodyStream(InputStream)` to forward a stream, or
`bodyStream(HttpResponseMessage.IOConsumer<OutputStream>)` to produce bytes
incrementally. For example, inside an HTTP-triggered function:

```java
return request.createResponseBuilder(HttpStatus.OK)
    .header("Content-Type", "text/plain; charset=utf-8")
    .bodyStream(output -> {
        output.write("first chunk\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
        output.write("last chunk\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    })
    .build();
```

Building a response does not read an input stream or invoke a writer. Keep an
input stream open after returning the response. The runtime closes streams it
consumes, even on failure. The caller must close an input stream that is replaced
or never handed to the runtime. Write synchronously in a callback, do not close
or retain its output stream, and close any resources opened by the callback.
Treat streaming bodies as single-use. A typed null delegates to `body(null)`;
an untyped `bodyStream(null)` is ambiguous, so use `body(null)` to clear a body.

Streaming requires compatible Java worker and host support with HTTP response
streaming enabled. Adding this library alone does not enable streaming. Older
workers cannot be assumed to buffer these body types correctly. The worker
implementation is tracked in
[Azure/azure-functions-java-worker#877](https://github.com/Azure/azure-functions-java-worker/pull/877).
Host and intermediary buffering can affect incremental delivery. Errors after
headers are sent can truncate the response and cannot change its status.

`1.4.0-SNAPSHOT` is a development coordinate for local integration, not a
published release guarantee. A core library release and downstream dependency
updates must be coordinated before deployment. Existing `body(Object)` calls
remain available and are not changed by these default methods.

## Summary

[Azure Functions Summary](https://github.com/Azure/azure-functions-java-library#summary)

### Sample

For samples of Azure function in Java please refer to [Azure Function Java Samples](https://github.com/Azure/azure-functions-java-library#sample)
and [Azure Functions Java Samples Repository](https://github.com/Azure-Samples/azure-functions-samples-java)

### License

This project is under the benevolent umbrella of the [.NET Foundation](http://www.dotnetfoundation.org/) and is licensed under [the MIT License](LICENSE.txt)

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
