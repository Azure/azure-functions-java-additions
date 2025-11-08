package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.ServiceBusQueueOutput;

import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class HttpExample {
    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            @ServiceBusQueueOutput(
                name = "message",
                queueName = "testqueue",
                connection = "ServiceBusConnectionString")
                OutputBinding<String> serviceBusMessage,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("This HTTP triggered function executed successfully. Pass a name in the query string or in the request body for a personalized response.")
                    .build();
        } else {
            String responseMessage = String.format("Hello, %s. This HTTP triggered function executed successfully.", name);
            
            // Send message to Service Bus queue
            String serviceBusMessageContent = String.format("User request from %s at %s", 
                name, 
                java.time.Instant.now().toString());
            serviceBusMessage.setValue(serviceBusMessageContent);
            
            context.getLogger().info("Processed request for: " + name);
            context.getLogger().info("Sent message to Service Bus: " + serviceBusMessageContent);
            
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(responseMessage)
                    .build();
        }
    }
}
