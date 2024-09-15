/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.AuthToken;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.function.Function;

import static io.nop.auth.core.AuthCoreErrors.ARG_TOKEN;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_TOKEN;

public class JwtHelper {


    public static final String ALG_HMAC_SHA256 = "HS256";

    public static AuthToken parseToken(Key key, String token) {
        return parseToken(token, k -> key);
    }

    public static SecretKey hmacKey(String password, String salt) {
        return new SecretKeySpec(HashHelper.sha256(password.getBytes(StringHelper.CHARSET_UTF8),
                salt.getBytes(StandardCharsets.UTF_8)), ALG_HMAC_SHA256);
    }

    public static AuthToken toAuthToken(String token, JWTClaimsSet claims) {
        String sessionId = (String) claims.getClaim(AuthApiConstants.JWT_CLAIMS_SID);
        if (sessionId == null)
            sessionId = claims.getJWTID();

        String subject = claims.getSubject();
        long expireAt = claims.getExpirationTime().getTime();

        int seconds = (int) ((expireAt - claims.getIssueTime().getTime()) / 1000);
        String userName = (String) claims.getClaim(AuthApiConstants.JWT_CLAIMS_USERNAME);
        return new AuthToken(token, subject, userName, sessionId, expireAt, seconds, claims.getClaims());
    }

    public static AuthToken parseToken(String token, Function<String, Key> keyLocator) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            Key key = keyLocator.apply(jwt.getHeader().getKeyID());
            JWSVerifier verifier = newVerifier(key);
            boolean isVerified = jwt.verify(verifier);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!isVerified)
                throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);
            AuthToken authToken = toAuthToken(token, claims);
            if (authToken.isExpired())
                throw new NopExpiredJwtException(claims.getClaims()).authToken(authToken);
            return authToken;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_JWT_INVALID_TOKEN, e).param(ARG_TOKEN, token);
        }
    }

    public static SecretKey newHMACKey(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, ALG_HMAC_SHA256);
    }

    public static String genToken(Key key, String subject, String userName, String sessionId, long expireSeconds) {
        Guard.notEmpty(sessionId, "sessionId");

        try {
            JWSSigner signer = newSigner(key);

            long begin = CoreMetrics.currentTimeMillis();
            JWSHeader header = new JWSHeader(getSignAlgorithm(key));
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer("nop")
                    .subject(subject)
                    .expirationTime(new Date(begin + expireSeconds * 1000L))
                    .issueTime(new Date(begin)).jwtID(sessionId)
                    .claim(AuthApiConstants.JWT_CLAIMS_USERNAME, userName).build();

            // 创建JWT对象
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            // 对JWT进行签名
            signedJWT.sign(signer);

            // 生成序列化的JWT
            String serializedJWT = signedJWT.serialize();

            return serializedJWT;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private static JWSAlgorithm getSignAlgorithm(Key key) {
        if (key instanceof PrivateKey)
            return JWSAlgorithm.RS256;
        return JWSAlgorithm.HS256;
    }

    private static JWSSigner newSigner(Key key) throws KeyLengthException {
        if (key instanceof PrivateKey)
            return new RSASSASigner((PrivateKey) key);
        if (key instanceof SecretKey)
            return new MACSigner((SecretKey) key);
        throw new IllegalArgumentException("nop.err.unsupported-key:" + key);
    }

    private static JWSVerifier newVerifier(Key key) throws JOSEException {
        if (key instanceof RSAPublicKey)
            return new RSASSAVerifier((RSAPublicKey) key);
        if (key instanceof SecretKey)
            return new MACVerifier((SecretKey) key);
        throw new IllegalArgumentException("nop.err.unsupported-key:" + key);
    }
}
