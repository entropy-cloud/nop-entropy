/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.login;

import io.nop.api.core.util.Guard;

import java.util.Map;

public class AuthToken {
    private final String token;
    private final String subject;
    private final String sessionId;
    private final String userName;
    private final long expireAt;
    private final int expireSeconds;
    private final Map<String, Object> claims;
    private boolean newlyCreated;

    public AuthToken(String token, String subject, String userName, String sessionId,
                     long expireAt, int expireSeconds, Map<String, Object> claims) {
        this.token = token;
        this.subject = subject;
        this.sessionId = Guard.notEmpty(sessionId,"sessionId");
        this.expireAt = expireAt;
        this.userName = userName;
        this.expireSeconds = expireSeconds;
        this.claims = claims;
    }

    public boolean isNewlyCreated() {
        return newlyCreated;
    }

    public void setNewlyCreated(boolean newlyCreated) {
        this.newlyCreated = newlyCreated;
    }

    public String getUserName() {
        return userName;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public String getToken() {
        return token;
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public String getSubject() {
        return subject;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getExpireAt() {
        return expireAt;
    }
}
