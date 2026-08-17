/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    /**
     * 信道类登录（SSO/扫码）经 mfaVerify 成功后的出口：一次性 accessCode，
     * 由前端再换取 accessToken。密码类登录路径为 null（直接签发 accessToken）。
     */
    private String accessCode;

    /**
     * MFA 受限会话标志（角色级强制策略不达标，W13-impl 可选增量——migration note：
     * 可选字段向后兼容，老消费方零感知）。true = 登录成功但会话受限
     * （{@code IUserContext.isMfaRestricted()}），前端据此渲染受限引导页；
     * 正常登录缺省不出现（null）。
     */
    private Boolean mfaRestricted;

    /**
     * 可信设备登记结果（W15-impl 可选增量，设计 §6.1 结论 4——migration note：可选字段
     * 向后兼容）。仅密码类路径的 mfaVerify(rememberDevice=true) 成功后出现：true=已登记，
     * false=未登记（无 device-id / 满员 / 登记失败——非错误提示，不阻断登录）；null=未请求登记。
     */
    private Boolean trustedDeviceRegistered;

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

    @PropMeta(propId = 10)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    @PropMeta(propId = 11)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getMfaRestricted() {
        return mfaRestricted;
    }

    public void setMfaRestricted(Boolean mfaRestricted) {
        this.mfaRestricted = mfaRestricted;
    }

    @PropMeta(propId = 12)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getTrustedDeviceRegistered() {
        return trustedDeviceRegistered;
    }

    public void setTrustedDeviceRegistered(Boolean trustedDeviceRegistered) {
        this.trustedDeviceRegistered = trustedDeviceRegistered;
    }

}