/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.sso;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;

@ConfigBean
public class SsoConfig {
    private String authServerUrl;
    private String realm;
    private String tokenUrl;
    private String introspectionUri;
    private String authUrl;
    private String userInfoUri;
    private String logoutUrl;
    private String issuer;
    private String audience;

    private String clientId;
    private String clientSecret;

    private int minTimeBetweenJwksRequests = 60;
    private int publicKeyCacheTtl = 3600;
    private String jwksUrl;

    /**
     * 刷新 token 请求的显式超时（毫秒），配置键 {@code nop.auth.sso.refresh-token-timeout}，
     * 经 sso-defaults.beans.xml 的 nopSsoConfig bean 逐属性 {@code @cfg:} 引用注入。
     * 默认 10000ms（IdP token 刷新为轻量 POST，10s ≈ 20-100× 正常 RTT 且低于客户端
     * 默认 readTimeout 30s）。≤0 = 不设显式请求超时（回落客户端部署级默认，
     * HttpClientConfig readTimeout 30s 兜底）——LOCK-001「默认有界 + ≤0 豁免档」两档语义。
     * 字段默认值与 beans.xml {@code @cfg:} 行内默认一致，防双源漂移（C4-007，plan 2508 裁定①）。
     */
    private int refreshTokenTimeout = 10000;

    public String getAuthServerUrl() {
        return authServerUrl;
    }

    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
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

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getIntrospectionUri() {
        return introspectionUri;
    }

    public void setIntrospectionUri(String introspectionUri) {
        this.introspectionUri = introspectionUri;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }

    public String getUserInfoUri() {
        return userInfoUri;
    }

    public void setUserInfoUri(String userInfoUri) {
        this.userInfoUri = userInfoUri;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * 期望的受众（aud）声明。null 时跳过 aud 校验。生产部署建议配置为本方
     * clientId，防止同 IdP 签发给其他 relying party 的令牌在本方被接受
     * （F-A6-1）。
     */
    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }


    /** 2.3-C4-008（plan 2551 closure audit Minor-5）：JWKS 拉取显式超时毫秒（缺省 5000；≤0=客户端缺省） */
    private long jwksFetchTimeoutMillis = 5000L;

    public long getJwksFetchTimeoutMillis() {
        return jwksFetchTimeoutMillis;
    }

    public void setJwksFetchTimeoutMillis(long jwksFetchTimeoutMillis) {
        this.jwksFetchTimeoutMillis = jwksFetchTimeoutMillis;
    }

    public int getMinTimeBetweenJwksRequests() {
        return minTimeBetweenJwksRequests;
    }

    public void setMinTimeBetweenJwksRequests(int minTimeBetweenJwksRequests) {
        this.minTimeBetweenJwksRequests = minTimeBetweenJwksRequests;
    }

    public int getPublicKeyCacheTtl() {
        return publicKeyCacheTtl;
    }

    public void setPublicKeyCacheTtl(int publicKeyCacheTtl) {
        this.publicKeyCacheTtl = publicKeyCacheTtl;
    }

    public int getRefreshTokenTimeout() {
        return refreshTokenTimeout;
    }

    public void setRefreshTokenTimeout(int refreshTokenTimeout) {
        this.refreshTokenTimeout = refreshTokenTimeout;
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public void setJwksUrl(String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getFullUrl(String url) {
        Guard.notEmpty(realm, "empty realm");
        url = StringHelper.replace(url, SsoConstants.PLACE_HOLDER_REALM, realm);
        return StringHelper.appendPath(getAuthServerUrl(), url);
    }
}