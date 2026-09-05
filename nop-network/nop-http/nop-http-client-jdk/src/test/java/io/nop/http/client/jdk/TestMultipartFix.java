package io.nop.http.client.jdk;

import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMultipartFix {

    @Test
    public void testBoundaryUniquePerInstance() {
        MultipartBodyPublisher first = new MultipartBodyPublisher();
        MultipartBodyPublisher second = new MultipartBodyPublisher();
        // static 边界会让所有请求共用同一分界符，应每实例独立
        assertNotEquals(first.getBoundary(), second.getBoundary());
        assertTrue(!first.getBoundary().isEmpty());
    }

    @Test
    public void testMultipartContentTypeContainsBoundary() {
        JdkHttpClient client = new JdkHttpClient(new HttpClientConfig());
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "value");

        HttpRequest request = HttpRequest.post("http://localhost/upload");
        request.setDataType(HttpApiConstants.DATA_TYPE_MULTIPART);
        request.setBody(form);

        java.net.http.HttpRequest jdkRequest = client.toJdkHttpRequest(request);
        String contentType = jdkRequest.headers().firstValue(HttpApiConstants.HEADER_CONTENT_TYPE).orElse("");
        // multipart 请求的 Content-Type 必须携带 boundary 参数，否则服务端无法解析
        assertTrue(contentType.contains("boundary="), "content-type must contain boundary: " + contentType);
    }
}
