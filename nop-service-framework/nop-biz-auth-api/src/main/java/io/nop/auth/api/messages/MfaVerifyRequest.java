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
 * <p>
 * W14-impl 增量（设计 §3.1 结论 5 统一验证载体 / §5.3.2）：可选 {@code assertion} 字段
 * （webauthn 断言，mfaType=webauthn 的 challenge 必填，其余类型忽略）——向后兼容，
 * 老调用方零感知。migration note：跨模块公共 API 增量（新增可选字段，无破坏性变更）。
 */
@DataBean
public class MfaVerifyRequest {
    private String challengeToken;
    private String code;
    private String recoveryCode;
    /** webauthn 断言（可选，W14：webauthn 类型 challenge 的验证凭据）。 */
    private WebAuthnAssertion assertion;

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

    public WebAuthnAssertion getAssertion() {
        return assertion;
    }

    public void setAssertion(WebAuthnAssertion assertion) {
        this.assertion = assertion;
    }
}
