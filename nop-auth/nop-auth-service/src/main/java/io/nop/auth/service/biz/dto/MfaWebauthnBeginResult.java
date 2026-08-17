/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.biz.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.auth.api.messages.WebAuthnRequestOptions;

/**
 * webauthnBeginVerify 的返回结果（W14-impl，设计 §5.3.2 解绑 ceremony 发起）：
 * scene=webauthn-unbind challenge 的 challengeToken + requestOptions（challenge 为 payload
 * 一次写入的 cryptoChallenge 只读复用；allowCredentials = 该用户 enabled credentials）。
 * 客户端完成断言后携 challengeToken + assertion 调 unbindMfa 完成解绑。
 */
@DataBean
public class MfaWebauthnBeginResult {

    /** scene=webauthn-unbind challenge token（unbindMfa 断言验证凭此定位）。 */
    private String challengeToken;
    /** 断言 requestOptions（allowCredentials + 只读复用的 cryptoChallenge）。 */
    private WebAuthnRequestOptions requestOptions;

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }

    public WebAuthnRequestOptions getRequestOptions() {
        return requestOptions;
    }

    public void setRequestOptions(WebAuthnRequestOptions requestOptions) {
        this.requestOptions = requestOptions;
    }
}
