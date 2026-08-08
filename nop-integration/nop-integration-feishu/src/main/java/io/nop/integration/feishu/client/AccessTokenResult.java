package io.nop.integration.feishu.client;

/**
 * {@code tenant_access_token} acquisition result returned by
 * {@link IFeishuHttpApi#getTenantAccessToken(String, String)}.
 *
 * <p>Package-private: internal model.
 */
final class AccessTokenResult {

    private final String token;
    private final long expireSeconds;

    AccessTokenResult(String token, long expireSeconds) {
        this.token = token;
        this.expireSeconds = expireSeconds;
    }

    String getToken() {
        return token;
    }

    long getExpireSeconds() {
        return expireSeconds;
    }
}
