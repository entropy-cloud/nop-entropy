package io.nop.http.api.contenttype;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestContentType {
    @Test
    public void testParse() {
        ContentType contentType = ContentType.parse("application/json;charset=UTF-8");
        assertEquals("application/json", contentType.getMimeType());
        assertEquals("UTF-8", contentType.getCharset().toString());

        contentType = ContentType.parse("application/json; charset=UTF-8");
        assertEquals("application/json", contentType.getMimeType());
        assertEquals("UTF-8", contentType.getCharset().toString());
    }
}
