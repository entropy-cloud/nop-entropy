/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.sso.login;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolver;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.core.jwt.JwtHelper;
import io.nop.auth.core.login.AbstractLoginService;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.sso.AccessTokenResponse;
import io.nop.auth.sso.SsoConfig;
import io.nop.auth.sso.SsoConstants;
import io.nop.auth.sso.jwk.JWKPublicKeyLocator;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.api.AuthApiConstants.LOGOUT_TYPE_SSO_CALLBACK;
import static io.nop.auth.sso.SsoErrors.ARG_ERROR;
import static io.nop.auth.sso.SsoErrors.ERR_AUTH_SSO_ACCESS_FAIL;

public class OAuthLoginServiceImpl extends AbstractLoginService {
    static final Logger LOG = LoggerFactory.getLogger(OAuthLoginServiceImpl.class);

    private static final String KEY_ACCESS_TOKEN = "accessToken";
    // private static final String KEY_ACCESS_TOKEN_EXPIRE_TIME = "accessTokenExpireTime";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";

    @Inject
    protected IHttpClient httpClient;

    protected SsoConfig config;

    protected JWKPublicKeyLocator keyLocator;

    public void setConfig(SsoConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        keyLocator = new JWKPublicKeyLocator();
        keyLocator.setHttpClient(httpClient);
        keyLocator.setConfig(config);
    }

    @Override
    public CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers) {
        HttpRequest req = newLoginRequest(request);
        return httpClient.fetchAsync(req, null).thenCompose(res -> {
            IUserContext userContext = buildUserContext(res.getBodyAsBean(AccessTokenResponse.class));
            if (userContext == null)
                return null;
            return userContextCache.saveUserContextAsync(userContext).thenApply(v -> userContext);
        });
    }

    /**
     * <code>
     * curl \
     * -d "client_id=admin-cli" \
     * -d "username=admin" \
     * -d "password=password" \
     * -d "grant_type=password" \
     * "http://localhost:8080/realms/master/protocol/openid-connect/token"
     * </code>
     */
    protected HttpRequest newLoginRequest(LoginRequest request) {
        HttpRequest req = new HttpRequest();
        req.setMethod(HttpApiConstants.METHOD_POST);
        Map<String, Object> map = new HashMap<>();
        if (request.getSsoToken() != null) {
            map.put(SsoConstants.GRANT_TYPE, SsoConstants.AUTHORIZATION_CODE);
            map.put(SsoConstants.CODE_FLOW_CODE, request.getSsoToken());
        } else {
            map.put(SsoConstants.PASSWORD_GRANT_USERNAME, request.getPrincipalId());
            map.put(SsoConstants.PASSWORD_GRANT_PASSWORD, request.getPrincipalSecret());
            map.put(SsoConstants.GRANT_TYPE, SsoConstants.PASSWORD_GRANT);
        }
        map.put(SsoConstants.CLIENT_ID, config.getClientId());
        if (!StringHelper.isEmpty(config.getClientSecret())) {
            map.put(SsoConstants.CLIENT_SECRET, config.getClientSecret());
        }
        req.dataType(HttpApiConstants.DATA_TYPE_FORM);
        req.body(map);
        req.url(getFullUrl(config.getTokenUrl()));
        return req;
    }

    /**
     * curl \
     * -d "client_id=<YOUR_CLIENT_ID>" \
     * -d "client_secret=<YOUR_CLIENT_SECRET>" \
     * -d "grant_type=client_credentials" \
     * "http://localhost:8080/realms/master/protocol/openid-connect/token"
     */
    protected HttpRequest newServiceLogin(LoginRequest request) {
        HttpRequest req = new HttpRequest();
        Map<String, Object> map = new HashMap<>();
        map.put(SsoConstants.GRANT_TYPE, SsoConstants.CLIENT_CREDENTIALS_GRANT);
        map.put(SsoConstants.CLIENT_ID, config.getClientId());
        map.put(SsoConstants.CLIENT_SECRET, config.getClientSecret());
        req.dataType(HttpApiConstants.DATA_TYPE_FORM);
        req.body(map);
        req.url(getFullUrl(config.getTokenUrl()));
        return req;
    }

    protected String getFullUrl(String url) {
        return config.getFullUrl(url);
    }

    protected IUserContext buildUserContext(AccessTokenResponse response) {
        if (!StringHelper.isEmpty(response.getError())) {
            throw new NopException(ERR_AUTH_SSO_ACCESS_FAIL).param(ARG_ERROR, response.getError());
        }

        UserContextImpl userContext = new UserContextImpl();
        String userName = (String) response.getOtherClaims().get(AuthApiConstants.JWT_CLAIMS_USERNAME);
        userContext.setUserName(userName);
        userContext.setUserId(userName);
        userContext.setSessionId(response.getSessionState());
        userContext.setAttr(KEY_ACCESS_TOKEN, response.getAccessToken());
        userContext.setAttr(KEY_REFRESH_TOKEN, response.getRefreshToken());

        Map<String, Object> realmAccess = (Map<String, Object>)
                response.getOtherClaims().get(AuthApiConstants.JWT_CLAIMS_REALM_ACCESS);
        if (realmAccess != null) {
            Set<String> roles = ConvertHelper.toCsvSet(realmAccess.get(AuthApiConstants.JWT_CLAIMS_ROLES));
            if (roles != null) {
                userContext.setRoles(roles);
            }
        }
        return userContext;
    }

    @Override
    public CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request) {
        AuthToken authToken = parseAuthToken(request.getAccessToken());
        if (authToken == null)
            return FutureHelper.success(null);

        CompletionStage<Void> future = doLogout(logoutType, new SessionInfo(authToken.getUserName(), authToken.getSessionId()));

        return future;
    }

    @Override
    protected CompletionStage<Void> doLogout(int logoutType, SessionInfo sessionInfo) {
        CompletionStage<Void> future = super.doLogout(logoutType, sessionInfo);

        if (logoutType != LOGOUT_TYPE_SSO_CALLBACK) {
            CompletionStage<IUserContext> promise = doGetUserContext(sessionInfo.getSessionId());
            promise.thenCompose(userContext -> {
                if (userContext == null)
                    return FutureHelper.success(null);
                HttpRequest req = newLogoutRequest(userContext);
                return httpClient.fetchAsync(req, null);
            });
            return FutureHelper.waitAll(Arrays.asList(future, promise));
        }
        return future;
    }

    protected HttpRequest newLogoutRequest(IUserContext userContext) {
        HttpRequest req = new HttpRequest();
        req.bearerToken(userContext.getAccessToken());
        Map<String, Object> map = new HashMap<>();
        map.put(SsoConstants.REFRESH_TOKEN_VALUE, userContext.getRefreshToken());
        map.put(SsoConstants.CLIENT_ID, config.getClientId());
        if (!StringHelper.isEmpty(config.getClientSecret())) {
            map.put(SsoConstants.CLIENT_SECRET, config.getClientSecret());
        }
        req.dataType(HttpApiConstants.DATA_TYPE_FORM);
        req.body(map);
        req.url(getFullUrl(config.getLogoutUrl()));
        return req;
    }

    @Override
    public String generateVerifyCode(String verifySecret) {
        throw new UnsupportedOperationException("nop.err.auth.not-impl");
    }

    @Override
    public AuthToken parseAuthToken(String accessToken) {
        return JwtHelper.parseToken(accessToken, new SigningKeyResolver() {
            @Override
            public Key resolveSigningKey(JwsHeader header, Claims claims) {
                return keyLocator.getPublicKey(header.getKeyId());
            }

            @Override
            public Key resolveSigningKey(JwsHeader header, String plaintext) {
                return null;
            }
        });
    }

    @Override
    public String refreshToken(IUserContext userContext, AuthToken authToken) {
        String refreshToken = userContext.getRefreshToken();
        if (refreshToken == null)
            return null;

        LOG.debug("nop.auth.sso.refresh-token:refreshToken={}", refreshToken);

        HttpRequest req = newRefreshTokenRequest(refreshToken);
        return FutureHelper.syncGet(httpClient.fetchAsync(req, null).thenApply(res -> {
            AccessTokenResponse response = res.getBodyAsBean(AccessTokenResponse.class);
            if (response.getError() != null) {
                LOG.debug("nop.auth.sso.refresh-token-fail:error={}", response.getError());
                return null;
            }
            userContext.setAccessToken(response.getAccessToken());
            userContext.setRefreshToken(response.getRefreshToken());
            return response.getAccessToken();
        }));
    }

    protected HttpRequest newRefreshTokenRequest(String refreshToken) {
        HttpRequest req = new HttpRequest();
        req.setMethod(HttpApiConstants.METHOD_POST);

        Map<String, Object> map = new HashMap<>();
        map.put(SsoConstants.REFRESH_TOKEN_VALUE, refreshToken);
        map.put(SsoConstants.GRANT_TYPE, SsoConstants.REFRESH_TOKEN_GRANT);
        map.put(SsoConstants.CLIENT_ID, config.getClientId());
        if (!StringHelper.isEmpty(config.getClientSecret())) {
            map.put(SsoConstants.CLIENT_SECRET, config.getClientSecret());
        }
        req.dataType(HttpApiConstants.DATA_TYPE_FORM);
        req.body(map);
        req.url(config.getFullUrl(config.getTokenUrl()));
        return req;
    }
}