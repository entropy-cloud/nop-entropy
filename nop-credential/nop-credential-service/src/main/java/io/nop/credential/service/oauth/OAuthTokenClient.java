/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.oauth;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.SourceLocation;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.credential.crypto.CredentialErrors;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth 2.0 令牌端点协议客户端（授权码换取 / refresh grant）。
 *
 * <p>表单构造参照 {@code OAuthLoginServiceImpl.newLoginRequest} 的协议形态（{@code IHttpClient} +
 * form data + grant_type/client_id/client_secret/code/redirect_uri/refresh_token），
 * <b>不引入 nop-auth-sso 依赖</b>（设计 §3.3 复用边界：协议级参照，非组件复用）。
 *
 * <p>失败语义全部 fail-closed：令牌端点返回 error 字段、HTTP 非 2xx、传输异常均抛
 * {@link NopException}（{@code ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED}），不静默返回 null。
 */
public class OAuthTokenClient {

    /** OAuth 协议常量（对齐 SsoConstants 字面值，独立声明避免依赖）。 */
    public static final String GRANT_TYPE = "grant_type";
    public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String CODE = "code";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String REFRESH_TOKEN = "refresh_token";

    /**
     * 由 NopIoC 注入。字段为 protected 以兼容 NopIoC 字段注入限制。
     */
    @Inject
    protected IHttpClient httpClient;

    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 以授权码换取 token 集（机密客户端：携带 client_id + client_secret + 回传 redirect_uri）。
     */
    public OAuthTokenResponse exchangeAuthorizationCode(String tokenEndpoint, String clientId,
                                                        String clientSecret, String code, String redirectUri) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put(GRANT_TYPE, GRANT_TYPE_AUTHORIZATION_CODE);
        form.put(CODE, code);
        form.put(CLIENT_ID, clientId);
        form.put(CLIENT_SECRET, clientSecret);
        form.put(REDIRECT_URI, redirectUri);
        return fetchToken(tokenEndpoint, form);
    }

    /**
     * 以 refreshToken 刷新 token 集（grant_type=refresh_token，携带实例的 clientId/clientSecret）。
     */
    public OAuthTokenResponse refresh(String tokenEndpoint, String clientId,
                                      String clientSecret, String refreshToken) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put(GRANT_TYPE, GRANT_TYPE_REFRESH_TOKEN);
        form.put(REFRESH_TOKEN, refreshToken);
        form.put(CLIENT_ID, clientId);
        form.put(CLIENT_SECRET, clientSecret);
        return fetchToken(tokenEndpoint, form);
    }

    private OAuthTokenResponse fetchToken(String tokenEndpoint, Map<String, Object> form) {
        HttpRequest request = new HttpRequest();
        request.setMethod(HttpApiConstants.METHOD_POST);
        request.dataType(HttpApiConstants.DATA_TYPE_FORM);
        request.body(form);
        request.url(tokenEndpoint);

        IHttpResponse response;
        try {
            response = httpClient.fetch(request, null);
        } catch (Exception e) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED, e)
                    .param(CredentialErrors.ARG_TOKEN_ENDPOINT, tokenEndpoint)
                    .param(CredentialErrors.ARG_ERROR, e.getMessage());
        }

        OAuthTokenResponse token;
        try {
            String body = response.getBodyAsString();
            if (body == null || body.isEmpty()) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED)
                        .param(CredentialErrors.ARG_TOKEN_ENDPOINT, tokenEndpoint)
                        .param(CredentialErrors.ARG_ERROR, "empty response body, httpStatus=" + response.getHttpStatus());
            }
            token = (OAuthTokenResponse) JSON.parseToBean(SourceLocation.fromPath(tokenEndpoint),
                    body, OAuthTokenResponse.class);
        } catch (NopException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED, e)
                    .param(CredentialErrors.ARG_TOKEN_ENDPOINT, tokenEndpoint)
                    .param(CredentialErrors.ARG_ERROR, "invalid token response: " + e.getMessage());
        }

        // token endpoint 错误响应（error 字段）显式抛错，不静默
        if (token.getError() != null) {
            String detail = token.getErrorDescription() != null
                    ? token.getError() + ": " + token.getErrorDescription()
                    : token.getError();
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED)
                    .param(CredentialErrors.ARG_TOKEN_ENDPOINT, tokenEndpoint)
                    .param(CredentialErrors.ARG_ERROR, detail);
        }
        if (token.getAccessToken() == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED)
                    .param(CredentialErrors.ARG_TOKEN_ENDPOINT, tokenEndpoint)
                    .param(CredentialErrors.ARG_ERROR, "token response missing access_token");
        }
        return token;
    }
}
