/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.sdktype.servicebus;

import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import java.lang.reflect.Parameter;

/**
 * Implemented by the optional Service Bus binding artifact in the application.
 */
public interface ServiceBusBindingProvider {
    Object createInstance(Parameter parameter, SdkInvocationContext context) throws Exception;
}
