# Service Bus SDK bindings for Java Functions

This optional artifact binds `com.azure.messaging.servicebus.ServiceBusReceivedMessage`,
`ServiceBusReceivedMessage[]`, and `List<ServiceBusReceivedMessage>` to Service Bus
triggers. `ServiceBusMessageActions` settles the host-held message lock through the
Functions host endpoint. It does not open a Service Bus receiver or establish a
second broker connection.

## Integration dependencies

This is an integration candidate, not a standalone released feature. It requires
the matching worker and Maven plugin changes for deferred bindings, the matching
Java library trigger annotations, and an Azure SDK build containing the supported
`ServiceBusReceivedMessage.fromAmqpMessage(BinaryData, String)` factory. The pinned
`7.19.0-beta.1` SDK coordinate must be built from the companion Azure SDK change.
An unmodified artifact with that coordinate does not provide the required API.
Update this dependency to the eventual released factory version before releasing
this artifact.

The source-verified settlement wire baseline is Service Bus extension 5.13.2 or
later. A minimum released Functions host version has not been established.
The host must supply deferred model binding data, and the Java worker must enable
`supportsDeferredBinding` through generated binding metadata. Binding expressions
in other input or output bindings can disable deferred trigger data. In that
case, SDK parameters fail explicitly rather than silently falling back to a body
binding. Ordinary body bindings are unchanged.

Add `com.microsoft.azure.functions:azure-functions-java-servicebus:1.0.0-SNAPSHOT`
to the function application's dependencies. The worker consumes
`azure-functions-java-spi:1.2.0-SNAPSHOT` and
`azure-functions-java-sdktypes:1.1.0-SNAPSHOT`.

## Manual settlement

Use `@BindingName` to associate the actions parameter with the trigger's host
binding name. The actions parameter does not create another host binding.
Disable automatic completion when settling manually.

```java
@FunctionName("settleOrder")
public void run(
    @ServiceBusQueueTrigger(
        name = "order",
        queueName = "orders",
        connection = "ServiceBusConnection",
        autoCompleteMessages = false)
    ServiceBusReceivedMessage order,
    @BindingName("order") ServiceBusMessageActions actions) {
    actions.deadLetterMessage(order, "InvalidOrder", "The order is missing its identifier.");
}
```

The actions API is synchronous. It provides `completeMessage`, `abandonMessage`,
`deferMessage`, `deadLetterMessage`, and `renewMessageLock`. Abandon, defer and
dead-letter have property-update overloads taking `Map<String, Object>`.
Properties are serialized as an AMQP map, not JSON. Null and empty dead-letter
reason/description values preserve the host protocol's absent/empty distinction.
Lock renewal returns no timestamp because the host response is empty.

The same actions object accepts each message in a batch. It only accepts the
exact message objects hydrated for its own invocation and binding. It rejects
copied messages, messages from another invocation, and use after invocation exit.
Successful complete, abandon, defer and dead-letter calls prevent another local
settlement of that token. Lock renewal does not mark a message settled.
RPC errors propagate to the function. All calls must finish before the function
returns. Do not retain messages or actions for later background work.

## Payload and transport

The accepted envelope is version `1.0`, source `AzureServiceBusReceivedMessage`,
and content type `application/octet-stream`. Its content is a 16-byte
`.NET Guid.ToByteArray()` lock token followed by a raw AMQP message. Envelope
content type is distinct from the message's own content type. Binary bodies,
application properties and 64-bit sequence numbers are decoded by the supported
Azure SDK factory without a JSON or text round trip. Unsupported AMQP forms are
rejected by that factory rather than partially decoded.

The transport uses the endpoint supplied to the worker at startup. Channels are
shared within the process and closed on process shutdown. Messages and actions
are invocation-local and disposed through the worker's completion hook.
Plain HTTP is accepted only for `localhost` or literal loopback addresses.
Remote endpoints require HTTPS. Each synchronous RPC has a 30-second deadline.
Session settlement, remote worker proxies, and live Azure host end-to-end
settlement have not been established by this candidate.

## Building

Install the companion Azure SDK artifact first. Use the repository's configured
Azure Artifacts feed for dependencies. With a compatible JDK configured:

```powershell
mvn -s settings.xml -pl azure-functions-java-servicebus -am test
mvn -s settings.xml -pl azure-functions-java-servicebus -am install "-Dgpg.skip=true"
```

The inherited Maven parent uses older analysis plugins that require JDK 8 for
the full lifecycle. On newer JDKs, `test` can run independently of those
verification-phase plugins.

For uncached dependencies, use the repository's documented
`eng\scripts\Install-MavenCredentialProvider.ps1` helper and ordinary POM-based
repository configuration. If the `-s settings.xml` mirror prevents the credential
provider itself from loading, omit `-s settings.xml` for that local restore.
The module still declares the approved Azure Artifacts feed. Do not substitute
an alternate package repository or commit authentication configuration.

Unlike the older modules, this module inherits the repository aggregator rather
than `java-8-parent`. That parent's legacy dependency management overrides modern
SDK and gRPC transitive versions. Compiler, test, source and Javadoc plugins are
pinned explicitly instead. The integration tests execute on Java 8 as well as
Java 17, rather than relying only on a Java 8 bytecode target.
