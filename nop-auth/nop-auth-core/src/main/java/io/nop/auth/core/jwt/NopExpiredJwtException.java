/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.jwt;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.AuthToken;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.auth.core.AuthCoreErrors.ARG_CLAIMS;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_TOKEN_EXPIRED;

public class NopExpiredJwtException extends NopException {
    private AuthToken authToken;

    public NopExpiredJwtException(Map<String, Object> claims) {
        super(ERR_JWT_TOKEN_EXPIRED);
        this.param(ARG_CLAIMS, claims);
    }

    public Map<String, Object> getClaims() {
        return (Map<String, Object>) getParam(ARG_CLAIMS);
    }

    public AuthToken getAuthToken() {
        return authToken;
    }

    public void setAuthToken(AuthToken authToken) {
        this.authToken = authToken;
    }

    public NopExpiredJwtException authToken(AuthToken authToken) {
        this.authToken = authToken;
        param("expTime", new Timestamp(authToken.getExpireAt()));
        return this;
    }
}
