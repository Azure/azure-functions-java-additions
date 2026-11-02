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
import com.microsoft.azure.functions.sdktype.servicebus.ServiceBusBindingProvider;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Builds Service Bus messages from the payloads the host sends and hands out the settlement actions. It is
 * registered through {@link java.util.ServiceLoader} and is not meant to be called by applications.
 */
public final class ServiceBusHostProvider implements ServiceBusBindingProvider {
    private static final Object LOCK = new Object();
    private static volatile ServiceBusMessageActions actions;

    @Override
    public Object createInstance(Parameter parameter, SdkInvocationContext context) {
        final Class<?> type = parameter.getType();
        if (type == ServiceBusMessageActions.class) {
            return actions(context);
        }
        final List<ServiceBusReceivedMessage> messages = new ArrayList<>();
        for (final ModelBindingData payload : context.getModelBindingData()) {
            messages.add(decode(payload));
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Parameter '" + parameter.getName() + "' requires deferred "
                + "ModelBindingData. Check that the host supports Service Bus SDK types and that "
                + "supportsDeferredBinding is not disabled.");
        }
        if (type.isArray()) {
            return messages.toArray(new ServiceBusReceivedMessage[0]);
        }
        if (type == List.class) {
            return messages;
        }
        if (messages.size() != 1) {
            throw new IllegalArgumentException("Parameter '" + parameter.getName() + "' expects one message but the "
                + "host sent " + messages.size() + ". Use a List or an array to receive batches.");
        }
        return messages.get(0);
    }

    // One channel per process, because the host endpoint does not change.
    private static ServiceBusMessageActions actions(SdkInvocationContext context) {
        ServiceBusMessageActions result = actions;
        if (result == null) {
            synchronized (LOCK) {
                result = actions;
                if (result == null) {
                    if (context.getHostEndpoint() == null) {
                        throw new IllegalStateException("The Functions host endpoint is not available");
                    }
                    result = HostServiceBusMessageActions.forEndpoint(context.getHostEndpoint());
                    actions = result;
                }
            }
        }
        return result;
    }

    static ServiceBusReceivedMessage decode(ModelBindingData payload) {
        if (!"1.0".equals(payload.getVersion())
            || !"AzureServiceBusReceivedMessage".equals(payload.getSource())
            || !"application/octet-stream".equals(payload.getContentType())) {
            throw new IllegalArgumentException("Unsupported Service Bus model binding envelope");
        }
        final byte[] content = payload.getContent();
        if (content == null || content.length <= 16) {
            throw new IllegalArgumentException("Service Bus payload requires a lock token and AMQP message");
        }
        // The host writes the lock token as a .NET Guid, so the first three groups are little endian.
        final byte[] token = Arrays.copyOf(content, 16);
        reverse(token, 0, 4);
        reverse(token, 4, 6);
        reverse(token, 6, 8);
        final ByteBuffer guid = ByteBuffer.wrap(token);
        final String lockToken = new UUID(guid.getLong(), guid.getLong()).toString();
        return ServiceBusReceivedMessage.fromAmqpMessage(
            BinaryData.fromBytes(Arrays.copyOfRange(content, 16, content.length)), lockToken);
    }

    private static void reverse(byte[] bytes, int start, int end) {
        for (int left = start, right = end - 1; left < right; left++, right--) {
            final byte value = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = value;
        }
    }
}
