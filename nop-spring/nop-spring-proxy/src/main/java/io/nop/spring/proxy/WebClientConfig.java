package io.nop.spring.proxy;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class WebClientConfig {
    private String baseUrl;
    private Map<String, String> headers;
    private String refreshAuthUrl;
    private String tokenPath;
    private String chatUrl;

    public String getHeader(String name) {
        return headers == null ? null : headers.get(name);
    }

    public String getChatUrl() {
        return chatUrl;
    }

    public void setChatUrl(String chatUrl) {
        this.chatUrl = chatUrl;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    public String getRefreshAuthUrl() {
        return refreshAuthUrl;
    }

    public void setRefreshAuthUrl(String refreshAuthUrl) {
        this.refreshAuthUrl = refreshAuthUrl;
    }

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