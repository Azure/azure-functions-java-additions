package com.function;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.opentelemetry.FunctionsOpenTelemetry;
import com.microsoft.azure.functions.*;
import java.time.Duration;

/**
 * Azure Functions with HTTP Trigger.
 */
public class HttpTriggerCaller {
    /**
     * This function listens at endpoint "/api/HttpTriggerCaller". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpTriggerCaller
     * 2. curl {your host}/api/HttpTriggerCaller?name=HTTP%20Query
     */
    @FunctionName("HttpTriggerCaller")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        var log = context.getLogger();
        
        return runImpl(request, context, log);
    }
    
    private HttpResponseMessage runImpl(HttpRequestMessage<Optional<String>> request, ExecutionContext context, java.util.logging.Logger log) {
        Map<String, String> azureContext = FunctionsOpenTelemetry.getCurrentAzureContext(context.getFunctionName(), context.getInvocationId());
        log.info("Java HTTP trigger processed a request: A.");
        String name = request.getQueryParameters().getOrDefault("name", "world");

        // Base URL for B (override in Azure with env var if you like)
        String base = Optional.ofNullable(System.getenv("B_URL"))
                .orElse("http://localhost:7071");

        String url = base + "/api/httpexample?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);

        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            var httpReq = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            log.info("Making HTTP call to: " + url);
            var resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
            log.info("Received response with status: " + resp.statusCode());

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "text/plain")
                    .body("A → B (" + url + ")\nB responded: " + resp.statusCode())
                    .build();

        } catch (Exception e) {
            log.severe("Call to B failed: " + e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("A failed to call B: " + e.getMessage())
                    .build();
        }
    }
}
