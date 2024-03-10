/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.sso;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.time.CoreMetrics;

import java.time.Duration;

/**
 * Access and Refresh tokens returned from a token grant request
 */
public class Tokens {
    final private String accessToken;
    final private Long accessTokenExpiresAt;
    final private Long refreshTokenTimeSkew;
    final private String refreshToken;
    final Long refreshTokenExpiresAt;

    public Tokens(String accessToken, Long accessTokenExpiresAt, Duration refreshTokenTimeSkewDuration,
                  String refreshToken, Long refreshTokenExpiresAt) {
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenTimeSkew = refreshTokenTimeSkewDuration == null ? null
                : refreshTokenTimeSkewDuration.getSeconds();
        this.refreshToken = refreshToken;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Long getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public Long getRefreshTokenTimeSkew() {
        return refreshTokenTimeSkew;
    }

    @JsonIgnore
    public boolean isAccessTokenExpired() {
        return isExpired(accessTokenExpiresAt);
    }

    @JsonIgnore
    public boolean isRefreshTokenExpired() {
        return isExpired(refreshTokenExpiresAt);
    }

    @JsonIgnore
    public boolean isAccessTokenWithinRefreshInterval() {
        if (accessTokenExpiresAt == null || refreshTokenTimeSkew == null) {
            return false;
        }
        final long nowSecs = CoreMetrics.currentTimeMillis() / 1000;
        return nowSecs + refreshTokenTimeSkew > accessTokenExpiresAt;
    }

    private static boolean isExpired(Long expiresAt) {
        if (expiresAt == null) {
            return false;
        }
        final long nowSecs = CoreMetrics.currentTimeMillis() / 1000;
        return nowSecs > expiresAt;
    }
}
