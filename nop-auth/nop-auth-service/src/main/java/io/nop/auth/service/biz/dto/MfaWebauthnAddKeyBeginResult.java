/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.biz.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.auth.api.messages.WebAuthnCreationOptions;
import io.nop.auth.api.messages.WebAuthnRequestOptions;

/**
 * webauthnBeginAddKey 的返回结果（A2-followup-2，设计 §10.2 add-key-while-enabled 正门）：
 * 双 challenge 行 ceremony——
 * <ul>
 *   <li>{@code verifyChallengeToken} + {@code assertionOptions}：持有证明（既有 enabled 钥匙
 *       webauthn.get 断言——allowCredentials = enabled credentials）。</li>
 *   <li>{@code addChallengeToken} + {@code creationOptions}：新钥匙注册（webauthn.create
 *       attestation——excludeCredentials = 全部既有 credentials，防同钥匙重复注册）。</li>
 * </ul>
 * 客户端完成两 ceremony 后携四参调 confirmWebauthnAddKey；整体成功时双 challenge 一并消费。
 */
@DataBean
public class MfaWebauthnAddKeyBeginResult {

    /** scene=webauthn-add 持有证明行 token（既有钥匙断言验证凭此定位）。 */
    private String verifyChallengeToken;
    /** 持有证明断言 requestOptions（allowCredentials = enabled credentials）。 */
    private WebAuthnRequestOptions assertionOptions;
    /** scene=webauthn-add 注册行 token（新钥匙 attestation 验证凭此定位）。 */
    private String addChallengeToken;
    /** 新钥匙注册 creationOptions（excludeCredentials = 既有 credentials 防重复注册）。 */
    private WebAuthnCreationOptions creationOptions;

    public String getVerifyChallengeToken() {
        return verifyChallengeToken;
    }

    public void setVerifyChallengeToken(String verifyChallengeToken) {
        this.verifyChallengeToken = verifyChallengeToken;
    }

    public WebAuthnRequestOptions getAssertionOptions() {
        return assertionOptions;
    }

    public void setAssertionOptions(WebAuthnRequestOptions assertionOptions) {
        this.assertionOptions = assertionOptions;
    }

    public String getAddChallengeToken() {
        return addChallengeToken;
    }

    public void setAddChallengeToken(String addChallengeToken) {
        this.addChallengeToken = addChallengeToken;
    }

    public WebAuthnCreationOptions getCreationOptions() {
        return creationOptions;
    }

    public void setCreationOptions(WebAuthnCreationOptions creationOptions) {
        this.creationOptions = creationOptions;
    }
}
