/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;

@DataBean
public class LoginRequest extends ExtensibleBean {
    private static final long serialVersionUID = -8865201415621170212L;

    /**
     * 登录方式，例如用户名/密码登录，短信验证等
     */
    private int loginType;

    /**
     * 用户id
     */
    private String principalId;
    private String principalSecret;

    /**
     * 验证码
     */
    private String verifyCode;
    private String verifySecret;


    /**
     * 登录用户主角色id
     */
    private String primaryRoleId;

    /**
     * 安全证书id
     */
    private String certId;

    /**
     * sso登录所使用的accessToken
     */
    private String ssoToken;

    private boolean rememberMe;

    /**
     * 登录用户所使用的设备的唯一id
     */
    private String deviceId;

    /**
     * 登录用户所使用的设备的类型，例如phone, pad等
     */
    private String deviceType;

    /**
     * 客户端应用id
     */
    private String clientId;

    private String locale;

    private String timeZone;

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    public int getLoginType() {
        return loginType;
    }

    public void setLoginType(int loginType) {
        this.loginType = loginType;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getPrincipalSecret() {
        return principalSecret;
    }

    public void setPrincipalSecret(String principalSecret) {
        this.principalSecret = principalSecret;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getPrimaryRoleId() {
        return primaryRoleId;
    }

    public void setPrimaryRoleId(String primaryRoleId) {
        this.primaryRoleId = primaryRoleId;
    }

    public String getCertId() {
        return certId;
    }

    public void setCertId(String certId) {
        this.certId = certId;
    }

    public String getSsoToken() {
        return ssoToken;
    }

    public void setSsoToken(String ssoToken) {
        this.ssoToken = ssoToken;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }

    public String getVerifySecret() {
        return verifySecret;
    }

    public void setVerifySecret(String verifySecret) {
        this.verifySecret = verifySecret;
    }

    @JsonIgnore
    public boolean isInternal() {
        return false;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}