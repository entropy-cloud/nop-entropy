package io.nop.metadata.service.mock;

import io.nop.http.api.client.IHttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test mock for {@link IHttpResponse}. Returns configurable status/body.
 */
public class MockHttpResponse implements IHttpResponse {
    private final int status;
    private final String body;

    public MockHttpResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    @Override
    public int getHttpStatus() {
        return status;
    }

    @Override
    public String getBodyAsString() {
        return body;
    }

    @Override
    public byte[] getBodyAsBytes() {
        return body != null ? body.getBytes() : new byte[0];
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public String getCharset() {
        return "UTF-8";
    }

    @Override
    public <T> T getBodyAsBean(Class<T> beanClass) {
        return null;
    }

    @Override
    public Object getBody() {
        return body;
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
