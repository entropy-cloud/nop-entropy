/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ExtensibleBean;

import java.util.Map;

/**
 * 返回的结果数据与OAuth响应相同
 */
@DataBean
public class LoginResult extends ExtensibleBean {
    private static final long serialVersionUID = 3358337231019147724L;

    private String accessToken;
    private long expiresIn;
    private String refreshToken;
    private long refreshExpiresIn;
    private String scope;

    private String tokenType = "bearer";

    private String sessionState;

    private LoginUserInfo userInfo;

    @PropMeta(propId = 1)
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @PropMeta(propId = 2)
    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    @PropMeta(propId = 3)
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @PropMeta(propId = 4)
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @PropMeta(propId = 5)
    public long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    public void setRefreshExpiresIn(long refreshExpiresIn) {
        this.refreshExpiresIn = refreshExpiresIn;
    }

    @PropMeta(propId = 6)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    @PropMeta(propId = 7)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public LoginUserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(LoginUserInfo userInfo) {
        this.userInfo = userInfo;
    }

    @PropMeta(propId = 8)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSessionState() {
        return sessionState;
    }

    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }

    @PropMeta(propId = 9)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getAttrs() {
        return super.getAttrs();
    }

}