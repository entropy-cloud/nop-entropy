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
 */
@DataBean
public class ScanLoginResult {

    private String accessCode;

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }
}
