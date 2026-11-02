# Service Bus SDK bindings for Java Functions

This optional artifact lets a function take `ServiceBusReceivedMessage` (or an array or `List` of
it) from a Service Bus trigger, and settle messages with `ServiceBusMessageActions`.
Settlement calls the `Settlement` gRPC service of the Functions host, which still holds the message
lock. It does not open a Service Bus receiver or a second broker connection.

This mirrors the Python and Node.js bindings. The actions object is one per process, is injected by
type, and uses the lock token of the message you pass in. The host rejects tokens it does not hold,
for example when the lock has expired.

## Requirements

* A host that sends deferred model binding data for Service Bus triggers and serves the `Settlement`
  gRPC service. In testing, host 4.25.2 and extension bundle 4.21.0 (Service Bus extension 5.16.4)
  worked. Hosts 4.24.3 and 4.24.4 answer settlement calls with `UNIMPLEMENTED`.
* A Java worker with SDK type invocation support, built against `azure-functions-java-spi`
  1.2.0-SNAPSHOT and `azure-functions-java-sdktypes` 1.1.0-SNAPSHOT.
* `azure-messaging-servicebus` 7.19.0-beta.1, which adds
  `ServiceBusReceivedMessage.fromAmqpMessage(BinaryData, String)`. It comes from
  [azure-sdk-for-java#50904](https://github.com/Azure/azure-sdk-for-java/pull/50904) and is not on
  Maven Central yet.

Add `com.microsoft.azure.functions:azure-functions-java-servicebus` to the function application.
The worker keeps its own gRPC and protobuf copies relocated, so this artifact brings the ones it needs.

## Settling messages

Declare an unannotated `ServiceBusMessageActions` parameter.

```java
@FunctionName("settleOrder")
public void run(
    @ServiceBusQueueTrigger(name = "order", queueName = "orders", connection = "ServiceBusConnection")
    ServiceBusReceivedMessage order,
    ServiceBusMessageActions actions) {
    actions.deadLetterMessage(order, "InvalidOrder", "The order is missing its identifier.");
}
```

`ServiceBusMessageActions` is an interface, so tests can supply their own implementation. It offers
`completeMessage`, `abandonMessage`, `deferMessage`, `deadLetterMessage` and `renewMessageLock`.
Abandon, defer and dead letter also take a `Map<String, Object>` of application properties to modify.
Calls are synchronous with a 30 second deadline. A rejected request throws
`ServiceBusSettlementException`, which carries the operation and the gRPC status code name.

In testing, the host ignored `autoCompleteMessages` in `host.json` for these functions. It completed a
message the function did not settle after a successful run, so such a message is neither redelivered
nor dead lettered.

## Wire contract

`src/main/proto/settlement.proto` is copied from the Service Bus extension in
[azure-sdk-for-net](https://github.com/Azure/azure-sdk-for-net/blob/de7f4bb91a9cef4cb2e7169556f3a65df6644155/sdk/servicebus/Microsoft.Azure.WebJobs.Extensions.ServiceBus/src/Grpc/Proto/settlement.proto).
Only the two Java options at the top were added. Stubs are generated at build time.

The model binding data envelope is version `1.0`, source `AzureServiceBusReceivedMessage` and content
type `application/octet-stream`. Its content is the 16 byte `.NET Guid.ToByteArray()` lock token
followed by the raw AMQP message.

The endpoint is the one the host gave the worker at startup. Plain HTTP is only accepted for loopback
addresses. Any other host needs HTTPS.

## Building

The module is not part of the default build because the SDK beta above is not on the feed yet.
Install that SDK build locally, then use JDK 9 or later, since the module compiles with
`--release 8`.

```powershell
mvn -pl azure-functions-java-servicebus -Pservicebus install "-Dgpg.skip=true"
```

This module does not use `java-8-parent`. Its dependency management pins old Guava, Netty and Jackson
versions that conflict with the Azure SDK and gRPC.

## Testing

The unit tests here use an in-process gRPC server. The worker's opt-in docker test
(`dockertests/linux-consumption-tests/test_servicebus_sdk_types.py` in
[azure-functions-java-worker#903](https://github.com/Azure/azure-functions-java-worker/pull/903))
runs a real Functions host with the Service Bus emulator on Java 8, 17 and 21. It checks complete, dead
letter with reason and description, defer, batch and topic subscriptions at the broker. It has not been
run against an Azure Service Bus namespace.
