/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions.servicebus;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import java.util.Map;

/**
 * Settles Service Bus messages that the Functions host received and still holds the lock for.
 *
 * <p>Declare an unannotated parameter of this type on a function to receive an instance. Settlement uses the
 * lock token of the message, so pass messages that were delivered to your function. Turn off automatic
 * completion in the Service Bus extension settings when you settle messages yourself.</p>
 *
 * <p>Every method blocks until the host answers and throws {@link ServiceBusSettlementException} when the host
 * rejects the request, for example when the lock has expired or the message was already settled.</p>
 */
public interface ServiceBusMessageActions {
    /**
     * Completes the message. It is removed from the entity.
     *
     * @param message the received message
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void completeMessage(ServiceBusReceivedMessage message);

    /**
     * Abandons the message. It becomes available for delivery again.
     *
     * @param message the received message
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void abandonMessage(ServiceBusReceivedMessage message);

    /**
     * Abandons the message and modifies its application properties.
     *
     * @param message the received message
     * @param propertiesToModify application properties to set, or null for none
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void abandonMessage(ServiceBusReceivedMessage message, Map<String, Object> propertiesToModify);

    /**
     * Defers the message. It can only be received again by its sequence number.
     *
     * @param message the received message
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void deferMessage(ServiceBusReceivedMessage message);

    /**
     * Defers the message and modifies its application properties.
     *
     * @param message the received message
     * @param propertiesToModify application properties to set, or null for none
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void deferMessage(ServiceBusReceivedMessage message, Map<String, Object> propertiesToModify);

    /**
     * Moves the message to the dead-letter subqueue.
     *
     * @param message the received message
     * @param reason the dead-letter reason, or null for none
     * @param description the dead-letter error description, or null for none
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void deadLetterMessage(ServiceBusReceivedMessage message, String reason, String description);

    /**
     * Moves the message to the dead-letter subqueue and modifies its application properties.
     *
     * @param message the received message
     * @param reason the dead-letter reason, or null for none
     * @param description the dead-letter error description, or null for none
     * @param propertiesToModify application properties to set, or null for none
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void deadLetterMessage(ServiceBusReceivedMessage message, String reason, String description,
        Map<String, Object> propertiesToModify);

    /**
     * Renews the lock of the message. The host does not report the new expiration time.
     *
     * @param message the received message
     * @throws ServiceBusSettlementException if the host rejects the request
     */
    void renewMessageLock(ServiceBusReceivedMessage message);
}
