package io.nop.http.api.contenttype;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestContentTypeParseParams {

    @Test
    public void testCharsetBeforeOtherParams() {
        ContentType ct = ContentType.parse("application/json;charset=UTF-8;q=0.9");
        assertEquals("application/json", ct.getMimeType());
        assertEquals(StandardCharsets.UTF_8, ct.getCharset());
    }

    @Test
    public void testCharsetCaseInsensitive() {
        ContentType ct = ContentType.parse("application/json;Charset=UTF-8");
        assertEquals(StandardCharsets.UTF_8, ct.getCharset());
    }

    @Test
    public void testQuotedCharsetWithSpaces() {
        ContentType ct = ContentType.parse("application/json; charset= \"utf-8\" ");
        assertEquals(StandardCharsets.UTF_8, ct.getCharset());
    }

    @Test
    public void testMimeTypeTrimmed() {
        ContentType ct = ContentType.parse("text/plain ; charset=utf-8");
        assertEquals("text/plain", ct.getMimeType());
        assertEquals(StandardCharsets.UTF_8, ct.getCharset());
    }

    @Test
    public void testNoCharset() {
        ContentType ct = ContentType.parse("application/json");
        assertEquals("application/json", ct.getMimeType());
        assertNull(ct.getCharset());
    }

    @Test
    public void testNameValuePairNullValueHashCode() {
        NameValuePair pair = new NameValuePair("n", null);
        NameValuePair pair2 = new NameValuePair("N", null);
        assertEquals(pair, pair2);
        assertEquals(pair.hashCode(), pair2.hashCode());

        // 与有值对象的 hashCode 也不应抛异常
        new NameValuePair("n", "v").hashCode();
    }
}
