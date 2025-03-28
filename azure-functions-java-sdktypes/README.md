![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/master/src/Azure.Functions.Cli/npm/assets/azure-functions-logo-color-raster.png)

## Table of Contents

- [Java SDK Types for Azure Functions](#java-sdk-types-for-azure-functions)
  - [Overview](#overview)
  - [Key Interfaces](#key-interfaces)
    - [SdkTypeMetaData](#sdktypemetadata)
    - [SdkType](#sdktype)
    - [CachableSdkType](#cachablesdktype)
    - [SdkTypeHydrator](#sdktypehydrator)
    - [SdkTypeFactory](#sdktypefactory)
  - [Registry](#registry)
  - [Example: BlobClient](#example-blobclient)
  - [Build-Time vs. Runtime](#build-time-vs-runtime)
  - [Shaded Fallback Libraries](#shaded-fallback-libraries)

---

# Java SDK Types for Azure Functions

This library provides a **two-phase** approach to binding advanced Azure SDK clients in the Java Functions worker:

1. **Build-Time Analysis**: A minimal metadata object (`SdkTypeMetaData`) is created for each recognized SDK parameter (e.g., `BlobClient`, `BlobContainerClient`, etc.).
2. **Runtime Invocation**: At invocation time, the worker transforms that
   metadata into a **concrete SDK client** (`SdkType`) via reflection.
   If Managed Identity is used for authentication, the worker will fall back to
   a **shaded** `azure-identity` library if the user hasn't brought their own.

---

## Overview

The **`azure-functions-java-sdktypes`** library decouples **analysis** of SDK-bound parameters (e.g., `BlobClient`, `QueueClient`, etc.) from **invocation-time** creation of those SDK clients. This design helps:

- Provide a **minimal** build-time “analyzer” that identifies recognized parameters.
- Allow the **Java Worker** to create the actual client objects at runtime—using reflection—without forcing users to reference those libraries directly.
- Support **connection string** vs. **managed identity** patterns, with fallback to a **shaded** `azure-identity` library if the user does not supply their own.

---

## Key Interfaces

### SdkTypeMetaData

```java
public interface SdkTypeMetaData {
    Set<String> getRequiredFields();
    Object getFieldValue(String key);
    void setFieldValue(String key, Object value);

    void parseAndVerify();

    Parameter getParam();
    String getFqcn();
}
```

- **Purpose**: Store raw field values and define `parseAndVerify()`.
- **Build Tools**: Create an instance of `SdkTypeMetaData` for each recognized parameter (via the `SdkTypeRegistry`).
- **Runtime**: The worker sets required fields (like `"ContainerName"`, `"Connection"`) from the invocation data, then calls `parseAndVerify()`.

### SdkType

```java
public interface SdkType<M extends SdkTypeMetaData> {
    M getMetaData();
    SdkTypeHydrator<M> getHydrator();

    default Object buildInstance() throws Exception {
        M meta = getMetaData();
        meta.parseAndVerify();
        return getHydrator().createInstance(meta);
    }
}
```

- **Purpose**: A recognized “type” that references its `MetaData` and a `Hydrator`.
- **Runtime**: The worker calls `buildInstance()` to finalize the client creation.

### CachableSdkType

```java
public interface CachableSdkType<M extends SdkTypeMetaData> extends SdkType<M> {
    CacheKey buildCacheKey();
}
```

- **Purpose**: A **sub-interface** of `SdkType` that supports **caching** via a `CacheKey`.
- **Why**: Some SDK clients are expensive to recreate each time. This interface allows the Java Worker to store them in a shared cache.

### SdkTypeHydrator

```java
public interface SdkTypeHydrator<M extends SdkTypeMetaData> {
    Object createInstance(M metaData) throws Exception;
}
```

- **Purpose**: Reflection logic that **builds** the final client object from the typed fields in `MetaData`.
- **Example**: Building a `BlobClient` from containerName, blobName, and connection string (or managed identity).

### SdkTypeFactory

```java
public interface SdkTypeFactory {
    SdkTypeMetaData createMetaData(String fqcn, Parameter param) throws Exception;
    SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception;
}
```

- **Purpose**: Provide **two-phase** creation:
    - **Build-Time**: `createMetaData(...)` for minimal `SdkTypeMetaData`.
    - **Runtime**: `createSdkType(...)` from that metadata.

---

## Registry

**`SdkTypeRegistry`** holds a map of **FQCN** (e.g., `"com.azure.storage.blob.BlobClient"`) to a **`SdkTypeFactory`**. At:

- **Build-Time**: The analyzer calls `registry.createMetaData(fqcn, param)` to produce `SdkTypeMetaData`.
- **Runtime**: The worker calls `registry.createSdkType(metaData)` to produce a concrete `SdkType`.

This avoids duplicating the FQCN → `SdkType` logic.

---

## Example: BlobClient

A typical **BlobClient** pattern:

1. **`BlobClientMetaData`** extends `SdkTypeMetaData`, storing `containerName`, `blobName`, `connectionEnvVar`.
2. **`BlobClientCacheKey`** implements `CacheKey`, capturing the fields for caching.
3. **`BlobClientHydrator`** implements reflection logic for building the client via `BlobClientBuilder`.
4. **`BlobClientSdkType`** implements `CachableSdkType<BlobClientMetaData>` returning a `buildCacheKey()` from the meta data.
5. **`BlobClientSdkTypeFactory`** implements `SdkTypeFactory`, creating the meta data at build time, the `SdkType` at runtime.

---

## Build-Time vs. Runtime

1. **Build-Time**: The user’s function code is analyzed by the **Maven/Gradle** plugin. If a parameter is recognized (e.g., type `"com.azure.storage.blob.BlobClient"`), we do `registry.createMetaData(...)` to produce a `SdkTypeMetaData`.
2. **Runtime**: The worker sees that metadata, calls `registry.createSdkType(metaData)` → yields a `SdkType`. The worker sets the required fields from the invocation request, calls `sdkType.buildInstance()`, and **injects** the resulting client into the user function.

---

## Shaded Fallback Libraries

Often, we want to **shade** `azure-identity` so the worker can do **managed identity** reflection if the user does not bring those libraries. This allows:

- **Unshaded** usage if user has them: The hydrator tries `"com.azure.identity.DefaultAzureCredentialBuilder"`.
- **Fallback** if not found: `"com.microsoft.azure.functions.shaded.com.azure.identity.DefaultAzureCredentialBuilder"`.
