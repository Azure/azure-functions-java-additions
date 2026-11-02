/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.microsoft.azure.functions.servicebus.implementation.grpc.AbandonRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.CompleteRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.DeadletterRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.DeferRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.RenewMessageLockRequest;
import com.microsoft.azure.functions.servicebus.implementation.grpc.SettlementGrpc;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.qpid.proton.codec.Data;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Settles messages through the Settlement gRPC service of the Functions host.
 */
final class HostServiceBusMessageActions implements ServiceBusMessageActions {
    private static final long DEADLINE_SECONDS = 30;

    private final SettlementGrpc.SettlementBlockingStub stub;

    HostServiceBusMessageActions(final Channel channel) {
        stub = SettlementGrpc.newBlockingStub(channel);
    }

    /**
     * Connects to the host. Plaintext is only allowed on loopback, anything else needs https.
     */
    static HostServiceBusMessageActions forEndpoint(final URI endpoint) {
        final String scheme = endpoint.getScheme();
        final boolean secure = "https".equalsIgnoreCase(scheme);
        if (endpoint.getHost() == null || !(secure || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Unsupported Functions host endpoint: " + endpoint);
        }
        final String host = endpoint.getHost().replaceAll("^\\[|\\]$", "").toLowerCase(Locale.ROOT);
        if (!secure && !isLoopback(host)) {
            throw new IllegalArgumentException("Plaintext Functions host endpoints must be loopback: " + endpoint);
        }
        final int port = endpoint.getPort() >= 0 ? endpoint.getPort() : (secure ? 443 : 80);
        final ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (secure) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        return new HostServiceBusMessageActions(builder.build());
    }

    private static boolean isLoopback(final String host) {
        return "localhost".equals(host) || "::1".equals(host) || host.matches("127(\\.\\d{1,3}){3}");
    }

    @Override
    public void completeMessage(final ServiceBusReceivedMessage message) {
        final CompleteRequest request = CompleteRequest.newBuilder().setLocktoken(lockToken(message)).build();
        call("Complete", () -> stub().complete(request));
    }

    @Override
    public void abandonMessage(final ServiceBusReceivedMessage message) {
        abandonMessage(message, null);
    }

    @Override
    public void abandonMessage(final ServiceBusReceivedMessage message,
        final Map<String, Object> propertiesToModify) {
        final AbandonRequest request = AbandonRequest.newBuilder().setLocktoken(lockToken(message))
            .setPropertiesToModify(encode(propertiesToModify)).build();
        call("Abandon", () -> stub().abandon(request));
    }

    @Override
    public void deferMessage(final ServiceBusReceivedMessage message) {
        deferMessage(message, null);
    }

    @Override
    public void deferMessage(final ServiceBusReceivedMessage message,
        final Map<String, Object> propertiesToModify) {
        final DeferRequest request = DeferRequest.newBuilder().setLocktoken(lockToken(message))
            .setPropertiesToModify(encode(propertiesToModify)).build();
        call("Defer", () -> stub().defer(request));
    }

    @Override
    public void deadLetterMessage(final ServiceBusReceivedMessage message, final String reason,
        final String description) {
        deadLetterMessage(message, reason, description, null);
    }

    @Override
    public void deadLetterMessage(final ServiceBusReceivedMessage message, final String reason,
        final String description, final Map<String, Object> propertiesToModify) {
        final DeadletterRequest.Builder builder = DeadletterRequest.newBuilder().setLocktoken(lockToken(message))
            .setPropertiesToModify(encode(propertiesToModify));
        if (reason != null) {
            builder.setDeadletterReason(StringValue.of(reason));
        }
        if (description != null) {
            builder.setDeadletterErrorDescription(StringValue.of(description));
        }
        final DeadletterRequest request = builder.build();
        call("Deadletter", () -> stub().deadletter(request));
    }

    @Override
    public void renewMessageLock(final ServiceBusReceivedMessage message) {
        final RenewMessageLockRequest request = RenewMessageLockRequest.newBuilder()
            .setLocktoken(lockToken(message)).build();
        call("RenewMessageLock", () -> stub().renewMessageLock(request));
    }

    private SettlementGrpc.SettlementBlockingStub stub() {
        return stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    private static void call(final String operation, final Runnable rpc) {
        try {
            rpc.run();
        } catch (StatusRuntimeException exception) {
            // The host puts a full server stack trace in the description of unexpected failures.
            String detail = exception.getStatus().getDescription();
            if (detail != null) {
                detail = detail.split("\\R", 2)[0].trim();
            }
            throw new ServiceBusSettlementException(operation, exception.getStatus().getCode().name(), detail);
        }
    }

    private static String lockToken(final ServiceBusReceivedMessage message) {
        final String token = Objects.requireNonNull(message, "message").getLockToken();
        if (token == null) {
            throw new IllegalArgumentException("The message has no lock token. Pass a message that was delivered "
                + "to the function.");
        }
        return token;
    }

    // The host reads this field as an AMQP encoded map. Empty bytes are not sent on the wire.
    private static ByteString encode(final Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return ByteString.EMPTY;
        }
        final Data data = Data.Factory.create();
        data.putObject(properties);
        final ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(data.encodedSize()));
        data.encode(buffer);
        return ByteString.copyFrom(buffer.array());
    }
}
