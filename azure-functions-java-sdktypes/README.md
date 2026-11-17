![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

## Table of Contents

- [Java SDK Types for Azure Functions](#java-sdk-types-for-azure-functions)
  - [Overview](#overview)
  - [Key Interfaces](#key-interfaces)
    - [SdkTypeDescriptor](#sdktypedescriptor)
    - [SdkTypeMetaData](#sdktypemetadata)
    - [SdkType](#sdktype)
    - [CachableSdkType](#cachablesdktype)
    - [SdkTypeHydrator](#sdktypehydrator)
    - [SdkTypeFactory](#sdktypefactory)
  - [Registry](#registry)
  - [Example: BlobClient](#example-blobclient)
  - [Build-Time vs. Runtime](#build-time-vs-runtime)
  - [Thread Safety](#thread-safety)
  - [Shaded Fallback Libraries](#shaded-fallback-libraries)

---

# Java SDK Types for Azure Functions

This library provides a **two-phase** approach to binding advanced Azure SDK clients in the Java Functions worker:

1. **Build-Time Analysis**: An immutable descriptor (`SdkTypeDescriptor`) is created for each recognized SDK parameter (e.g., `BlobClient`, `BlobContainerClient`, etc.).
2. **Runtime Invocation**: At invocation time, the worker binds that descriptor to the
   invocation's raw values, which gives an immutable `SdkTypeMetaData`. It then turns the
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

### SdkTypeDescriptor

```java
public interface SdkTypeDescriptor {
    String getFqcn();
    Parameter getParam();
    Set<String> getRequiredFields();   // unmodifiable

    SdkTypeMetaData bind(Map<String, String> rawValues);
}
```

- **Purpose**: Describe one SDK parameter of one function. It is immutable and safe to share across threads.
- **Build Tools**: Create one descriptor per recognized parameter (via the `SdkTypeRegistry`).
- **Runtime**: For each invocation, the worker reads the values named in `getRequiredFields()` from the invocation data and calls `bind(rawValues)`. This validates the values and returns a new `SdkTypeMetaData`.

### SdkTypeMetaData

```java
public interface SdkTypeMetaData {
    Parameter getParam();
    String getFqcn();
}
```

- **Purpose**: The immutable, validated values of **one invocation**, produced by `SdkTypeDescriptor.bind(...)`.

### SdkType

```java
public interface SdkType<M extends SdkTypeMetaData> {
    M getMetaData();
    SdkTypeHydrator<M> getHydrator();

    default Object buildInstance() throws Exception {
        return getHydrator().createInstance(getMetaData());
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
    SdkTypeDescriptor createDescriptor(String fqcn, Parameter param) throws SdkTypeCreationException;
    SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception;
}
```

- **Purpose**: Provide **two-phase** creation:
    - **Build-Time**: `createDescriptor(...)` for the immutable per-function `SdkTypeDescriptor`.
    - **Runtime**: `createSdkType(...)` from the metadata that `descriptor.bind(...)` returned.

---

## Registry

**`SdkTypeRegistry`** holds a map of **FQCN** (e.g., `"com.azure.storage.blob.BlobClient"`) to a **`SdkTypeFactory`**. At:

- **Build-Time**: The analyzer calls `registry.createDescriptor(fqcn, param)` to produce an `SdkTypeDescriptor`.
- **Runtime**: The worker calls `descriptor.bind(rawValues)` and then `registry.createSdkType(metaData)` to produce a concrete `SdkType`.

This avoids duplicating the FQCN → `SdkType` logic.

---

## Example: BlobClient

A typical **BlobClient** pattern:

1. **`BlobClientDescriptor`** implements `SdkTypeDescriptor`. It requires `"ContainerName"`, `"BlobName"` and `"Connection"`, and its `bind` returns a `BlobClientMetaData`.
2. **`BlobClientMetaData`** implements `SdkTypeMetaData`, storing `containerName`, `blobName`, `connectionEnvVar`. It is immutable.
3. **`BlobClientCacheKey`** implements `CacheKey`, capturing the fields for caching.
4. **`BlobClientHydrator`** implements reflection logic for building the client via `BlobClientBuilder`.
5. **`BlobClientSdkType`** implements `CachableSdkType<BlobClientMetaData>` returning a `buildCacheKey()` from the meta data.
6. **`BlobClientSdkTypeFactory`** implements `SdkTypeFactory`, creating the descriptor at build time, the `SdkType` at runtime.

`BlobContainerClient` follows the same pattern with `BlobContainerDescriptor` (`"ContainerName"`, `"Connection"`) and `BlobContainerMetaData`.

---

## Build-Time vs. Runtime

1. **Build-Time**: The user’s function code is analyzed by the **Maven/Gradle** plugin. If a parameter is recognized (e.g., type `"com.azure.storage.blob.BlobClient"`), we do `registry.createDescriptor(...)` to produce an `SdkTypeDescriptor`.
2. **Runtime**: The worker keeps the descriptor for the function. For each invocation it collects the required values from the request, calls `descriptor.bind(rawValues)` → `SdkTypeMetaData`, then `registry.createSdkType(metaData)` → `SdkType`. It calls `sdkType.buildInstance()` and **injects** the resulting client into the user function.

---

## Thread Safety

Descriptors and metadata are immutable, so concurrent invocations never share mutable state.

- A descriptor can be shared by any number of threads. `bind(...)` never changes it and returns a new `SdkTypeMetaData` on every call.
- For one invocation, the `SdkType`, its cache key and the built client are all derived from that invocation's metadata.

---

## Shaded Fallback Libraries

Often, we want to **shade** `azure-identity` so the worker can do **managed identity** reflection if the user does not bring those libraries. This allows:

- **Unshaded** usage if user has them: The hydrator tries `"com.azure.identity.DefaultAzureCredentialBuilder"`.
- **Fallback** if not found: `"com.microsoft.azure.functions.shaded.com.azure.identity.DefaultAzureCredentialBuilder"`.
