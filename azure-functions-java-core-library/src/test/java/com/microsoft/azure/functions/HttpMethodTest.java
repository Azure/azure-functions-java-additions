package com.microsoft.azure.functions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpMethodTest {
    @Test
    public void parse_query_httpmethod() {
        assertEquals(HttpMethod.QUERY, HttpMethod.value("QUERY"));
        assertEquals(HttpMethod.QUERY, HttpMethod.value("query"));
        assertEquals(HttpMethod.QUERY, HttpMethod.value("QuErY"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void reject_unknown_httpmethod() {
        HttpMethod.value("UNKNOWN");
    }
}
