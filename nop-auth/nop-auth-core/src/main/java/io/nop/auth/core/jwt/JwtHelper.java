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
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolver;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.AuthToken;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.json.JsonTool;

import java.io.OutputStream;
import java.io.Reader;
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
        return parseToken(token, new Locator<Key>() {
            @Override
            public Key locate(Header header) {
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

    public static AuthToken parseToken(String token, Locator<Key> keyLocator) {
        try {
            Jws<Claims> jwt = Jwts.parser().keyLocator(keyLocator)
                    .deserializeJsonWith(new Deserializer<Map<String, ?>>() {
                        @Override
                        public Map<String, ?> deserialize(byte[] bytes) throws DeserializationException {
                            return (Map<String, ?>) JsonTool.parse(new String(bytes, StandardCharsets.UTF_8));
                        }

                        @Override
                        public Map<String, ?> deserialize(Reader reader) throws DeserializationException {
                            try {
                                String str = IoHelper.readText(reader);
                                return (Map<String, ?>) JsonTool.parse(str);
                            } catch (Exception e) {
                                throw new DeserializationException("deserialize-fail", e);
                            }
                        }
                    }).build().parseClaimsJws(token);

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
                .serializeToJsonWith(new Serializer<>() {
                    @Override
                    public byte[] serialize(Map<String, ?> stringMap) throws SerializationException {
                        return JsonTool.stringify(stringMap).getBytes(StandardCharsets.UTF_8);
                    }

                    @Override
                    public void serialize(Map<String, ?> stringMap, OutputStream out) throws SerializationException {
                        try {
                            out.write(serialize(stringMap));
                        } catch (Exception e) {
                            throw new SerializationException("serialize-fail", e);
                        }
                    }
                }).compact();
    }
}
