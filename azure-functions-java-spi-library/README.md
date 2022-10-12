![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/master/src/Azure.Functions.Cli/npm/assets/azure-functions-logo-color-raster.png)

# Extend library for Azure Java Functions
This repo contains spi library for building Azure Java Functions. **This library should not be used when build your function app.** 
This library provide hooks to third parties supporting them to interact with function runtime during function invocation process. 
**_This library should be used with scope as `provided`, because customer should not have transitive dependency on it_**

For more information about Azure Java Functions please visit the [complete documentation of Azure Functions - Java Developer Guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java) for more details.

## azure-functions-maven plugin
[How to use azure-functions-maven plugin to create, update, deploy and test azure java functions](https://docs.microsoft.com/en-us/java/api/overview/azure/maven/azure-functions-maven-plugin/readme?view=azure-java-stable)

## Prerequisites

* Java 8

## Parent POM

Please see for details on Parent POM https://github.com/Microsoft/maven-java-parent

## Summary

Azure Functions is a solution for easily running small pieces of code, or "functions," in the cloud. You can write just the code you need for the problem at hand, without worrying about a whole application or the infrastructure to run it. Functions can make development even more productive.Pay only for the time your code runs and trust Azure to scale as needed. Azure Functions lets you develop [serverless](https://azure.microsoft.com/en-us/solutions/serverless/) applications on Microsoft Azure.

Azure Functions supports triggers, which are ways to start execution of your code, and bindings, which are ways to simplify coding for input and output data. A function should be a stateless method to process input and produce output. Although you are allowed to write instance methods, your function must not depend on any instance fields of the class. You need to make sure all the function methods are `public` accessible and method with annotation @FunctionName is unique as that defines the entry for the function.

A deployable unit is an uber JAR containing one or more functions (see below), and a JSON file with the list of functions and triggers definitions, deployed to Azure Functions. The JAR can be created in many ways, although we recommend [Azure Functions Maven Plugin](https://docs.microsoft.com/en-us/java/api/overview/azure/maven/azure-functions-maven-plugin/readme), as it provides templates to get you started with key scenarios.

All the input and output bindings can be defined in `function.json` (not recommended), or in the Java method by using annotations (recommended). All the types and annotations used in this document are included in the [azure-functions-java-library](https://github.com/Azure/azure-functions-java-library) package.

### Sample

For an example of a HttpTrigger Azure function in Java please refer to [HttpTrigger sample](https://github.com/Azure/azure-functions-java-library#sample)

### License

This project is under the benevolent umbrella of the [.NET Foundation](http://www.dotnetfoundation.org/) and is licensed under [the MIT License](LICENSE.txt)

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
