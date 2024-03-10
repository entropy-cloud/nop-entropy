/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.sso;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Locator;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.auth.core.jwt.JwtHelper;
import io.nop.auth.sso.jwk.IPublicKeyLocator;
import io.nop.auth.sso.jwk.JWKPublicKeyLocator;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.security.Key;

import static io.nop.auth.core.AuthCoreErrors.ARG_CLAIMS;

@Disabled
public class TestJwtHelper {
    @Test
    public void testVerify() {
        JSON.registerProvider(JsonTool.instance());
        IPublicKeyLocator keyLocator = buildKeyLocator();

        String token = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICIxMEM3YTJkOUZJV0ZVanhzdjEyYjAxT2hPemFnb0t0WkNQNnl0UWJLbDVvIn0.eyJleHAiOjE2NzA2NDI1OTMsImlhdCI6MTY3MDY0MjI5MywianRpIjoiODRiNmEwMTYtZjFjZi00ZmUwLThlNjctOWU3OWYyNzZlMTZjIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDQxL3JlYWxtcy9hcHAiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiNmFkMzZiODItZWY2Yy00YjI1LWIwNDItMGM4ZjQ1MGY2MjVjIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoidGVzdC1jbGllbnQiLCJzZXNzaW9uX3N0YXRlIjoiMjI2YzRlOGItNDA1ZC00OGMwLTkzN2EtM2M2MjAxMDhlNzE4IiwiYWNyIjoiMSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJhcHBVc2VyIiwib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiIsImRlZmF1bHQtcm9sZXMtYXBwIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJlbWFpbCBwcm9maWxlIiwic2lkIjoiMjI2YzRlOGItNDA1ZC00OGMwLTkzN2EtM2M2MjAxMDhlNzE4IiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJuYW1lIjoidGVzdCBBQkMiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJ0ZXN0MSIsImdpdmVuX25hbWUiOiJ0ZXN0IiwiZmFtaWx5X25hbWUiOiJBQkMifQ.A2tbYa6sEiCLQ-lgXCZfVpYQF-D9Y3jRVairwTOLozPT3aaiGZaBWU-hTZhWVNHW0nxJHYrUE9AfFVXRhUJ3bWy2sUj_gDTmNjWsG32-9DUn_j8UK1qQnh8S9FB5GGzj4-1FOtIcvQf4byBagUFGj6FzhYu09rLFwF1SZCFQ6SylkEGOuUKxAR_EPy6U5INhFGGf6Q___oN4L2J5uOmq7Baf1VWRe3KFNNPns4rI6xik_3b2ayOyFmk_0uB4SWEmFRgRPaZd62Pi_DhJe2-6FE7QlLtCnynS3aRDkS8Loo-HIOgCV0zH4TqMeGEY6UUDoz4q_CcqSK0BOxd_sASlYg";
        try {
            JwtHelper.parseToken(token, new Locator<Key>() {
                @Override
                public Key locate(Header header) {
                    return keyLocator.getPublicKey(((JwsHeader) header).getKeyId());
                }
            });
        } catch (NopException e) {
            System.out.println(e.getParam(ARG_CLAIMS));
        }
    }

    IPublicKeyLocator buildKeyLocator() {
        HttpClientConfig config = new HttpClientConfig();
        JdkHttpClient httpClient = new JdkHttpClient(config);
        httpClient.start();

        JWKPublicKeyLocator keyLocator = new JWKPublicKeyLocator();
        keyLocator.setHttpClient(httpClient);
        SsoConfig ssoConfig = new SsoConfig();
        ssoConfig.setRealm("app");
        ssoConfig.setPublicKeyCacheTtl(300);
        ssoConfig.setJwksUrl("http://localhost:8041/realms/app/protocol/openid-connect/certs");
        keyLocator.setConfig(ssoConfig);
        return keyLocator;
    }
}
