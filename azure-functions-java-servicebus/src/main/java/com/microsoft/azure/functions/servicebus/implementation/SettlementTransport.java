/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus.implementation;

import com.google.protobuf.CodedOutputStream;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import org.apache.qpid.proton.codec.Data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Process-shared gRPC connections to the host's Service Bus settlement service.
 */
public final class SettlementTransport {
    private static final int MAX_ENDPOINTS = 128;
    private static final ConcurrentMap<String, SettlementTransport> TRANSPORTS = new ConcurrentHashMap<>();
    private static final MethodDescriptor.Marshaller<byte[]> RAW_BYTES = new MethodDescriptor.Marshaller<byte[]>() {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            try {
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
                return output.toByteArray();
            } catch (IOException exception) {
                throw Status.INTERNAL.withDescription("Cannot read settlement response")
                        .withCause(exception).asRuntimeException();
            }
        }
    };

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (TRANSPORTS) {
                TRANSPORTS.values().forEach(transport -> transport.channel.shutdown());
            }
        }, "servicebus-settlement-shutdown"));
    }

    private final ManagedChannel channel;

    private SettlementTransport(String host, int port, boolean secure) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
        channel = (secure ? builder.useTransportSecurity() : builder.usePlaintext()).build();
    }

    /**
     * Gets the shared transport for a validated HTTP or HTTPS endpoint.
     *
     * @param endpoint host settlement endpoint, with an optional root slash and no query or fragment
     * @return the process-shared transport
     */
    public static SettlementTransport forEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        String scheme = endpoint.getScheme();
        boolean secure = "https".equalsIgnoreCase(scheme);
        if ((!secure && !"http".equalsIgnoreCase(scheme)) || endpoint.isOpaque()
                || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                || (endpoint.getRawPath() != null && !endpoint.getRawPath().isEmpty()
                    && !"/".equals(endpoint.getRawPath()))) {
            throw new IllegalArgumentException("Settlement endpoint must be an HTTP(S) authority without a path");
        }
        String host = endpoint.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        int port = endpoint.getPort();
        if (port == 0 || port > 65535 || endpoint.getRawAuthority().endsWith(":")) {
            throw new IllegalArgumentException("Settlement endpoint has an invalid port");
        }
        if (!secure && !isLiteralLoopback(host)) {
            throw new IllegalArgumentException("Plaintext settlement endpoints must use localhost or a loopback literal");
        }
        if (port == -1) {
            port = secure ? 443 : 80;
        }
        String key = (secure ? "https://" : "http://") + "[" + host + "]:" + port;
        SettlementTransport existing = TRANSPORTS.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (TRANSPORTS) {
            existing = TRANSPORTS.get(key);
            if (existing == null) {
                if (TRANSPORTS.size() >= MAX_ENDPOINTS) {
                    throw new IllegalStateException("Settlement endpoint connection limit reached");
                }
                existing = new SettlementTransport(host, port, secure);
                TRANSPORTS.put(key, existing);
            }
            return existing;
        }
    }

    private static boolean isLiteralLoopback(String host) {
        if ("localhost".equals(host)) {
            return true;
        }
        // URI has already validated the IPv6 syntax. Never resolve a hostname to decide plaintext eligibility.
        if (host.indexOf(':') >= 0) {
            return host.matches("[0:]*:0{0,3}1");
        }
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        for (String octet : octets) {
            if (!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * Invokes one of the host's unary settlement methods.
     *
     * @param operation Complete, Abandon, Deadletter, Defer, or RenewMessageLock
     * @param lockToken message lock token
     * @param properties application properties to modify, or null
     * @param reason optional dead-letter reason
     * @param description optional dead-letter description
     */
    public void settle(String operation, String lockToken, Map<String, Object> properties,
                       String reason, String description) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(lockToken, "lockToken");
        boolean hasProperties;
        switch (operation) {
            case "Complete":
            case "RenewMessageLock":
                hasProperties = false;
                break;
            case "Abandon":
            case "Deadletter":
            case "Defer":
                hasProperties = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown settlement operation: " + operation);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream request = CodedOutputStream.newInstance(bytes);
        try {
            request.writeString(1, lockToken);
            if (hasProperties && properties != null) {
                request.writeByteArray(2, encodeProperties(properties));
            }
            if ("Deadletter".equals(operation)) {
                writeStringWrapper(request, 3, reason);
                writeStringWrapper(request, 4, description);
            }
            request.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode settlement request", exception);
        }
        MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("Settlement", operation))
                .setRequestMarshaller(RAW_BYTES)
                .setResponseMarshaller(RAW_BYTES)
                .build();
        ClientCalls.blockingUnaryCall(channel, method,
                CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS), bytes.toByteArray());
    }

    private static byte[] encodeProperties(Map<String, Object> properties) {
        Data data = Data.Factory.create();
        data.putObject(properties);
        ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(data.encodedSize()));
        data.encode(buffer);
        return buffer.array();
    }

    private static void writeStringWrapper(CodedOutputStream request, int field, String value) throws IOException {
        if (value != null) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CodedOutputStream wrapper = CodedOutputStream.newInstance(bytes);
            if (!value.isEmpty()) {
                wrapper.writeString(1, value);
            }
            wrapper.flush();
            request.writeByteArray(field, bytes.toByteArray());
        }
    }
}
