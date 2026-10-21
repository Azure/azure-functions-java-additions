package com.microsoft.azure.functions.servicebus;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.sdktype.SdkParameterAnalysisResult;
import com.microsoft.azure.functions.sdktype.SdkParameterAnalyzer;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.google.protobuf.StringValue;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedInteger;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ServiceBusBindingTest {
    private static final String TOKEN = "00112233-4455-6677-8899-aabbccddeeff";
    // .NET Guid.ToByteArray followed by an AMQP data section containing "abc".
    private static final byte[] HOST_FIXTURE = hex("33221100554477668899aabbccddeeff005375a003616263");
    private final ServiceBusBindingProvider provider = new ServiceBusBindingProvider();

    void function(@BindingName("trigger") ServiceBusReceivedMessage message,
        @BindingName("trigger") ServiceBusReceivedMessage[] batch,
        @BindingName("trigger") List<ServiceBusReceivedMessage> list,
        @BindingName("trigger") ServiceBusMessageActions actions, List<String> other,
        ServiceBusReceivedMessage unannotated) { }

    void invalidActions(@ServiceBusQueueTrigger(name = "trigger", queueName = "orders", connection = "connection")
        ServiceBusMessageActions actions) { }

    void anotherBinding(@BindingName("secondTrigger") ServiceBusReceivedMessage message) { }

    @Test
    void producerGuidAndAmqpFixtureIsDecodedWithoutStringRoundTrip() {
        ServiceBusReceivedMessage message = ServiceBusBindingProvider.decode(envelope(HOST_FIXTURE));
        assertEquals(TOKEN, message.getLockToken());
        assertArrayEquals(new byte[] {'a', 'b', 'c'}, message.getBody().toBytes());
    }

    @Test
    void malformedContainerLengthsAreRejectedThroughHostEnvelope() {
        String prefix = "33221100554477668899aabbccddeeff";
        assertThrows(IllegalArgumentException.class, () -> ServiceBusBindingProvider.decode(
            envelope(hex(prefix + "005376c0ff0141"))));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusBindingProvider.decode(
            envelope(hex(prefix + "005377c1ff024141"))));
        assertNotNull(ServiceBusBindingProvider.decode(envelope(hex(prefix + "005376c0020141")))
            .getRawAmqpMessage());
        assertNotNull(ServiceBusBindingProvider.decode(envelope(hex(prefix + "005377c103024141")))
            .getRawAmqpMessage());
    }

    @Test
    void rawAnnotationsPropertiesAndInt64PrecisionArePreserved() {
        Message raw = Message.Factory.create();
        raw.setBody(new Data(new Binary(new byte[] {0, (byte) 0xff, 1})));
        Header header = new Header();
        header.setDeliveryCount(UnsignedInteger.valueOf(4));
        raw.setHeader(header);
        raw.setApplicationProperties(new ApplicationProperties(Collections.singletonMap("large", 9007199254740993L)));
        Map<Symbol, Object> annotations = new HashMap<>();
        annotations.put(Symbol.valueOf("x-opt-sequence-number"), 9007199254740995L);
        annotations.put(Symbol.valueOf("x-opt-enqueued-time"), new java.util.Date(1700000000000L));
        raw.setMessageAnnotations(new MessageAnnotations(annotations));
        byte[] buffer = new byte[4096];
        int length = raw.encode(buffer, 16, buffer.length - 16);
        System.arraycopy(HOST_FIXTURE, 0, buffer, 0, 16);
        ServiceBusReceivedMessage message = ServiceBusBindingProvider.decode(envelope(Arrays.copyOf(buffer, length + 16)));
        assertArrayEquals(new byte[] {0, (byte) 0xff, 1}, message.getBody().toBytes());
        assertEquals(9007199254740993L, message.getApplicationProperties().get("large"));
        assertEquals(9007199254740995L, message.getSequenceNumber());
        assertEquals(4, message.getDeliveryCount());
        assertEquals(1700000000000L, message.getEnqueuedTime().toInstant().toEpochMilli());
    }

    @Test
    void invalidEnvelopeVersionsSourcesTypesAndTruncationFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> ServiceBusBindingProvider.decode(
            new ModelBindingData("2.0", "AzureServiceBusReceivedMessage", "application/octet-stream", HOST_FIXTURE)));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusBindingProvider.decode(
            new ModelBindingData("1.0", "other", "application/octet-stream", HOST_FIXTURE)));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusBindingProvider.decode(
            new ModelBindingData("1.0", "AzureServiceBusReceivedMessage", "application/json", HOST_FIXTURE)));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusBindingProvider.decode(envelope(new byte[16])));
        assertThrows(RuntimeException.class, () -> ServiceBusBindingProvider.decode(envelope(new byte[17])));
    }

    @Test
    void analyzerRecognizesOnlySupportedMessageShapesAndActions() throws Exception {
        SdkParameterAnalysisResult result = new SdkParameterAnalyzer().analyze(method());
        assertEquals(5, result.getSdkTypesMetaData().size());
        assertEquals(ServiceBusMessageActions.class.getName(), result.getSdkTypesMetaData().get(3).getFqcn());
        for (int index = 0; index < 3; index++) {
            assertEquals(ServiceBusReceivedMessage.class.getName(), result.getSdkTypesMetaData().get(index).getFqcn());
            assertEquals(parameters()[index], result.getSdkTypesMetaData().get(index).getParam());
        }
    }

    @Test
    void registryHydrationUsesServiceProviderAndFreshInvocationMetadata() throws Exception {
        SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
        SdkTypeMetaData template = analyzer.analyze(method()).getSdkTypesMetaData().get(0);
        try (Context context = context()) {
            SdkTypeMetaData metadata = analyzer.getRegistry().createMetaData(template.getFqcn(), template.getParam());
            Object message = analyzer.getRegistry().createSdkType(metadata).buildInstance(context);
            assertInstanceOf(ServiceBusReceivedMessage.class, message);
            assertSame(message, provider.createInstance(parameters()[0], context));
        }
    }

    @Test
    void singleArrayAndListShareIdentityWithinScopeButNotAcrossInvocations() throws Exception {
        try (Context first = context(); Context second = context()) {
            ServiceBusReceivedMessage message = (ServiceBusReceivedMessage) provider.createInstance(parameters()[0], first);
            ServiceBusReceivedMessage[] batch = (ServiceBusReceivedMessage[]) provider.createInstance(parameters()[1], first);
            List<?> list = (List<?>) provider.createInstance(parameters()[2], first);
            assertSame(message, batch[0]);
            assertSame(message, list.get(0));
            assertThrows(UnsupportedOperationException.class, list::clear);
            assertNotSame(message, provider.createInstance(parameters()[0], second));
            ServiceBusMessageActions actions = (ServiceBusMessageActions) provider.createInstance(parameters()[3], first);
            ServiceBusReceivedMessage other = (ServiceBusReceivedMessage) provider.createInstance(parameters()[0], second);
            assertEquals(message.getLockToken(), other.getLockToken());
            assertThrows(IllegalArgumentException.class, () -> actions.completeMessage(other));
            assertEquals(1, first.resources.size());
            first.close();
            assertThrows(IllegalStateException.class, () -> actions.completeMessage(message));
            assertThrows(IllegalStateException.class, () -> provider.createInstance(parameters()[0], first));
        }
    }

    @Test
    void batchesPreserveOrderAndSingleCardinalityIsStrict() throws Exception {
        try (Context context = context()) {
            byte[] second = HOST_FIXTURE.clone();
            second[0] = 0x44;
            context.payloads = Arrays.asList(envelope(HOST_FIXTURE), envelope(second));
            ServiceBusReceivedMessage[] batch = (ServiceBusReceivedMessage[]) provider.createInstance(parameters()[1], context);
            assertEquals(TOKEN, batch[0].getLockToken());
            assertEquals("00112244-4455-6677-8899-aabbccddeeff", batch[1].getLockToken());
            assertThrows(IllegalArgumentException.class, () -> provider.createInstance(parameters()[0], context));
        }
    }

    @Test
    void actionsRequireEndpointButMessagesDoNotAndMissingAssociationFails() throws Exception {
        try (Context context = context()) {
            context.endpoint = null;
            assertInstanceOf(ServiceBusReceivedMessage.class, provider.createInstance(parameters()[0], context));
            assertThrows(IllegalStateException.class, () -> provider.createInstance(parameters()[3], context));
            assertThrows(IllegalArgumentException.class, () -> provider.createInstance(parameters()[5], context));
            Parameter invalid = ServiceBusBindingTest.class.getDeclaredMethod("invalidActions",
                ServiceBusMessageActions.class).getParameters()[0];
            assertThrows(IllegalArgumentException.class, () -> provider.createInstance(invalid, context));
        }
        try (Context context = context()) {
            context.payloads = Collections.emptyList();
            assertThrows(IllegalArgumentException.class, () -> provider.createInstance(parameters()[0], context));
        }
    }

    @Test
    void parallelInvocationsKeepDistinctMessageIdentityAndCleanup() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<ServiceBusReceivedMessage>> tasks = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                tasks.add(() -> {
                    try (Context context = context()) {
                        ServiceBusReceivedMessage message =
                            (ServiceBusReceivedMessage) provider.createInstance(parameters()[0], context);
                        assertSame(message, ((ServiceBusReceivedMessage[])
                            provider.createInstance(parameters()[1], context))[0]);
                        assertEquals(1, context.resources.size());
                        return message;
                    }

                });
            }
            List<Future<ServiceBusReceivedMessage>> results = executor.invokeAll(tasks);
            for (int index = 1; index < results.size(); index++) {
                assertNotSame(results.get(0).get(), results.get(index).get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publicActionsUseHostEndpointAndOnlySuccessfulSettlementTransitionsState() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MethodDescriptor<StringValue, StringValue> method =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("Settlement/Complete")
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance())).build();
        // Complete's field 1 is a string, so StringValue is an independent decoder for this request.
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(ServerServiceDefinition.builder("Settlement")
                .addMethod(method, ServerCalls.asyncUnaryCall((request, response) -> {
                    assertEquals(TOKEN, request.getValue());
                    if (calls.incrementAndGet() == 1) {
                        response.onError(Status.FAILED_PRECONDITION.withDescription("lock missing").asRuntimeException());
                    } else {
                        response.onNext(StringValue.getDefaultInstance());
                        response.onCompleted();
                    }
                })).build()).build().start();
        try (Context context = context()) {
            context.endpoint = URI.create("http://127.0.0.1:" + server.getPort());
            ServiceBusReceivedMessage message =
                (ServiceBusReceivedMessage) provider.createInstance(parameters()[0], context);
            ServiceBusMessageActions actions =
                (ServiceBusMessageActions) provider.createInstance(parameters()[3], context);
            assertSame(actions, provider.createInstance(parameters()[3], context));
            StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, () -> actions.completeMessage(message));
            assertEquals(Status.Code.FAILED_PRECONDITION, failure.getStatus().getCode());
            actions.completeMessage(message);
            assertThrows(IllegalStateException.class, () -> actions.completeMessage(message));
            assertEquals(2, calls.get());
        } finally {
            server.shutdownNow();
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void registeredCleanupActuallyRemovesInvocationIdentityState() throws Exception {
        try (Context context = context()) {
            Object original = provider.createInstance(parameters()[0], context);
            context.resources.get(0).close();
            Object recreated = provider.createInstance(parameters()[0], context);
            assertNotSame(original, recreated);
        }
    }

    @Test
    void sameTokenFromDifferentBindingIsNotAuthorizedByActions() throws Exception {
        try (Context context = context()) {
            ServiceBusMessageActions actions =
                (ServiceBusMessageActions) provider.createInstance(parameters()[3], context);
            Parameter otherParameter = ServiceBusBindingTest.class.getDeclaredMethod("anotherBinding",
                ServiceBusReceivedMessage.class).getParameters()[0];
            ServiceBusReceivedMessage other =
                (ServiceBusReceivedMessage) provider.createInstance(otherParameter, context);
            assertEquals(TOKEN, other.getLockToken());
            assertThrows(IllegalArgumentException.class, () -> actions.completeMessage(other));
        }
    }

    @Test
    void slowPayloadInOneInvocationDoesNotBlockAnotherInvocation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Context first = context(); Context second = context()) {
            first.onRead = () -> {
                reading.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            };
            Future<Object> slow = executor.submit(() -> provider.createInstance(parameters()[0], first));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            Future<Object> fast = executor.submit(() -> provider.createInstance(parameters()[0], second));
            assertInstanceOf(ServiceBusReceivedMessage.class, fast.get(5, TimeUnit.SECONDS));
            release.countDown();
            assertInstanceOf(ServiceBusReceivedMessage.class, slow.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static Parameter[] parameters() throws Exception { return method().getParameters(); }

    private static Method method() throws Exception {
        return ServiceBusBindingTest.class.getDeclaredMethod("function", ServiceBusReceivedMessage.class,
            ServiceBusReceivedMessage[].class, List.class, ServiceBusMessageActions.class,
            List.class, ServiceBusReceivedMessage.class);
    }

    private static ModelBindingData envelope(byte[] content) {
        return new ModelBindingData("1.0", "AzureServiceBusReceivedMessage", "application/octet-stream", content);
    }

    private static Context context() { return new Context(); }

    private static byte[] hex(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    private static final class Context implements SdkInvocationContext, AutoCloseable {
        private boolean active = true;
        private URI endpoint = URI.create("http://127.0.0.1:1");
        private List<ModelBindingData> payloads = Collections.singletonList(envelope(HOST_FIXTURE));
        private final List<AutoCloseable> resources = new ArrayList<>();
        private Runnable onRead = () -> { };
        public String getInvocationId() { return "invocation"; }
        public List<ModelBindingData> getModelBindingData(String name) {
            assertTrue("trigger".equals(name) || "secondTrigger".equals(name));
            onRead.run();
            return payloads;
        }
        public URI getHostEndpoint() { return endpoint; }
        public boolean isActive() { return active; }
        public void registerCloseable(AutoCloseable resource) { resources.add(resource); }
        public void close() throws Exception {
            active = false;
            for (AutoCloseable resource : resources) { resource.close(); }
            resources.clear();
        }
    }
}
