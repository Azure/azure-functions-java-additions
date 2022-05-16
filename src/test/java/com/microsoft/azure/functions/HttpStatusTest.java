package com.microsoft.azure.functions;

import org.junit.*;

import static junit.framework.TestCase.*;

/**
 * Unit tests that enforce annotation contracts and conventions for Functions
 */
public class HttpStatusTest {
    @Test
    public void test_settingCustomStatusCode() {
        HttpStatusType customHttpStatus = HttpStatusType.custom(209);
        assertEquals(209, customHttpStatus.value());
    }

    @Test
    public void test_standardStatusCode() {
        HttpStatusType customHttpStatus = HttpStatus.OK;
        assertEquals(200, customHttpStatus.value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_invalidStatusCode_throwsException() {
        HttpStatusType.custom(-100);
    }
}
