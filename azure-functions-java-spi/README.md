![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

# SPI for Azure Java Functions
This repo contains SPI for building Azure Java Functions. **This library could be used when building your function app.** 
This library provides hooks to third parties supporting them to interact with function runtime during function invocation process. 
**_This library should be used with scope as `provided`, because customers should not inherit it via a transitive dependency_**

For more information about Azure Java Functions please visit the [complete documentation of Azure Functions - Java Developer Guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java).

## azure-functions-maven plugin
[How to use azure-functions-maven plugin to create, update, deploy and test azure java functions](https://docs.microsoft.com/en-us/java/api/overview/azure/maven/azure-functions-maven-plugin/readme?view=azure-java-stable)

## Prerequisites

* Java 8

## Invocation-scoped SDK bindings

The internal `sdk` SPI adds immutable `ModelBindingData` and `SdkInvocationContext`.
Workers preserve raw payload bytes, expose ordered payloads by host binding name,
and provide the startup host endpoint without interpreting extension payloads.
Each invocation has its own context. Register invocation-owned identity state
with `registerCloseable`, invalidate the context when invocation middleware exits,
and close resources on success, failure and cancellation. Cleanup failures must
not replace the original invocation failure. Shared transport channels are not
invocation-owned resources.

The optional [Service Bus module](../azure-functions-java-servicebus/README.md)
uses this contract. Workers must provide SPI `1.2.0-SNAPSHOT` and SDK types
`1.1.0-SNAPSHOT`. Applications must not package separate copies of these
worker-owned SPI artifacts.

## Summary

[Azure Functions Summary](https://github.com/Azure/azure-functions-java-library#summary)

### Sample

For samples of Azure function in Java please refer to [Azure Function Java Samples](https://github.com/Azure/azure-functions-java-library#sample)
and [Azure Functions Java Samples Repository](https://github.com/Azure-Samples/azure-functions-samples-java)

### License

This project is under the benevolent umbrella of the [.NET Foundation](http://www.dotnetfoundation.org/) and is licensed under [the MIT License](LICENSE.txt)

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
