## Unreleased

### New
* Added `SdkInvocationContext` and `ModelBindingData` to SPI so a worker can pass raw host payloads and the host endpoint to SDK types.
* Added context-aware hydration and Service Bus parameter analysis to SDK types.
* Added the optional `azure-functions-java-servicebus` module with `ServiceBusReceivedMessage` bindings and `ServiceBusMessageActions` settlement through the host. It needs azure-messaging-servicebus 7.19.0-beta.1 and is built with `-Pservicebus` until that SDK is released.

## azure-functions-java-spi_1.0.0

### New
* Added two new types for Kafka Extension - OAuthBearerMethod and KafkaMessageKeyType.

### Updates
* Updated the BrokerProtocol type for Kafka Extension.

### Breaking changes
* to be update
