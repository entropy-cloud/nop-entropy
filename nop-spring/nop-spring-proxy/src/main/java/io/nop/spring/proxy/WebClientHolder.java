package io.nop.spring.proxy;

import org.springframework.web.reactive.function.client.WebClient;

public class WebClientHolder {
    private WebClient webClient;
    private WebClientConfig config;
    private String authorization;
    private String cookie;

    public WebClient getWebClient() {
        return webClient;
    }

    public void setWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public WebClientConfig getConfig() {
        return config;
    }

    public void setConfig(WebClientConfig config) {
        this.config = config;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }
}
