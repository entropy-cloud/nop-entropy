package io.nop.http.oauth;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class HttpClientAuthConfigs {
    private Map<String, OauthProviderConfig> oauthProviders;

    private Map<String, HttpClientAuthConfig> httpClients;

    public Map.Entry<String, HttpClientAuthConfig> getHttpClientConfigForUrl(String url) {
        if (httpClients == null)
            return null;
        for (Map.Entry<String, HttpClientAuthConfig> entry : httpClients.entrySet()) {
            if (entry.getValue().isMatchUrl(url))
                return entry;
        }
        return null;
    }

    public OauthProviderConfig getOauthProvider(String name) {
        return oauthProviders == null ? null : oauthProviders.get(name);
    }

    public Map<String, OauthProviderConfig> getOauthProviders() {
        return oauthProviders;
    }

    public void setOauthProviders(Map<String, OauthProviderConfig> oauthProviders) {
        this.oauthProviders = oauthProviders;
    }

    public Map<String, HttpClientAuthConfig> getHttpClients() {
        return httpClients;
    }

    public void setHttpClients(Map<String, HttpClientAuthConfig> httpClients) {
        this.httpClients = httpClients;
    }
}
