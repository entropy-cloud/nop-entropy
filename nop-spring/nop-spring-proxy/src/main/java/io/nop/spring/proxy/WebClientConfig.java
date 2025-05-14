package io.nop.spring.proxy;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class WebClientConfig {
    private String baseUrl;
    private Map<String, String> headers;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}