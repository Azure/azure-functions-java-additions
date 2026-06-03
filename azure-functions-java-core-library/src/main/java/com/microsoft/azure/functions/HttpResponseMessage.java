/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An HttpResponseMessage instance is returned by Azure Functions methods that are triggered by an
 * {https://github.com/Azure/azure-functions-java-library/blob/dev/src/main/java/com/microsoft/azure/functions/annotation/HttpTrigger.java}.
 *
 * {https://github.com/Azure/azure-functions-java-library/blob/dev/src/main/java/com/microsoft/azure/functions/annotation/HttpTrigger.java}
 * @see HttpRequestMessage
 * @since 1.0.0
 */
public interface HttpResponseMessage {

    /**
     * Returns the HTTP status code set on the HttpResponseMessage instance.
     * 
     * @return the status code set on the HttpResponseMessage instance.
     */
    HttpStatusType getStatus();

    /**
     * Returns the HTTP status code set on the HttpResponseMessage instance.
     * 
     * @return the status code set on the HttpResponseMessage instance.
     */
    default int getStatusCode() {
        return getStatus().value();
    }

    /**
     * Returns a header value for the given key.
     * 
     * @param key The key for which the header value is sought.
     * @return Returns the value if the key has previously been added, or null if it has not.
     */
    String getHeader(String key);

    /**
     * Returns the body of the HTTP response.
     * 
     * @return the body of the HTTP response.
     */
    Object getBody();

    /**
     * A consumer that may throw {@link IOException}, used by
     * {@link Builder#bodyStream(IOConsumer)} for callback-driven response streaming.
     *
     * @param <T> the type of the input to the operation
     * @since 1.4.0
     */
    @FunctionalInterface
    interface IOConsumer<T> {
        /**
         * Performs this operation on the given argument.
         *
         * @param value the input argument
         * @throws IOException if an I/O error occurs
         */
        void accept(T value) throws IOException;
    }

    /**
     * A builder to create an instance of HttpResponseMessage 
     */
    public interface Builder {

        /**
         * Sets the status code to be used in the HttpResponseMessage object.
         * 
         * You can provide standard HTTP Status using enum values from {@link HttpStatus}, or you can
         * create a custom status code using {@link HttpStatusType#custom(int)}.
         * 
         * @param status An HTTP status code representing the outcome of the HTTP request.
         * @return this builder
         */
        Builder status(HttpStatusType status);

        /**
         * Adds a (key, value) header to the response.
         * 
         * @param key   The key of the header value.
         * @param value The value of the header value.
         * @return this builder
         */
        Builder header(String key, String value);

        /**
         * Sets the body of the HTTP response.
         * 
         * @param body The body of the HTTP response
         * @return this builder
         */
        Builder body(Object body);

        /**
         * Streams the body of the HTTP response from an {@link InputStream}. The
         * stream is read by the Functions runtime and copied to the response body
         * without buffering the entire payload in memory; suitable for large
         * payloads or content of unknown length.
         *
         * <p>The stream is closed by the runtime after the response has been
         * sent. Implementations should not assume the stream supports
         * {@code mark}/{@code reset}.</p>
         *
         * <p>This is a typed alias for {@link #body(Object)} that signals to the
         * runtime to use the streaming write path.</p>
         *
         * @param stream the input stream to stream as the response body
         * @return this builder
         * @since 1.4.0
         */
        default Builder bodyStream(InputStream stream) {
            return body(stream);
        }

        /**
         * Streams the body of the HTTP response via a writer callback. The
         * Functions runtime invokes the callback with the response
         * {@link OutputStream} once response headers have been sent; the
         * function writes its content to the stream and returns. The runtime
         * flushes and closes the stream when the callback returns.
         *
         * <p>Use this overload for server-sent events, chunked responses, or
         * any payload that is more naturally produced incrementally than
         * materialized as a single {@code byte[]} or {@code InputStream}.</p>
         *
         * <p>This is a typed alias for {@link #body(Object)} that signals to the
         * runtime to use the streaming write path.</p>
         *
         * @param writer callback invoked with the response output stream
         * @return this builder
         * @since 1.4.0
         */
        default Builder bodyStream(IOConsumer<OutputStream> writer) {
            return body(writer);
        }

        /**
         * Creates an instance of HttpMessageResponse with the values configured in this builder.
         * 
         * @return an HttpMessageResponse object
         */
        HttpResponseMessage build();
    }
}
