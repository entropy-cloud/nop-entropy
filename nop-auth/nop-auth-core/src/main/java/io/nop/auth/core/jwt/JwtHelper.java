/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolver;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.AuthToken;
import io.nop.core.lang.json.JsonTool;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.nop.auth.core.AuthCoreErrors.ARG_TOKEN;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_TOKEN;

public class JwtHelper {
    public static final String ALG_HMAC_SHA256 = SignatureAlgorithm.HS256.getValue();

    public static AuthToken parseToken(Key key, String token) {
        return parseToken(token, new SigningKeyResolver() {
            @Override
            public Key resolveSigningKey(JwsHeader header, Claims claims) {
                return key;
            }

            @Override
            public Key resolveSigningKey(JwsHeader header, String plaintext) {
                return key;
            }
        });
    }

    public static AuthToken toAuthToken(String token, Claims claims) {
        String sessionId = (String) claims.get(AuthApiConstants.JWT_CLAIMS_SID);
        if (sessionId == null)
            sessionId = claims.getId();

        String subject = claims.getSubject();
        long expireAt = claims.getExpiration().getTime();

        int seconds = (int) ((expireAt - claims.getIssuedAt().getTime()) / 1000);
        String userName = (String) claims.get(AuthApiConstants.JWT_CLAIMS_USERNAME);
        return new AuthToken(token, subject, userName, sessionId, expireAt, seconds, claims);
    }

    public static AuthToken parseToken(String token, SigningKeyResolver resolver) {
        try {
            Jws<Claims> jwt = Jwts.parserBuilder().setSigningKeyResolver(resolver)
                    .deserializeJsonWith(
                            bytes -> (Map<String, ?>) JsonTool.parse(new String(bytes, StandardCharsets.UTF_8)))
                    .build().parseClaimsJws(token);

            Claims claims = jwt.getBody();
            return toAuthToken(token, claims);
        } catch (SecurityException e) {
            throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);
        } catch (ExpiredJwtException e) {
            throw new NopExpiredJwtException(e.getClaims()).authToken(toAuthToken(token, e.getClaims()));
        }
    }

    public static String genToken(Key key, String subject, String userName, String sessionId, long expireSeconds) {
        long begin = CoreMetrics.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put(AuthApiConstants.JWT_CLAIMS_USERNAME, userName);
        return Jwts.builder().addClaims(claims).setId(sessionId).setSubject(subject).setIssuedAt(new Date(begin)).signWith(key)
                .setExpiration(new Date(begin + expireSeconds * 1000L))
                .serializeToJsonWith(stringMap -> JsonTool.stringify(stringMap).getBytes(StandardCharsets.UTF_8))
                .compact();
    }
}
