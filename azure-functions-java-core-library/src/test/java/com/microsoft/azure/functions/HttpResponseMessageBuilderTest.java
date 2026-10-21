/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;

import org.junit.Test;

/**
 * Verifies the default {@code bodyStream} overloads on
 * {@link HttpResponseMessage.Builder} delegate to {@link Builder#body(Object)}
 * with the original object reference preserved, so the runtime can
 * type-dispatch on it.
 */
public class HttpResponseMessageBuilderTest {

    @Test
    public void bodyStreamInputStreamDelegatesToBody() {
        RecordingBuilder builder = new RecordingBuilder();
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        Builder returned = builder.bodyStream(stream);

        assertSame("bodyStream should be a fluent builder", builder, returned);
        assertSame("bodyStream(InputStream) must pass the stream through to body(Object) unchanged",
            stream, builder.lastBody);
    }

    @Test
    public void bodyStreamConsumerDelegatesToBody() {
        RecordingBuilder builder = new RecordingBuilder();
        IOConsumer<OutputStream> writer = os -> os.write(42);

        Builder returned = builder.bodyStream(writer);

        assertSame(builder, returned);
        assertSame("bodyStream(IOConsumer<OutputStream>) must pass the writer through to body(Object) unchanged",
            writer, builder.lastBody);
    }

    @Test
    public void ioConsumerPropagatesIOException() {
        IOException failure = new IOException("disk full");
        IOConsumer<OutputStream> writer = os -> {
            throw failure;
        };

        try {
            writer.accept(new ByteArrayOutputStream());
            fail("Expected IOException");
        } catch (IOException ex) {
            assertSame(failure, ex);
        }
    }

    @Test
    public void ioConsumerExecutesNormally() throws Exception {
        IOConsumer<OutputStream> writer = os -> os.write("hi".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        writer.accept(sink);
        assertEquals("hi", new String(sink.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void bodyStreamRetainsAllOtherBuilderState() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.status(HttpStatus.ACCEPTED)
               .header("X-Test", "1")
               .bodyStream(new ByteArrayInputStream(new byte[0]));

        assertEquals(HttpStatus.ACCEPTED, builder.lastStatus);
        assertEquals("1", builder.headers.get("X-Test"));
    }

    @Test
    public void buildingResponseDoesNotReadOrCloseInputStream() {
        InputStream stream = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Builder must not read the stream");
            }

            @Override
            public void close() {
                throw new AssertionError("Builder must not close the stream");
            }
        };

        HttpResponseMessage response = new RecordingBuilder().bodyStream(stream).build();

        assertSame(stream, response.getBody());
    }

    @Test
    public void buildingResponseDoesNotInvokeCallback() {
        IOConsumer<OutputStream> writer = output -> {
            throw new AssertionError("Builder must not invoke the callback");
        };

        HttpResponseMessage response = new RecordingBuilder().bodyStream(writer).build();

        assertSame(writer, response.getBody());
    }

    @Test
    public void typedNullsDelegateToBody() {
        RecordingBuilder builder = new RecordingBuilder();
        builder.body("previous");

        assertSame(builder, builder.bodyStream((InputStream) null));
        assertSame(null, builder.build().getBody());

        builder.body("previous");

        assertSame(builder, builder.bodyStream((IOConsumer<OutputStream>) null));
        assertSame(null, builder.build().getBody());
    }

    @Test
    public void bodyAndStreamingBodiesReplaceEachOther() {
        RecordingBuilder builder = new RecordingBuilder();
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        IOConsumer<OutputStream> writer = output -> {
            throw new AssertionError("Replacing a callback must not invoke it");
        };

        builder.body("buffered").bodyStream(stream);
        assertSame(stream, builder.build().getBody());
        builder.bodyStream(writer);
        assertSame(writer, builder.build().getBody());
        builder.body("replacement");
        assertEquals("replacement", builder.build().getBody());
    }

    @Test
    public void overloadsPreserveDelegatedBuilderReturnValue() {
        Builder delegated = new RecordingBuilder();
        Builder builder = new RecordingBuilder() {
            @Override
            public Builder body(Object body) {
                return delegated;
            }
        };

        assertSame(delegated, builder.bodyStream(new ByteArrayInputStream(new byte[0])));
        assertSame(delegated, builder.bodyStream(output -> output.write(42)));
    }

    @Test
    public void overloadsPropagateDelegationFailures() {
        IllegalStateException failure = new IllegalStateException("builder is sealed");
        Builder builder = new RecordingBuilder() {
            @Override
            public Builder body(Object body) {
                throw failure;
            }
        };

        try {
            builder.bodyStream(new ByteArrayInputStream(new byte[0]));
            fail("Expected builder failure");
        } catch (IllegalStateException ex) {
            assertSame(failure, ex);
        }
        try {
            builder.bodyStream(output -> output.write(42));
            fail("Expected builder failure");
        } catch (IllegalStateException ex) {
            assertSame(failure, ex);
        }
    }

    private static class RecordingBuilder implements Builder {
        Object lastBody;
        HttpStatusType lastStatus;
        Map<String, String> headers = new HashMap<>();

        @Override
        public Builder status(HttpStatusType status) {
            this.lastStatus = status;
            return this;
        }

        @Override
        public Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        @Override
        public Builder body(Object body) {
            this.lastBody = body;
            return this;
        }

        @Override
        public HttpResponseMessage build() {
            HttpStatusType status = lastStatus;
            Map<String, String> responseHeaders = new HashMap<>(headers);
            Object body = lastBody;
            return new HttpResponseMessage() {
                @Override
                public HttpStatusType getStatus() {
                    return status;
                }

                @Override
                public String getHeader(String key) {
                    return responseHeaders.get(key);
                }

                @Override
                public Object getBody() {
                    return body;
                }
            };
        }
    }
}
