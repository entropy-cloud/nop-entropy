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
 */
@DataBean
public class MfaVerifyOperationRequest {
    private String challengeToken;
    private String code;

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
}
