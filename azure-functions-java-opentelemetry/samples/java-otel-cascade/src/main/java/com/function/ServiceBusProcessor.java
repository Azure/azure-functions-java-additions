package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;



/**
 * Azure Functions Service Bus Trigger.
 */
public class ServiceBusProcessor {
    
    /**
     * This function is triggered by messages from a Service Bus queue.
     */
    @FunctionName("ServiceBusProcessor")
    public void run(
        @ServiceBusQueueTrigger(
            name = "message",
            queueName = "testqueue",
            connection = "ServiceBusConnectionString")
            String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("ServiceBus trigger function processed a message: " + message);
        
        // Process the message - spans will be automatically created by the middleware
        try {
            processMessage(message, context);
            context.getLogger().info("Successfully processed Service Bus message");
        } catch (Exception e) {
            context.getLogger().severe("Error processing Service Bus message: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Process the incoming message.
     */
    private void processMessage(String message, ExecutionContext context) {
        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Log the processing
        context.getLogger().info("Processing message content: " + message);
        
        // You could add additional business logic here such as:
        // - Parsing the message content
        // - Calling other services
        // - Storing data in a database
        // - Sending notifications
    }
}