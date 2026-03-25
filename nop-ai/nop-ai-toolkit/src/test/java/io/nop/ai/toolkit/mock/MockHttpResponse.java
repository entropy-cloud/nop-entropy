package io.nop.ai.toolkit.mock;

import io.nop.http.api.client.IHttpResponse;

import java.util.Map;

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
        return body.getBytes();
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
        return Map.of("Content-Type", "application/json");
    }
}
