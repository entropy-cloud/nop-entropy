/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.messages;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 第二因子验证请求（设计 §3.6）。
 * <p>
 * {@code challengeToken} 由第一因子通过后服务端经 {@code ERR_AUTH_MFA_REQUIRED} 的
 * errorParams 返回；{@code code} 为 TOTP / 短信验证码；{@code recoveryCode} 可选，
 * 提交时走恢复码分支（成功后强制重绑）。
 */
@DataBean
public class MfaVerifyRequest {
    private String challengeToken;
    private String code;
    private String recoveryCode;

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }
}
