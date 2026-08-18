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
 * 操作级 MFA 验证请求（设计 §3.3 验证端点）。
 * <p>
 * 与登录级 {@link MfaVerifyRequest} 的区别：无 {@code recoveryCode} 字段——恢复码是
 * "丢失验证器的登录恢复通道"（使用即强制重绑），操作级不接受恢复码（设计 §3.1 结论 6）；
 * 成功不签发任何凭证（无 completeLogin），仅将 challenge 转为一次性短 TTL 票。
 * <p>
 * W14-impl 增量（设计 §3.1 结论 5 / §5.3.2 操作级联动）：可选 {@code assertion} 字段
 * （webauthn 断言——拦截器创建的 operation challenge payload 含 cryptoChallenge，客户端
 * 经 {@code webauthnAuthOptions} 取 options 后断言验证）。migration note：跨模块公共
 * API 增量（新增可选字段，无破坏性变更）。
 */
@DataBean
public class MfaVerifyOperationRequest {
    private String challengeToken;
    private String code;
    /** webauthn 断言（可选，W14：webauthn 用户的操作级验证凭据）。 */
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

    public WebAuthnAssertion getAssertion() {
        return assertion;
    }

    public void setAssertion(WebAuthnAssertion assertion) {
        this.assertion = assertion;
    }
}
