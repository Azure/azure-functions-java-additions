/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.servicebus;

import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeFactory;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import java.lang.reflect.Parameter;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Keeps Azure SDK and transport dependencies out of the worker and build analyzer.
 */
public final class ServiceBusSdkTypeFactory implements SdkTypeFactory {
    public static final String MESSAGE = "com.azure.messaging.servicebus.ServiceBusReceivedMessage";
    public static final String ACTIONS = "com.microsoft.azure.functions.servicebus.ServiceBusMessageActions";

    @Override
    public SdkTypeMetaData createMetaData(String fqcn, Parameter parameter) {
        return new ServiceBusMetaData(fqcn, parameter);
    }

    @Override
    public SdkType<?> createSdkType(SdkTypeMetaData metadata) {
        if (!(metadata instanceof ServiceBusMetaData)) {
            throw new IllegalArgumentException("Expected ServiceBusMetaData");
        }
        return new ServiceBusSdkType((ServiceBusMetaData) metadata);
    }

    private static final class ServiceBusSdkType implements SdkType<ServiceBusMetaData> {
        private final ServiceBusMetaData metadata;

        private ServiceBusSdkType(ServiceBusMetaData metadata) {
            this.metadata = metadata;
        }

        @Override
        public ServiceBusMetaData getMetaData() {
            return metadata;
        }

        @Override
        public SdkTypeHydrator<ServiceBusMetaData> getHydrator() {
            return new ServiceBusHydrator();
        }
    }

    private static final class ServiceBusHydrator implements SdkTypeHydrator<ServiceBusMetaData> {
        @Override
        public Object createInstance(ServiceBusMetaData meta) {
            throw new IllegalStateException("Service Bus bindings require an invocation context");
        }

        @Override
        public Object createInstance(ServiceBusMetaData meta, SdkInvocationContext context) throws Exception {
            if (context == null || !context.isActive()) {
                throw new IllegalStateException("Service Bus binding invocation is not active");
            }
            final Iterator<ServiceBusBindingProvider> providers = ServiceLoader.load(
                ServiceBusBindingProvider.class, meta.getParam().getDeclaringExecutable()
                    .getDeclaringClass().getClassLoader()).iterator();
            if (!providers.hasNext()) {
                throw new IllegalStateException("Add azure-functions-java-servicebus to the application");
            }
            final ServiceBusBindingProvider provider = providers.next();
            if (providers.hasNext()) {
                throw new IllegalStateException("Multiple Service Bus binding providers found");
            }
            return provider.createInstance(meta.getParam(), context);
        }
    }
}
