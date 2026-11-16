## azure-functions-java-sdktypes_2.0.0

### New
* Added `SdkTypeDescriptor`, a read-only description of an SDK-bound parameter that is shared per function. Its `bind(Map)` method returns a new immutable `SdkTypeMetaData` for each invocation.
* Added `BlobClientDescriptor` and `BlobContainerDescriptor`.
* Added `SdkTypeFactory.createDescriptor(String, Parameter)` and `SdkTypeRegistry.createDescriptor(String, Parameter)`.

### Updates
* `SdkTypeMetaData`, `BlobClientMetaData`, `BlobContainerMetaData` and `SdkParameterAnalysisResult` are now immutable and safe to share between threads.
* Invocation values are validated when the metadata is created, so `SdkType.buildInstance` no longer needs a separate validation step.

### Breaking changes
* Removed `SdkTypeMetaData.getRequiredFields`, `getFieldValue`, `setFieldValue` and `parseAndVerify`. Use `SdkTypeDescriptor.getRequiredFields` and `SdkTypeDescriptor.bind` instead.
* Removed `SdkTypeFactory.createMetaData` and `SdkTypeRegistry.createMetaData`. Use `createDescriptor`.
* `SdkParameterAnalysisResult` is now final and constructed from a list of descriptors. `addSdkTypeMetaData` and `getSdkTypesMetaData` are replaced by `getSdkTypeDescriptors`.
* The `BlobClientMetaData` and `BlobContainerMetaData` constructors now take the field values directly. The metadata classes and `SdkParameterAnalysisResult` are final.
* See the migration section in the `azure-functions-java-sdktypes` README for a mapping of old to new calls.

## azure-functions-java-spi_1.0.0

### New
* Added two new types for Kafka Extension - OAuthBearerMethod and KafkaMessageKeyType.

### Updates
* Updated the BrokerProtocol type for Kafka Extension.

### Breaking changes
* to be update
