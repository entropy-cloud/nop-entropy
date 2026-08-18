package io.nop.ai.gateway.login;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Result of the QR-scan-login callback endpoint
 * ({@link ChannelLoginApiBizModel#loginByScanAsync}). Carries the one-time
 * {@code accessCode} that the front-end then exchanges for a full
 * {@code LoginResult} via the existing
 * {@code ILoginSpi.getLoginResultAsync(AccessCodeRequest)}.
 *
 * <p>The {@code accessCode} is a provider-signed JWT minted by
 * {@link io.nop.auth.core.login.IAuthTokenProvider#generateAccessCode},
 * not an opaque store key — the existing {@code parseAccessCode} decodes it.
 *
 * <p><b>MFA adaptation (W6)</b>: when the bound platform user has MFA enabled,
 * {@code createSessionForUserAsync} throws {@code ERR_AUTH_MFA_REQUIRED}
 * synchronously (design §3.2). {@link ChannelLoginApiBizModel#loginByScanAsync}
 * captures it and returns {@code mfaRequired=true} with the challenge params
 * ({@code challengeToken}/{@code mfaType}/{@code loginType}) instead of an
 * {@code accessCode}. The mobile side then calls {@code mfaVerify(challengeToken)}
 * to obtain a channel-typed {@code accessCode}. The new fields are optional and
 * backward compatible: existing callers that ignore them are unaffected
 * (non-MFA scans return {@code mfaRequired=false}, {@code accessCode} populated).
 */
@DataBean
public class ScanLoginResult {

    private String accessCode;

    /** True when the bound user has MFA enabled and a second factor is required. */
    private boolean mfaRequired;

    /** One-time MFA challenge token (set when {@code mfaRequired=true}). */
    private String challengeToken;

    /** MFA factor type: {@code totp} / {@code sms} (set when {@code mfaRequired=true}). */
    private String mfaType;

    /** Real channel loginType (20-23) carried from the challenge (audit fidelity). */
    private Integer loginType;

    /**
     * MFA restricted-session flag (W13, role-level MFA policy): true when the first
     * factor passed but the bound user's factor holdings do not satisfy the role
     * policy ({@code minMfaLevel}). The scan still yields a valid {@code accessCode}
     * (the session is issued restricted); the front-end should render the
     * restricted-guidance flow. Optional field, backward compatible (W6 precedent,
     * same shape as {@code mfaRequired}).
     */
    private boolean mfaRestricted;

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public boolean isMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
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

    public Integer getLoginType() {
        return loginType;
    }

    public void setLoginType(Integer loginType) {
        this.loginType = loginType;
    }

    public boolean isMfaRestricted() {
        return mfaRestricted;
    }

    public void setMfaRestricted(boolean mfaRestricted) {
        this.mfaRestricted = mfaRestricted;
    }
}
