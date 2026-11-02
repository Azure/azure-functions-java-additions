/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.sdktype.SdkParameterAnalyzer;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceBusHostProviderTest {
    private static final String TOKEN = "00112233-4455-6677-8899-aabbccddeeff";
    // A .NET Guid.ToByteArray() followed by an AMQP data section that holds "abc".
    private static final String HOST_PAYLOAD = "33221100554477668899aabbccddeeff005375a003616263";

    private final ServiceBusHostProvider provider = new ServiceBusHostProvider();

    void function(@ServiceBusQueueTrigger(name = "message", queueName = "orders", connection = "Connection")
        ServiceBusReceivedMessage message,
        @ServiceBusQueueTrigger(name = "batch", queueName = "orders", connection = "Connection")
        ServiceBusReceivedMessage[] batch,
        @ServiceBusQueueTrigger(name = "list", queueName = "orders", connection = "Connection")
        List<ServiceBusReceivedMessage> list,
        ServiceBusMessageActions actions) { }

    @Test
    void decodesTheGuidAndAmqpBytesTheHostSends() {
        ServiceBusReceivedMessage message = ServiceBusHostProvider.decode(envelope(hex(HOST_PAYLOAD)));
        assertEquals(TOKEN, message.getLockToken());
        assertArrayEquals(new byte[] {'a', 'b', 'c'}, message.getBody().toBytes());
    }

    @Test
    void rejectsUnknownEnvelopesAndTruncatedPayloads() {
        byte[] payload = hex(HOST_PAYLOAD);
        assertThrows(IllegalArgumentException.class, () -> ServiceBusHostProvider.decode(
            new ModelBindingData("2.0", "AzureServiceBusReceivedMessage", "application/octet-stream", payload)));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusHostProvider.decode(
            new ModelBindingData("1.0", "Other", "application/octet-stream", payload)));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusHostProvider.decode(
            new ModelBindingData("1.0", "AzureServiceBusReceivedMessage", "application/json", payload)));
        assertThrows(IllegalArgumentException.class, () -> ServiceBusHostProvider.decode(envelope(new byte[16])));
    }

    @Test
    void buildsSingleArrayAndListParameters() throws Exception {
        byte[] second = hex(HOST_PAYLOAD);
        second[0] = 0x44;
        Context batch = new Context(null, envelope(hex(HOST_PAYLOAD)), envelope(second));

        ServiceBusReceivedMessage[] array =
            (ServiceBusReceivedMessage[]) provider.createInstance(parameter(1), batch);
        assertEquals(TOKEN, array[0].getLockToken());
        assertEquals("00112244-4455-6677-8899-aabbccddeeff", array[1].getLockToken());
        assertEquals(2, ((List<?>) provider.createInstance(parameter(2), batch)).size());

        Context single = new Context(null, envelope(hex(HOST_PAYLOAD)));
        assertEquals(TOKEN, ((ServiceBusReceivedMessage) provider.createInstance(parameter(0), single)).getLockToken());
        assertThrows(IllegalArgumentException.class, () -> provider.createInstance(parameter(0), batch));
    }

    @Test
    void failsClearlyWhenTheHostSendsNoDeferredPayload() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> provider.createInstance(parameter(0), new Context(null)));
        assertEquals(true, error.getMessage().contains("deferred"));
    }

    @Test
    void injectsOneActionsInstanceByTypeWithoutBindingData() throws Exception {
        Context context = new Context(URI.create("http://127.0.0.1:1"));
        Object first = provider.createInstance(parameter(3), context);
        assertInstanceOf(ServiceBusMessageActions.class, first);
        assertSame(first, provider.createInstance(parameter(3), context));
    }

    @Test
    void analyzerAndRegistryHydrateMessageAndUnannotatedActions() throws Exception {
        SdkParameterAnalyzer analyzer = new SdkParameterAnalyzer();
        List<SdkTypeMetaData> found = analyzer.analyze(method()).getSdkTypesMetaData();
        assertEquals(4, found.size());
        assertEquals(ServiceBusMessageActions.class.getName(), found.get(3).getFqcn());

        Context context = new Context(URI.create("http://127.0.0.1:1"), envelope(hex(HOST_PAYLOAD)));
        SdkTypeMetaData message = analyzer.getRegistry().createMetaData(found.get(0).getFqcn(), parameter(0));
        assertEquals(TOKEN, ((ServiceBusReceivedMessage) analyzer.getRegistry().createSdkType(message)
            .buildInstance(context)).getLockToken());
        SdkTypeMetaData actions = analyzer.getRegistry().createMetaData(found.get(3).getFqcn(), parameter(3));
        assertInstanceOf(ServiceBusMessageActions.class,
            analyzer.getRegistry().createSdkType(actions).buildInstance(context));
    }

    private static Parameter parameter(int index) throws Exception {
        return method().getParameters()[index];
    }

    private static Method method() throws Exception {
        return ServiceBusHostProviderTest.class.getDeclaredMethod("function", ServiceBusReceivedMessage.class,
            ServiceBusReceivedMessage[].class, List.class, ServiceBusMessageActions.class);
    }

    private static ModelBindingData envelope(byte[] content) {
        return new ModelBindingData("1.0", "AzureServiceBusReceivedMessage", "application/octet-stream", content);
    }

    static byte[] hex(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    private static final class Context implements SdkInvocationContext {
        private final URI endpoint;
        private final List<ModelBindingData> payloads;

        Context(URI endpoint, ModelBindingData... payloads) {
            this.endpoint = endpoint;
            this.payloads = payloads.length == 0 ? Collections.<ModelBindingData>emptyList() : Arrays.asList(payloads);
        }

        @Override
        public List<ModelBindingData> getModelBindingData() {
            return payloads;
        }

        @Override
        public URI getHostEndpoint() {
            return endpoint;
        }
    }
}
