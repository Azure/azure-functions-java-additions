/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.servicebus.implementation.SettlementTransport;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Synchronous settlement of messages held by the Functions host.
 * Instances are injected using {@code @BindingName} and expire with the invocation.
 * Disable automatic completion on the Service Bus trigger when settling manually.
 */
public final class ServiceBusMessageActions {
    private final SdkInvocationContext context;
    private final Map<ServiceBusReceivedMessage, String> messages;
    private final Set<String> settledTokens = new HashSet<>();
    private final SettlementTransport transport;

    ServiceBusMessageActions(SdkInvocationContext context, Map<ServiceBusReceivedMessage, String> messages) {
        this.context = Objects.requireNonNull(context, "context");
        if (context.getHostEndpoint() == null) {
            throw new IllegalStateException("Service Bus actions require the Functions host endpoint");
        }
        this.messages = new IdentityHashMap<>(messages);
        this.transport = SettlementTransport.forEndpoint(context.getHostEndpoint());
    }

    public void completeMessage(ServiceBusReceivedMessage message) {
        settle("Complete", message, Collections.emptyMap(), null, null);
    }

    public void abandonMessage(ServiceBusReceivedMessage message) {
        abandonMessage(message, Collections.emptyMap());
    }

    public void abandonMessage(ServiceBusReceivedMessage message, Map<String, Object> propertiesToModify) {
        settle("Abandon", message, propertiesToModify, null, null);
    }

    public void deferMessage(ServiceBusReceivedMessage message) {
        deferMessage(message, Collections.emptyMap());
    }

    public void deferMessage(ServiceBusReceivedMessage message, Map<String, Object> propertiesToModify) {
        settle("Defer", message, propertiesToModify, null, null);
    }

    public void deadLetterMessage(ServiceBusReceivedMessage message, String reason, String description) {
        deadLetterMessage(message, reason, description, Collections.emptyMap());
    }

    public void deadLetterMessage(ServiceBusReceivedMessage message, String reason, String description,
        Map<String, Object> propertiesToModify) {
        settle("Deadletter", message, propertiesToModify, reason, description);
    }

    /**
     * Renews the host-held lock. The host returns no new expiration timestamp.
     *
     * @param message a message from this invocation
     */
    public void renewMessageLock(ServiceBusReceivedMessage message) {
        settle("RenewMessageLock", message, Collections.emptyMap(), null, null);
    }

    private synchronized void settle(String operation, ServiceBusReceivedMessage message,
        Map<String, Object> properties, String reason, String description) {
        if (!context.isActive()) {
            throw new IllegalStateException("Service Bus invocation has ended");
        }
        String token = messages.get(Objects.requireNonNull(message, "message"));
        if (token == null) {
            throw new IllegalArgumentException("Message does not belong to this invocation and binding");
        }
        if (settledTokens.contains(token)) {
            throw new IllegalStateException("Message has already been settled");
        }
        transport.settle(operation, token, Objects.requireNonNull(properties, "propertiesToModify"), reason, description);
        if (!"RenewMessageLock".equals(operation)) {
            settledTokens.add(token);
        }
    }
}
