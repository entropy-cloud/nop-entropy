/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.messages;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * WebAuthn 注册 creationOptions（W14-impl，设计 §5.3.2 注册 ceremony 的服务端下发）。
 * <p>
 * 与 {@code PublicKeyCredentialCreationOptions} 平铺对应（challenge 等二进制字段为 base64url）；
 * 经 {@code bindMfa(webauthn)} 返回（随 challengeToken）。excludeCredentials 携带该用户已注册
 * credentialId（防同一钥匙重复注册）。
 */
@DataBean
public class WebAuthnCreationOptions {
    /** 密码学挑战（base64url，32 字节随机——服务端记入 challenge payload 一次写入，断言/证明时比对）。 */
    private String challenge;
    /** rpId（{@code nop.auth.mfa.webauthn.rp-id}）。 */
    private String rpId;
    /** rp 显示名（{@code nop.auth.mfa.webauthn.rp-name}）。 */
    private String rpName;
    /** 用户句柄（base64url，= 服务端 userId——断言回传时校验归属）。 */
    private String userId;
    /** 用户名（安全描述用，如 user.userName）。 */
    private String userName;
    /** 用户显示名。 */
    private String userDisplayName;
    /** 支持的公钥算法（COSE alg 名，如 ES256/RS256）。 */
    private List<String> pubKeyCredParams;
    /** 已注册 credentialId（base64url，excludeCredentials——防重复注册）。 */
    private List<String> excludeCredentials;
    /** 超时毫秒（可空）。 */
    private Long timeout;
    /** 认证器选择要求（可空字符串=不指定；如 "cross-platform"）。 */
    private String authenticatorSelection;

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public void setUserDisplayName(String userDisplayName) {
        this.userDisplayName = userDisplayName;
    }

    public List<String> getPubKeyCredParams() {
        return pubKeyCredParams;
    }

    public void setPubKeyCredParams(List<String> pubKeyCredParams) {
        this.pubKeyCredParams = pubKeyCredParams;
    }

    public List<String> getExcludeCredentials() {
        return excludeCredentials;
    }

    public void setExcludeCredentials(List<String> excludeCredentials) {
        this.excludeCredentials = excludeCredentials;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getAuthenticatorSelection() {
        return authenticatorSelection;
    }

    public void setAuthenticatorSelection(String authenticatorSelection) {
        this.authenticatorSelection = authenticatorSelection;
    }
}
