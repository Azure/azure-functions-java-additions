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
        IOConsumer<OutputStream> writer = os -> {
            throw new IOException("disk full");
        };

        try {
            writer.accept(new ByteArrayOutputStream());
            fail("Expected IOException");
        } catch (IOException ex) {
            assertEquals("disk full", ex.getMessage());
        }
    }

    @Test
    public void ioConsumerExecutesNormally() throws Exception {
        IOConsumer<OutputStream> writer = os -> os.write("hi".getBytes("UTF-8"));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        writer.accept(sink);
        assertEquals("hi", new String(sink.toByteArray(), "UTF-8"));
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

    /** Minimal in-memory Builder that records the last body passed in. */
    private static final class RecordingBuilder implements Builder {
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
            // Tests inspect builder state directly; no need to materialize a response.
            return null;
        }
    }
}
