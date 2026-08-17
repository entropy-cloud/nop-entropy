package io.nop.auth.service.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

/**
 * bindMfa 的返回结果（设计 §3.6 绑定状态机）。
 * <ul>
 *   <li>totp：{@code provisioningUri}（otpauth URI，含明文 base32 secret，一次性返回）+ {@code bindToken}。</li>
 *   <li>sms：{@code smsSent=true}（验证码已发往用户手机）+ {@code bindToken}，{@code provisioningUri} 为 null。</li>
 *   <li>webauthn（W14-impl，设计 §5.3.2 注册 ceremony）：{@code challengeToken}（scene=webauthn-register
 *       challenge）+ {@code creationOptions}（PublicKeyCredentialCreationOptions 平铺——challenge 即
 *       payload 一次写入的 cryptoChallenge）；{@code bindToken} 不参与 webauthn ceremony（confirm 凭
 *       challengeToken），为 null。</li>
 * </ul>
 * 明文 secret 仅经此处的 provisioning URI 一次性返回，{@code confirmMfa} 响应不含 secret。
 */
@DataBean
public class MfaBindResult {

    private String mfaType;
    /** otpauth://totp/... provisioning URI（仅 totp 类型，含明文 base32 secret）。 */
    private String provisioningUri;
    /** 绑定确认一次性令牌（confirmMfa 凭此定位 pending 记录）。 */
    private String bindToken;
    /** sms 类型时为 true（验证码已发送）。 */
    private boolean smsSent;
    /** webauthn 注册 ceremony 的 challengeToken（confirmWebauthnRegistration 凭此定位，W14）。 */
    private String challengeToken;
    /** webauthn 注册 creationOptions（W14；challenge=cryptoChallenge，excludeCredentials 防重复注册）。 */
    private io.nop.auth.api.messages.WebAuthnCreationOptions creationOptions;

    public String getMfaType() {
        return mfaType;
    }

    public void setMfaType(String mfaType) {
        this.mfaType = mfaType;
    }

    public String getProvisioningUri() {
        return provisioningUri;
    }

    public void setProvisioningUri(String provisioningUri) {
        this.provisioningUri = provisioningUri;
    }

    public String getBindToken() {
        return bindToken;
    }

    public void setBindToken(String bindToken) {
        this.bindToken = bindToken;
    }

    public boolean isSmsSent() {
        return smsSent;
    }

    public void setSmsSent(boolean smsSent) {
        this.smsSent = smsSent;
    }

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }

    public io.nop.auth.api.messages.WebAuthnCreationOptions getCreationOptions() {
        return creationOptions;
    }

    public void setCreationOptions(io.nop.auth.api.messages.WebAuthnCreationOptions creationOptions) {
        this.creationOptions = creationOptions;
    }
}
