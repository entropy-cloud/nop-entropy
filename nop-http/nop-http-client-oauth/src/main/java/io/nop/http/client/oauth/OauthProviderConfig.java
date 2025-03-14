package io.nop.http.client.oauth;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class OauthProviderConfig {
    private String tokenUri;
    private String userInfoUri;
    private String clientId;
    private String clientSecret;
    private String redirectUri;

    private String scope;

    private String authorizationUri;

    private long expireGap;

    public long getExpireGap() {
        return expireGap;
    }

    public void setExpireGap(long expireGap) {
        this.expireGap = expireGap;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getUserInfoUri() {
        return userInfoUri;
    }

    public void setUserInfoUri(String userInfoUri) {
        this.userInfoUri = userInfoUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getAuthorizationUri() {
        return authorizationUri;
    }

    public void setAuthorizationUri(String authorizationUri) {
        this.authorizationUri = authorizationUri;
    }
}
