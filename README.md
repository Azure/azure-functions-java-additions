![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

|Branch|Status|
|---|---|
|master|[![Build status](https://ci.appveyor.com/api/projects/status/ebphtfegnposba6w?svg=true)](https://ci.appveyor.com/project/appsvc/azure-functions-java-library?branch=master)|
|dev|[![Build status](https://ci.appveyor.com/api/projects/status/ebphtfegnposba6w?svg=true)](https://ci.appveyor.com/project/appsvc/azure-functions-java-library?branch=dev)|

# Additional artifacts for Azure Java Functions
This repo contains two additional artifacts for building Azure Java Functions. 
* [azure-functions-java-core-library](https://github.com/Azure/azure-functions-java-additions/azure-functions-java-core-library)
* [azure-functions-java-spi](https://github.com/Azure/azure-functions-java-additions/azure-functions-java-spi)

For more information about Azure Java Functions please visit the [complete documentation of Azure Functions - Java Developer Guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java) for more details.

## azure-functions-maven plugin
[How to use azure-functions-maven plugin to create, update, deploy and test azure java functions](https://docs.microsoft.com/en-us/java/api/overview/azure/maven/azure-functions-maven-plugin/readme?view=azure-java-stable)

## Prerequisites

* Java 8
* [Apache Maven](https://maven.apache.org/) 3.0 or later

## Package feed

All Maven packages and plugins are restored from the `upstream-public` Azure Artifacts feed
(`https://pkgs.dev.azure.com/azfunc/public/_packaging/upstream-public/maven/v1`), which is configured
as the `central` repository in every `pom.xml` in this repository.

The repository root also has a [`settings.xml`](settings.xml) that mirrors every remote repository
(`external:*`) to the same feed. It exists because a `pom.xml` cannot cover everything:

- Maven resolves build extensions and plugin prefixes *before* a pom's `<repositories>` are honored,
	so those requests would otherwise go straight to Maven Central.
- `MavenAuthenticate@0` and the credential provider key credentials off the Azure Artifacts *feed
	name* (`upstream-public`), while the pom repository id must be `central` in order to override the
	id Maven inherits from the Super POM. The mirror id bridges the two.
- `build.ps1` clones and builds third-party Maven projects whose poms declare repositories this
	repository does not control, so only the mirror can keep those restores on the feed.

CI installs this file to `~/.m2/settings.xml`. Locally you only need it when pulling a package or
version the feed has not cached yet, in which case pass it explicitly with `mvn -s settings.xml`.

### Anonymous restore (default)

The feed allows anonymous reads, so no credentials are required to build once a package version has
been saved to the feed. External contributors and fresh clones need no setup. `mvn` just works.
Never commit credentials or a `<server>` entry to `settings.xml` in this repository because doing so
would force authentication on everyone.

### Authenticating (Microsoft developers only)

Authentication is only needed to *ingest* a package version that the feed has not cached yet. The
first restore of any new or upgraded dependency will fail anonymously with:

> No local versions of package '...'; please provide authentication to access versions from upstream
> that have not yet been saved to your feed.

When that happens, a Microsoft developer with access to the `azfunc/public` project must run the
restore once with credentials, which pulls the version from upstream and saves it to the feed. Every
subsequent anonymous restore then succeeds.

The recommended way to authenticate is the `artifacts-maven-credprovider`, which acquires a token via
Entra ID so you do not have to manage a PAT.

Run the helper script for your shell from the root of your clone. It installs the credential provider
into your local Maven repository if it is missing, then writes `.mvn/extensions.xml`. Both scripts
are idempotent, so re-running them is safe:

```powershell
./eng/scripts/Install-MavenCredentialProvider.ps1
```

```bash
./eng/scripts/install-maven-credprovider.sh
```

Pass `-Version` / `--version` to install a different release, and `-Force` / `--force` to reinstall or
to overwrite an `.mvn/extensions.xml` the script does not manage.

If you would rather do it by hand, the equivalent steps are:

1. Bootstrap the credential provider once per machine. Run this from a directory outside any Maven
	 project, such as your home directory. It downloads the extension from the public `AzureArtifacts`
	 tools feed, which needs no authentication:

	 ```powershell
	 mvn dependency:get "-Dartifact=com.microsoft.azure:artifacts-maven-credprovider:3.2.1" "-DremoteRepositories=central::::https://pkgs.dev.azure.com/artifacts-public/PublicTools/_packaging/AzureArtifacts/maven/v1"
	 ```

	 Using the repository id `central` matters. Maven records the extension as having come from
	 `central`, which is the same id this repository's `pom.xml` files declare, so the cached copy
	 validates during later builds.

2. Create `.mvn/extensions.xml` at the root of your clone:

	 ```xml
	 <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
		 <extension>
			 <groupId>com.microsoft.azure</groupId>
			 <artifactId>artifacts-maven-credprovider</artifactId>
			 <version>3.2.1</version>
		 </extension>
	 </extensions>
	 ```

`.mvn/` is deliberately listed in `.gitignore`. Do not commit it. The extension exits when it
detects a build context, and committing it would break anonymous restores for everyone else.

If you would rather not use the credential provider, you can instead add a `<server>` entry to your
user-level `~/.m2/settings.xml` (never to a file inside this repository), using an Azure DevOps
personal access token with Packaging read and write scope:

```xml
<settings>
	<servers>
		<server>
			<!-- Must match the <id> of the repository declared in the pom.xml files. -->
			<id>central</id>
			<username>azfunc</username>
			<password>[PERSONAL_ACCESS_TOKEN]</password>
		</server>
	</servers>
</settings>
```

CI covers this automatically. The `MavenAuthenticate@0` task in the build templates authenticates the
`central` repository, so merged changes to dependency versions are ingested by the pipeline. The
credential provider is not used in pipelines.

## Parent POM

Please see for details on Parent POM https://github.com/Microsoft/maven-java-parent

## Summary

[Azure Functions Summary](https://github.com/Azure/azure-functions-java-library#summary)

### Sample

For samples of Azure function in Java please refer to [Azure Function Java Samples](https://github.com/Azure/azure-functions-java-library#sample)
and [Azure Functions Java Samples Repository](https://github.com/Azure-Samples/azure-functions-samples-java)

### License

This project is under the benevolent umbrella of the [.NET Foundation](http://www.dotnetfoundation.org/) and is licensed under [the MIT License](LICENSE.txt)

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
