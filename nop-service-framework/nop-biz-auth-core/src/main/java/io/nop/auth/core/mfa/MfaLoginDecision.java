/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 外部身份登录入口（OAuth/SSO）的 MFA/角色策略判定结果（W13-impl，设计 §4.1 结论 10）。
 * <p>
 * 三分支：{@code null} = 放行（维持一期行为）；{@link #isRestricted()} = 策略不达标
 * （受限签发，调用方将 userContext 交回 {@code IMfaLoginPolicyService.completeRestricted}）；
 * challenge 分支由判定方直接抛 {@code ERR_AUTH_MFA_REQUIRED}（errorParams 携带
 * challengeToken/mfaType/loginType，与一期表达一致——错误码常量在 nop-auth-service，
 * 经 SPI 实现方抛出以避免 nop-auth-sso 引入依赖边）。
 */
@DataBean
public class MfaLoginDecision {

    /** true = 角色策略不达标（受限签发分支）；false = challenge 分支（challengeToken 携带）。 */
    private boolean restricted;

    /** challenge 分支：第二因子 challenge token（ERR_AUTH_MFA_REQUIRED errorParams 携带）。 */
    private String challengeToken;

    /** challenge 分支：第二因子类型。 */
    private String mfaType;

    public static MfaLoginDecision restricted() {
        MfaLoginDecision d = new MfaLoginDecision();
        d.restricted = true;
        return d;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }

    public String getMfaType() {
        return mfaType;
    }

    public void setMfaType(String mfaType) {
        this.mfaType = mfaType;
    }
}
