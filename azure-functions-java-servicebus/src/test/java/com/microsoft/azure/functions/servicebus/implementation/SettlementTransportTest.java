package com.microsoft.azure.functions.servicebus.implementation;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCalls;
import org.apache.qpid.proton.codec.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SettlementTransportTest {
    private final BlockingQueue<Invocation> invocations = new LinkedBlockingQueue<>();
    private Server server;
    private SettlementTransport transport;

    @BeforeEach
    void startServer() throws IOException {
        ServerServiceDefinition.Builder service = ServerServiceDefinition.builder("Settlement");
        for (String operation : new String[] {"Complete", "Abandon", "Deadletter", "Defer", "RenewMessageLock"}) {
            MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("Settlement/" + operation)
                    .setRequestMarshaller(new IndependentMarshaller())
                    .setResponseMarshaller(new IndependentMarshaller())
                    .build();
            service.addMethod(method, ServerCalls.asyncUnaryCall((request, response) -> {
                invocations.add(new Invocation(operation, request,
                        Context.current().getDeadline().timeRemaining(TimeUnit.MILLISECONDS)));
                try {
                    if ("fail".equals(readFields(request).get(1))) {
                        response.onError(Status.PERMISSION_DENIED.withDescription("host rejected settlement")
                                .asRuntimeException());
                        return;
                    }
                } catch (IOException exception) {
                    response.onError(exception);
                    return;
                }
                response.onNext(Empty.getDefaultInstance().toByteArray());
                response.onCompleted();
            }));
        }
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(service.build()).build().start();
        transport = SettlementTransport.forEndpoint(URI.create("http://127.0.0.1:" + server.getPort()));
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        if (server != null) {
            server.shutdownNow();
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void completeAndRenewUseUnpackagedMethodsAndOnlyLockToken() throws Exception {
        for (String operation : new String[] {"Complete", "RenewMessageLock"}) {
            transport.settle(operation, "token-\u00e9", Collections.singletonMap("ignored", true), "ignored", "ignored");
            Invocation invocation = takeInvocation(operation);
            assertEquals(Collections.singletonMap(1, "token-\u00e9"), readFields(invocation.request));
            assertTrue(invocation.deadlineMillis > 0 && invocation.deadlineMillis <= 30000);
        }
    }

    @Test
    void abandonAndDeferEncodeTypedAmqpPropertyMaps() throws Exception {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", "value");
        properties.put("count", 42);
        properties.put("large", 1234567890123L);
        properties.put("enabled", true);
        properties.put("ratio", 1.5d);
        properties.put("nothing", null);
        for (String operation : new String[] {"Abandon", "Defer"}) {
            transport.settle(operation, "lock", properties, null, null);
            Map<Integer, Object> fields = readFields(takeInvocation(operation).request);
            assertEquals(2, fields.size());
            assertEquals("lock", fields.get(1));
            assertAmqpMapEquals(properties, (byte[]) fields.get(2));
        }
    }

    @Test
    void deadletterUsesAmqpAndNestedStringValueFields() throws Exception {
        Map<String, Object> properties = Collections.singletonMap("attempt", 3);
        transport.settle("Deadletter", "lock", properties, "reason", "description");
        Map<Integer, Object> fields = readFields(takeInvocation("Deadletter").request);
        assertEquals(4, fields.size());
        assertEquals("lock", fields.get(1));
        assertAmqpMapEquals(properties, (byte[]) fields.get(2));
        assertEquals("reason", StringValue.parseFrom((byte[]) fields.get(3)).getValue());
        assertEquals("description", StringValue.parseFrom((byte[]) fields.get(4)).getValue());
    }

    @Test
    void deadletterPreservesAbsentVersusPresentEmptyWrappers() throws Exception {
        transport.settle("Deadletter", "lock", null, null, "");
        Map<Integer, Object> fields = readFields(takeInvocation("Deadletter").request);
        assertFalse(fields.containsKey(2));
        assertFalse(fields.containsKey(3));
        assertTrue(fields.containsKey(4));
        assertEquals("", StringValue.parseFrom((byte[]) fields.get(4)).getValue());
    }

    @Test
    void nullPropertiesAreAbsentAndEmptyPropertiesAreAbsentOrAmqpMap() throws Exception {
        transport.settle("Abandon", "lock", null, null, null);
        assertFalse(readFields(takeInvocation("Abandon").request).containsKey(2));
        transport.settle("Defer", "lock", Collections.emptyMap(), null, null);
        byte[] encoded = (byte[]) readFields(takeInvocation("Defer").request).get(2);
        if (encoded != null) {
            assertAmqpMapEquals(Collections.emptyMap(), encoded);
        }
    }

    @Test
    void hostFailuresPropagateUnchanged() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> transport.settle("Complete", "fail", null, null, null));
        assertEquals(Status.Code.PERMISSION_DENIED, error.getStatus().getCode());
        assertEquals("host rejected settlement", error.getStatus().getDescription());
    }

    @Test
    void unsupportedOperationDoesNotReachHost() {
        assertThrows(IllegalArgumentException.class,
                () -> transport.settle("DeadLetter", "lock", null, null, null));
        assertTrue(invocations.isEmpty());
    }

    @Test
    void endpointInstancesAreProcessSharedAndCanonicalized() {
        assertSame(transport, SettlementTransport.forEndpoint(URI.create("http://127.0.0.1:" + server.getPort())));
        assertSame(transport, SettlementTransport.forEndpoint(URI.create("http://127.0.0.1:" + server.getPort() + "/")));
        assertSame(SettlementTransport.forEndpoint(URI.create("HTTP://LOCALHOST:80")),
                SettlementTransport.forEndpoint(URI.create("http://localhost")));
        assertSame(SettlementTransport.forEndpoint(URI.create("https://example.com:443")),
                SettlementTransport.forEndpoint(URI.create("https://EXAMPLE.com")));
    }

    @Test
    void concurrentFirstUseSharesOneTransport() throws Exception {
        URI endpoint = URI.create("http://localhost:" + server.getPort());
        ExecutorService callers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SettlementTransport>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(callers.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return SettlementTransport.forEndpoint(endpoint);
                }));
            }
            start.countDown();
            SettlementTransport first = results.get(0).get(5, TimeUnit.SECONDS);
            for (Future<SettlementTransport> result : results) {
                assertSame(first, result.get(5, TimeUnit.SECONDS));
            }
        } finally {
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void onlyLiteralLoopbackOrLocalhostMayUsePlaintext() {
        for (String endpoint : new String[] {"http://localhost:1234", "http://127.0.0.1:1234",
                "http://127.255.255.254:1234", "http://[::1]:1234", "http://[0:0:0:0:0:0:0:1]:1234",
                "http://[0000:0000:0000:0000:0000:0000:0000:0001]:1234", "http://[0:0::0001]:1234",
                "https://remote.example:1234", "https://192.0.2.1:1234"}) {
            assertDoesNotThrow(() -> SettlementTransport.forEndpoint(URI.create(endpoint)), endpoint);
        }
        for (String endpoint : new String[] {"http://example.com:1234", "http://192.0.2.1:1234",
                "http://0.0.0.0:1234", "http://[::]:1234", "http://[::2]:1234",
                "http://[0:0:0:0:0:0:1:1]:1234", "http://[::ffff:127.0.0.1]:1234",
                "http://localhost.example:1234", "http://127.0.0.1.nip.io:1234",
                "http://localhost.:1234", "http://2130706433:1234", "http://127.1:1234"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SettlementTransport.forEndpoint(URI.create(endpoint)), endpoint);
        }
    }

    @Test
    void malformedOrDecoratedEndpointsAreRejected() {
        for (String endpoint : new String[] {"ftp://localhost:1234", "localhost:1234",
                "http://user@localhost:1234", "https://user:password@example.com:443",
                "http://localhost:1234/path", "http://localhost:1234?query",
                "http://localhost:1234?", "http://localhost:1234#fragment", "http://localhost:1234#",
                "http://localhost:0", "http://localhost:65536", "http://localhost:-1",
                "http://localhost:", "http://localhost:notaport", "http:///missing"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SettlementTransport.forEndpoint(URI.create(endpoint)), endpoint);
        }
    }

    private Invocation takeInvocation(String operation) throws InterruptedException {
        Invocation invocation = invocations.poll(5, TimeUnit.SECONDS);
        assertNotNull(invocation);
        assertEquals(operation, invocation.operation);
        return invocation;
    }

    private static Map<Integer, Object> readFields(byte[] request) throws IOException {
        CodedInputStream input = CodedInputStream.newInstance(request);
        Map<Integer, Object> fields = new LinkedHashMap<>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            assertEquals(2, tag & 7, "Expected a length-delimited protobuf field");
            int field = tag >>> 3;
            assertFalse(fields.containsKey(field), "Duplicate protobuf field");
            fields.put(field, field == 1 ? input.readStringRequireUtf8() : input.readByteArray());
        }
        return fields;
    }

    private static void assertAmqpMapEquals(Map<String, Object> expected, byte[] encoded) {
        assertNotNull(encoded);
        int constructor = encoded[0] & 0xff;
        assertTrue(constructor == 0xc1 || constructor == 0xd1, "Expected an AMQP map constructor, not JSON");
        Data decoded = Data.Factory.create();
        assertEquals(encoded.length, decoded.decode(ByteBuffer.wrap(encoded)));
        decoded.rewind();
        assertNotNull(decoded.next());
        assertEquals(expected, decoded.getObject());
    }

    private static final class Invocation {
        private final String operation;
        private final byte[] request;
        private final long deadlineMillis;

        private Invocation(String operation, byte[] request, long deadlineMillis) {
            this.operation = operation;
            this.request = request;
            this.deadlineMillis = deadlineMillis;
        }
    }

    private static final class IndependentMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream input) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            try {
                int length;
                while ((length = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, length);
                }
                return bytes.toByteArray();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
