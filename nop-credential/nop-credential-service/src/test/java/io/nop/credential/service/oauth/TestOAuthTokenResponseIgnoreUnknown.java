/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.oauth;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOAuthTokenResponseIgnoreUnknown {

    @Test
    public void testUnknownFieldsAreIgnored() {
        String json = "{\"access_token\":\"token\",\"expires_in\":3600,\"refresh_token\":\"refresh\"," +
                "\"token_type\":\"Bearer\",\"scope\":\"read\",\"unknown_field\":\"ignored\"}";

        OAuthTokenResponse response = JsonTool.parseBeanFromText(json, OAuthTokenResponse.class);
        assertEquals("token", response.getAccessToken());
        assertEquals(Long.valueOf(3600), response.getExpiresIn());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("read", response.getScope());
    }
}
