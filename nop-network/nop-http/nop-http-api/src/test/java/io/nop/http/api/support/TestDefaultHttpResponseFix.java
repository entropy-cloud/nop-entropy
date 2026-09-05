package io.nop.http.api.support;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestDefaultHttpResponseFix {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testBodyAsBytesFromObjectBody() {
        DefaultHttpResponse response = new DefaultHttpResponse();
        response.setBody(Map.of("key", "value"));

        byte[] bytes = response.getBodyAsBytes();
        assertNotNull(bytes);
        assertEquals("{\"key\":\"value\"}", new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testBodyNotCorruptedAfterGetBodyAsBytes() {
        DefaultHttpResponse response = new DefaultHttpResponse();
        response.setBody(Map.of("key", "value"));

        response.getBodyAsBytes();

        assertEquals("{\"key\":\"value\"}", response.getBodyAsString());
    }

    @Test
    public void testDefaultCharsetIsUtf8() {
        DefaultHttpResponse response = new DefaultHttpResponse();
        response.setBodyAsText("中文测试");

        byte[] bytes = response.getBodyAsBytes();
        assertArrayEquals("中文测试".getBytes(StandardCharsets.UTF_8), bytes);

        DefaultHttpResponse response2 = new DefaultHttpResponse();
        response2.setBodyAsBytes("中文测试".getBytes(StandardCharsets.UTF_8));
        assertEquals("中文测试", response2.getBodyAsString());
    }
}
