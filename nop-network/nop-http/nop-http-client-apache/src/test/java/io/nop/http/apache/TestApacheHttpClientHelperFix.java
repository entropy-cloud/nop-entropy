package io.nop.http.apache;

import io.nop.http.api.support.DefaultHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestApacheHttpClientHelperFix {

    @Test
    public void testFromSimpleResponseWithoutBody() {
        // 204/304/HEAD 等无实体响应：copy() 后 body 为 null
        SimpleHttpResponse response = SimpleHttpResponse.create(204);

        DefaultHttpResponse result = assertDoesNotThrow(() -> ApacheHttpClientHelper.fromSimpleResponse(response));
        assertEquals(204, result.getHttpStatus());
    }

    @Test
    public void testFromSimpleResponseWithTextBody() {
        SimpleHttpResponse response = SimpleHttpResponse.create(200, "hello", ContentType.TEXT_PLAIN);
        DefaultHttpResponse result = ApacheHttpClientHelper.fromSimpleResponse(response);
        assertEquals(200, result.getHttpStatus());
        assertEquals("hello", result.getBodyAsString());
    }
}
