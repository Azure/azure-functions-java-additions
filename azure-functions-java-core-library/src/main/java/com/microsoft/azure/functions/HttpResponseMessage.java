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
         * Sets an {@link InputStream} as the response body for deferred streaming.
         * This method delegates to {@link #body(Object)} without reading or
         * closing the stream. A {@code null} stream delegates to {@code body(null)}.
         *
         * <p>Streaming requires a compatible Functions Java worker and host with
         * HTTP response streaming enabled. This API alone does not enable
         * streaming, and older runtimes do not provide a streaming fallback.</p>
         *
         * <p>Keep the stream open after the function returns. The runtime closes
         * a stream it consumes, including when copying fails. Do not reuse the
         * stream for another response. The stream need not support
         * {@code mark}/{@code reset}. If the body is replaced or the response is
         * never handed to the runtime, the caller remains responsible for closing
         * the stream.</p>
         *
         * <p>Errors encountered while streaming may occur after response headers
         * have been sent. In that case the response may be incomplete and its
         * status cannot be changed to report the error.</p>
         *
         * @param stream the input stream to stream as the response body
         * @return this builder
         * @since 1.4.0
         */
        default Builder bodyStream(InputStream stream) {
            return body(stream);
        }

        /**
         * Sets a writer callback as the response body for deferred streaming.
         * This method delegates to {@link #body(Object)} without invoking the
         * callback. A {@code null} writer delegates to {@code body(null)}.
         *
         * <p>The runtime invokes the callback when it consumes the response body.
         * Write synchronously to the supplied {@link OutputStream} and return
         * when finished. Flush it when incremental delivery is needed. Do not
         * close or retain it, or write to it after returning. The runtime owns
         * and closes the output stream, including when the callback fails.
         * The callback must manage any resources it opens.</p>
         *
         * <p>Streaming requires a compatible Functions Java worker and host with
         * HTTP response streaming enabled. This API alone does not enable
         * streaming, and older runtimes do not provide a streaming fallback.
         * Delivery timing also depends on host and intermediary buffering.</p>
         *
         * <p>The callback may throw {@link IOException}. An error after headers
         * have been sent can leave an incomplete response; it cannot change the
         * response status. Treat the callback as single-use and do not assume it
         * runs on the function invocation thread.</p>
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
