/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hydrates native messages without opening a broker connection.
 */
public final class ServiceBusBindingProvider
    implements com.microsoft.azure.functions.sdktype.servicebus.ServiceBusBindingProvider {
    private static final Map<SdkInvocationContext, Scope> SCOPES = new IdentityHashMap<>();

    @Override
    public Object createInstance(Parameter parameter, SdkInvocationContext context) throws Exception {
        if (!context.isActive()) {
            throw new IllegalStateException("Service Bus invocation has ended");
        }
        String binding = bindingName(parameter);
        List<ServiceBusReceivedMessage> messages = messages(context, binding);
        if (parameter.getType() == ServiceBusMessageActions.class) {
            return actions(context, binding, messages);
        }
        if (parameter.getType().isArray()) {
            return messages.toArray(new ServiceBusReceivedMessage[0]);
        }
        if (parameter.getType() == List.class) {
            return messages;
        }
        if (messages.size() != 1) {
            throw new IllegalArgumentException("Single message binding requires exactly one payload");
        }
        return messages.get(0);
    }

    private static List<ServiceBusReceivedMessage> messages(SdkInvocationContext context,
        String binding) {
        Scope scope = scope(context);
        synchronized (scope) {
            if (!context.isActive()) {
                throw new IllegalStateException("Service Bus invocation has ended");
            }
            List<ServiceBusReceivedMessage> result = scope.messages.get(binding);
            if (result == null) {
                List<ModelBindingData> payloads = context.getModelBindingData(binding);
                if (payloads.isEmpty()) {
                    throw new IllegalArgumentException("Service Bus SDK binding '" + binding
                        + "' requires deferred ModelBindingData. The host supplied an incompatible payload. "
                        + "Check host and extension support and binding expressions that disable deferred binding.");
                }
                List<ServiceBusReceivedMessage> decoded = new ArrayList<>();
                for (ModelBindingData payload : payloads) {
                    decoded.add(decode(payload));
                }
                result = Collections.unmodifiableList(decoded);
                scope.messages.put(binding, result);
            }
            return result;
        }
    }

    static ServiceBusReceivedMessage decode(ModelBindingData payload) {
        if (!"1.0".equals(payload.getVersion())
            || !"AzureServiceBusReceivedMessage".equals(payload.getSource())
            || !"application/octet-stream".equals(payload.getContentType())) {
            throw new IllegalArgumentException("Unsupported Service Bus model binding envelope");
        }
        byte[] content = payload.getContent();
        if (content.length <= 16) {
            throw new IllegalArgumentException("Service Bus payload requires a lock token and AMQP message");
        }
        byte[] token = Arrays.copyOf(content, 16);
        reverse(token, 0, 4);
        reverse(token, 4, 6);
        reverse(token, 6, 8);
        ByteBuffer guid = ByteBuffer.wrap(token);
        String lockToken = new UUID(guid.getLong(), guid.getLong()).toString();
        return ServiceBusReceivedMessage.fromAmqpMessage(
            BinaryData.fromBytes(Arrays.copyOfRange(content, 16, content.length)), lockToken);
    }

    private static void reverse(byte[] bytes, int start, int end) {
        for (int left = start, right = end - 1; left < right; left++, right--) {
            byte value = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = value;
        }
    }

    private static String bindingName(Parameter parameter) throws Exception {
        String result = null;
        for (Annotation annotation : parameter.getAnnotations()) {
            String type = annotation.annotationType().getName();
            String property;
            if ("com.microsoft.azure.functions.annotation.BindingName".equals(type)) {
                property = "value";
            } else if (parameter.getType() != ServiceBusMessageActions.class
                && ("com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger".equals(type)
                    || "com.microsoft.azure.functions.annotation.ServiceBusTopicTrigger".equals(type))) {
                property = "name";
            } else {
                continue;
            }
            String value = (String) annotation.annotationType().getMethod(property).invoke(annotation);
            if (value == null || value.isEmpty() || (result != null && !result.equals(value))) {
                throw new IllegalArgumentException("Service Bus parameter has an invalid binding association");
            }
            result = value;
        }
        if (result == null) {
            throw new IllegalArgumentException("Service Bus parameter requires a trigger or @BindingName association");
        }
        return result;
    }

    private static Scope scope(SdkInvocationContext context) {
        synchronized (SCOPES) {
            Scope scope = SCOPES.get(context);
            if (scope == null) {
                context.registerCloseable(() -> closeScope(context));
                scope = new Scope();
                SCOPES.put(context, scope);
            }
            return scope;
        }
    }

    private static void closeScope(SdkInvocationContext context) {
        Scope scope;
        synchronized (SCOPES) {
            scope = SCOPES.remove(context);
        }
        if (scope != null) {
            synchronized (scope) {
                scope.messages.clear();
                scope.actions.clear();
            }
        }
    }

    private static ServiceBusMessageActions actions(SdkInvocationContext context,
        String binding, List<ServiceBusReceivedMessage> messages) {
        Scope scope;
        synchronized (SCOPES) {
            scope = SCOPES.get(context);
        }
        if (scope == null) {
            throw new IllegalStateException("Service Bus invocation scope has been closed");
        }
        synchronized (scope) {
            if (!context.isActive()) {
                throw new IllegalStateException("Service Bus invocation has ended");
            }
            ServiceBusMessageActions result = scope.actions.get(binding);
            if (result == null) {
                Map<ServiceBusReceivedMessage, String> identities = new IdentityHashMap<>();
                for (ServiceBusReceivedMessage message : messages) {
                    identities.put(message, message.getLockToken());
                }
                result = new ServiceBusMessageActions(context, identities);
                scope.actions.put(binding, result);
            }
            return result;
        }
    }

    private static final class Scope {
        private final Map<String, List<ServiceBusReceivedMessage>> messages = new HashMap<>();
        private final Map<String, ServiceBusMessageActions> actions = new HashMap<>();
    }
}
