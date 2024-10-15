package io.nop.http.oauth.interceptor;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.oauth.Oauth2TokenResponseBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpClientInterceptor;
import io.nop.http.oauth.HttpClientAuthConfig;
import io.nop.http.oauth.HttpClientAuthConfigs;
import io.nop.http.oauth.OauthProviderConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.http.api.HttpApiConstants.CONTENT_TYPE_FORM_URLENCODED;
import static io.nop.http.oauth.HttpOauthConfigs.DEFAULT_EXPIRE_GAP;
import static io.nop.http.oauth.HttpOauthErrors.ARG_PROVIDER;
import static io.nop.http.oauth.HttpOauthErrors.ERR_HTTP_OAUTH_NO_USER_CONTEXT;
import static io.nop.http.oauth.HttpOauthErrors.ERR_HTTP_OAUTH_UNKNOWN_PROVIDER;

public class AddAccessTokenHttpClientInterceptor implements IHttpClientInterceptor {

    private HttpClientAuthConfigs authConfigs;

    private final Map<String, Oauth2TokenResponseBean> authTokens = new ConcurrentHashMap<>();

    public void setAuthConfigs(HttpClientAuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }

    @Override
    public void onBeginFetch(IHttpClient client, HttpRequest request, ICancelToken cancelToken) {
        String url = request.getUrlNoQuery();
        Map.Entry<String, HttpClientAuthConfig> authConfig = authConfigs.getHttpClientConfigForUrl(url);
        if (authConfig == null)
            return;

        Set<String> propagateHeaders = authConfig.getValue().getPropagateHeaders();
        if (propagateHeaders != null) {
            this.processPropagateHeaders(request, propagateHeaders);
        }

        String accessToken = getAccessToken(client, authConfig);
        if (accessToken != null)
            request.setBearerToken(accessToken);
    }

    protected void processPropagateHeaders(HttpRequest request, Set<String> propagateHeaders) {
        IContext context = ContextProvider.currentContext();
        if (context != null) {
            Map<String, Object> headers = context.getPropagateRpcHeaders();
            if (headers != null) {
                propagateHeaders.forEach(name -> {
                    Object value = headers.get(name);
                    if (value != null) {
                        request.header(name, value);
                    }
                });
            }
        }
    }

    protected String getAccessToken(IHttpClient client, Map.Entry<String, HttpClientAuthConfig> authConfig) {
        HttpClientAuthConfig config = authConfig.getValue();
        if (config.isUseContextAccessToken()) {
            IUserContext userContext = IUserContext.get();
            if (userContext == null)
                throw new NopException(ERR_HTTP_OAUTH_NO_USER_CONTEXT);
            return userContext.getAccessToken();
        } else if (config.getOauthProvider() != null) {
            OauthProviderConfig providerConfig = authConfigs.getOauthProvider(config.getOauthProvider());
            if (providerConfig == null)
                throw new NopException(ERR_HTTP_OAUTH_UNKNOWN_PROVIDER)
                        .param(ARG_PROVIDER, config.getOauthProvider());

            long gap = providerConfig.getExpireGap();
            if (gap == 0)
                gap = DEFAULT_EXPIRE_GAP;

            return getAccessTokenFromProvider(client, config.getOauthProvider(), providerConfig, gap);
        } else {
            // 没有oauthProvider也不从上下文中获取accessToken，则表示不需要增加accessToken
            return null;
        }
    }

    protected String getAccessTokenFromProvider(IHttpClient client, String providerName, OauthProviderConfig providerConfig, long gap) {
        Oauth2TokenResponseBean token = authTokens.get(providerName);
        if (token != null && !token.isExpired(gap))
            return token.getAccessToken();

        synchronized (authTokens) {
            token = authTokens.get(providerName);
            if (token != null && !token.isExpired(gap))
                return token.getAccessToken();

            Oauth2TokenResponseBean response = fetchToken(client, providerConfig);
            authTokens.put(providerName, response);
        }
        return token.getAccessToken();
    }

    protected Oauth2TokenResponseBean fetchToken(IHttpClient client, OauthProviderConfig providerConfig) {
        HttpRequest request = HttpRequest.post(providerConfig.getTokenUri());
        request.header(HttpApiConstants.HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM_URLENCODED);
        request.setBody(buildTokenRequestBody(providerConfig));
        return client.fetch(request, null).getBodyAsBean(Oauth2TokenResponseBean.class);
    }

    private String buildTokenRequestBody(OauthProviderConfig providerConfig) {
        return "grant_type=client_credentials&client_id=" + encodeURL(providerConfig.getClientId())
                + "&client_secret=" + encodeURL(providerConfig.getClientSecret()) + "&scope=" + encodeURL(providerConfig.getScope());
    }

    private String encodeURL(String value) {
        if (ApiStringHelper.isEmpty(value))
            return "";
        return ApiStringHelper.encodeURL(value);
    }
}
