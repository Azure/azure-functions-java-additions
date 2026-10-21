## azure-functions-java-core-library_1.4.0 (unreleased)

### New
* Added default `HttpResponseMessage.Builder.bodyStream(InputStream)` and
  `bodyStream(HttpResponseMessage.IOConsumer<OutputStream>)` methods for deferred
  HTTP response streaming with compatible worker and host support.
* Added the nested `HttpResponseMessage.IOConsumer<T>` functional interface for
  callbacks that can throw `IOException`.

## azure-functions-java-spi_1.0.0

### New
* Added two new types for Kafka Extension - OAuthBearerMethod and KafkaMessageKeyType.

### Updates
* Updated the BrokerProtocol type for Kafka Extension.

### Breaking changes
* to be update
