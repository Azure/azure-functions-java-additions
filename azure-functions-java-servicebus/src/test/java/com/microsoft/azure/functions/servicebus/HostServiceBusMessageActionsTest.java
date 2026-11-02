/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.google.protobuf.Empty;
import com.microsoft.azure.functions.servicebus.implementation.grpc.AbandonRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.CompleteRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.DeadletterRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.DeferRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.RenewMessageLockRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.SettlementGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.qpid.proton.codec.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostServiceBusMessageActionsTest {
    private static final String TOKEN = "00112233-4455-6677-8899-aabbccddeeff";
    private static final byte[] AMQP_BODY = ServiceBusHostProviderTest.hex("005375a003616263");

    private final AtomicReference<Object> received = new AtomicReference<>();
    private final AtomicReference<Status> failure = new AtomicReference<>();
    private Server server;
    private ServiceBusMessageActions actions;
    private ServiceBusReceivedMessage message;

    @BeforeEach
    void startHost() throws Exception {
        server = ServerBuilder.forPort(0).addService(new Host()).build().start();
        actions = HostServiceBusMessageActions.forEndpoint(URI.create("http://127.0.0.1:" + server.getPort()));
        message = ServiceBusReceivedMessage.fromAmqpMessage(BinaryData.fromBytes(AMQP_BODY), TOKEN);
    }

    @AfterEach
    void stopHost() throws Exception {
        server.shutdownNow();
        assertTrue(server.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void completeSendsTheLockTokenOfTheMessage() {
        actions.completeMessage(message);
        assertEquals(TOKEN, ((CompleteRequest) received.get()).getLocktoken());
    }

    @Test
    void renewSendsTheLockTokenOfTheMessage() {
        actions.renewMessageLock(message);
        assertEquals(TOKEN, ((RenewMessageLockRequest) received.get()).getLocktoken());
    }

    @Test
    void abandonAndDeferSendNoPropertiesWhenNoneAreGiven() {
        actions.abandonMessage(message);
        AbandonRequest abandon = (AbandonRequest) received.get();
        assertTrue(abandon.getPropertiesToModify().isEmpty());
        // Only the lock token field is on the wire, which is a tag, a length and the token.
        assertEquals(2 + TOKEN.length(), abandon.toByteArray().length);

        actions.deferMessage(message, Collections.<String, Object>emptyMap());
        assertEquals(2 + TOKEN.length(), ((DeferRequest) received.get()).toByteArray().length);
    }

    @Test
    void abandonAndDeferSendPropertiesAsAnAmqpMap() {
        actions.abandonMessage(message, Collections.<String, Object>singletonMap("retry", "later"));
        assertEquals("later", amqpMap(((AbandonRequest) received.get()).getPropertiesToModify().toByteArray())
            .get("retry"));

        actions.deferMessage(message, Collections.<String, Object>singletonMap("count", 3));
        assertEquals(3, amqpMap(((DeferRequest) received.get()).getPropertiesToModify().toByteArray()).get("count"));
    }

    @Test
    void deadLetterSendsReasonAndDescriptionOnlyWhenGiven() {
        actions.deadLetterMessage(message, "BadData", "The order has no id");
        DeadletterRequest full = (DeadletterRequest) received.get();
        assertEquals("BadData", full.getDeadletterReason().getValue());
        assertEquals("The order has no id", full.getDeadletterErrorDescription().getValue());

        actions.deadLetterMessage(message, null, null,
            Collections.<String, Object>singletonMap("failed", true));
        DeadletterRequest bare = (DeadletterRequest) received.get();
        assertFalse(bare.hasDeadletterReason());
        assertFalse(bare.hasDeadletterErrorDescription());
        assertEquals(true, amqpMap(bare.getPropertiesToModify().toByteArray()).get("failed"));
    }

    @Test
    void hostRejectionsSurfaceAsOneExceptionWithoutTheHostStackTrace() {
        failure.set(Status.FAILED_PRECONDITION.withDescription("Lock is not held\n   at Host.Settle(...)"));
        ServiceBusSettlementException error =
            assertThrows(ServiceBusSettlementException.class, () -> actions.completeMessage(message));
        assertEquals("Complete", error.getOperation());
        assertEquals("FAILED_PRECONDITION", error.getStatusCode());
        assertNull(error.getCause());
        assertTrue(error.getMessage().contains("Lock is not held"));
        assertFalse(error.getMessage().contains("Host.Settle"));
    }

    @Test
    void messagesWithoutALockTokenAreRejectedBeforeCallingTheHost() {
        ServiceBusReceivedMessage unlocked =
            ServiceBusReceivedMessage.fromAmqpMessage(BinaryData.fromBytes(AMQP_BODY), null);
        assertThrows(IllegalArgumentException.class, () -> actions.completeMessage(unlocked));
        assertNull(received.get());
    }

    @Test
    void plaintextIsOnlyAllowedOnLoopback() {
        for (String allowed : new String[] {"http://127.0.0.1:50051", "http://localhost:50051",
            "http://[::1]:50051", "https://functions.example.com"}) {
            HostServiceBusMessageActions.forEndpoint(URI.create(allowed));
        }
        for (String rejected : new String[] {"http://functions.example.com", "http://127.0.0.1.example.com",
            "ftp://127.0.0.1", "127.0.0.1:50051"}) {
            assertThrows(IllegalArgumentException.class,
                () -> HostServiceBusMessageActions.forEndpoint(URI.create(rejected)), rejected);
        }
    }

    private static Map<?, ?> amqpMap(byte[] bytes) {
        Data data = Data.Factory.create();
        data.decode(ByteBuffer.wrap(bytes));
        data.next();
        return (Map<?, ?>) data.getObject();
    }

    private final class Host extends SettlementGrpc.SettlementImplBase {
        @Override
        public void complete(CompleteRequest request, StreamObserver<Empty> response) {
            respond(request, response);
        }

        @Override
        public void abandon(AbandonRequest request, StreamObserver<Empty> response) {
            respond(request, response);
        }

        @Override
        public void defer(DeferRequest request, StreamObserver<Empty> response) {
            respond(request, response);
        }

        @Override
        public void deadletter(DeadletterRequest request, StreamObserver<Empty> response) {
            respond(request, response);
        }

        @Override
        public void renewMessageLock(RenewMessageLockRequest request, StreamObserver<Empty> response) {
            respond(request, response);
        }

        private void respond(Object request, StreamObserver<Empty> response) {
            received.set(request);
            if (failure.get() != null) {
                response.onError(failure.get().asRuntimeException());
                return;
            }
            response.onNext(Empty.getDefaultInstance());
            response.onCompleted();
        }
    }
}
