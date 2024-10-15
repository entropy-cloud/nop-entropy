package io.nop.http.oauth;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Set;
import java.util.regex.Pattern;

@DataBean
public class HttpClientAuthConfig {
    private String urlPattern;

    private String oauthProvider;

    private Set<String> propagateHeaders;

    private boolean useContextAccessToken;

    public String getUrlPattern() {
        return urlPattern;
    }

    private Pattern pattern;

    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
        this.pattern = Pattern.compile(urlPattern);
    }

    public boolean isMatchUrl(String url) {
        return pattern.matcher(url).matches();
    }

    public String getOauthProvider() {
        return oauthProvider;
    }

    public void setOauthProvider(String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }

    public Set<String> getPropagateHeaders() {
        return propagateHeaders;
    }

    public void setPropagateHeaders(Set<String> propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }

    public boolean isUseContextAccessToken() {
        return useContextAccessToken;
    }

    public void setUseContextAccessToken(boolean useContextAccessToken) {
        this.useContextAccessToken = useContextAccessToken;
    }
}
