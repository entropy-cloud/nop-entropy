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
 * WebAuthn 认证 requestOptions（W14-impl，设计 §5.3.2 认证/解绑 ceremony 的服务端下发）。
 * <p>
 * 与 {@code PublicKeyCredentialRequestOptions} 平铺对应；经 {@code webauthnAuthOptions(challengeToken)}
 * （登录级公开/其余场景登录态+同会话）与 {@code webauthnBeginVerify}（解绑发起）返回。
 * challenge 为该 challenge payload 中一次写入的 cryptoChallenge 的<b>只读复用</b>（不更新）。
 */
@DataBean
public class WebAuthnRequestOptions {
    /** 密码学挑战（base64url——challenge payload 的 cryptoChallenge 只读复用）。 */
    private String challenge;
    /** rpId（{@code nop.auth.mfa.webauthn.rp-id}）。 */
    private String rpId;
    /** 允许的 credentialId（base64url，allowCredentials——该用户 enabled credentials）。 */
    private List<String> allowCredentials;
    /** 用户验证要求（缺省 "preferred"）。 */
    private String userVerification;
    /** 超时毫秒（可空）。 */
    private Long timeout;

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

    public List<String> getAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(List<String> allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public String getUserVerification() {
        return userVerification;
    }

    public void setUserVerification(String userVerification) {
        this.userVerification = userVerification;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }
}
