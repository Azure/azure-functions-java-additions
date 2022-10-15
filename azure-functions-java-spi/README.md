![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/master/src/Azure.Functions.Cli/npm/assets/azure-functions-logo-color-raster.png)

# SPI for Azure Java Functions
This repo contains SPI library for building Azure Java Functions. **This library should not be used when building your function app.** 
This library provides hooks to third parties supporting them to interact with function runtime during function invocation process. 
**_This library should be used with scope as `provided`, because customer should not have transitive dependency on it_**

For more information about Azure Java Functions please visit the [complete documentation of Azure Functions - Java Developer Guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java) for more details.

## azure-functions-maven plugin
[How to use azure-functions-maven plugin to create, update, deploy and test azure java functions](https://docs.microsoft.com/en-us/java/api/overview/azure/maven/azure-functions-maven-plugin/readme?view=azure-java-stable)

## Prerequisites

* Java 8

## Parent POM

Please see for details on parent POM https://github.com/Microsoft/maven-java-parent

## Summary

[Azure Functions Summary](https://github.com/Azure/azure-functions-java-library#summary)

### Sample

For an example of a HttpTrigger Azure function in Java please refer to [HttpTrigger sample](https://github.com/Azure/azure-functions-java-library#sample)

### License

This project is under the benevolent umbrella of the [.NET Foundation](http://www.dotnetfoundation.org/) and is licensed under [the MIT License](LICENSE.txt)

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
