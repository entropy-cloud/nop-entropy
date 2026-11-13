package io.nop.http.client.oauth.enhancer;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.oauth.Oauth2TokenResponseBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpClientEnhancer;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DelegateHttpClient;
import io.nop.http.client.oauth.HttpClientAuthConfig;
import io.nop.http.client.oauth.HttpClientAuthConfigs;
import io.nop.http.client.oauth.OauthProviderConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;

import static io.nop.http.api.HttpApiConstants.CONTENT_TYPE_FORM_URLENCODED;
import static io.nop.http.client.oauth.HttpOauthConfigs.DEFAULT_EXPIRE_GAP;
import static io.nop.http.client.oauth.HttpOauthErrors.ARG_PROVIDER;
import static io.nop.http.client.oauth.HttpOauthErrors.ERR_HTTP_OAUTH_NO_USER_CONTEXT;
import static io.nop.http.client.oauth.HttpOauthErrors.ERR_HTTP_OAUTH_UNKNOWN_PROVIDER;

public class AddAccessTokenHttpClientEnhancer implements IHttpClientEnhancer {

    private HttpClientAuthConfigs authConfigs;

    private final Map<String, Oauth2TokenResponseBean> authTokens = new ConcurrentHashMap<>();

    /**
     * 4.3-MA-08（plan 2560 裁定 A）：分 provider 锁对象——token 刷新的 singleflight
     * 粒度从「全 provider 一把 authTokens 监视器」收敛为每 provider 一把锁，锁内
     * token HTTP 往返不再跨 provider 串行化。
     */
    private final ConcurrentHashMap<String, Object> providerLocks = new ConcurrentHashMap<>();

    public void setAuthConfigs(HttpClientAuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }

    @Override
    public IHttpClient enhance(IHttpClient client) {
        return new EnhancedClient(client);
    }

    public class EnhancedClient extends DelegateHttpClient {

        public EnhancedClient(IHttpClient httpClient) {
            super(httpClient);
        }

        public HttpClientAuthConfigs getAuthConfigs() {
            return authConfigs;
        }

        /**
         * 4.3-MA-08（plan 2560 裁定 A）：token 注入统一钩子从 onFetchBegin 上移到
         * DelegateHttpClient.onRequestBegin——fetchAsync/downloadAsync/uploadAsync/
         * fetchServerEventFlow 四入口全部经 onRequestBegin（前三者经各 onXxxBegin 缺省
         * 委托，SSE 直调），下载/上传/SSE 面不再绕过 token 注入与 propagateHeaders
         * （C3-008 round2 失效面收敛）。
         */
        @Override
        protected void onRequestBegin(HttpRequest request, ICancelToken cancelToken) {
            String url = request.getUrlNoQuery();
            Map.Entry<String, HttpClientAuthConfig> authConfig = authConfigs.getHttpClientConfigForUrl(url);
            if (authConfig == null)
                return;

            Set<String> propagateHeaders = authConfig.getValue().getPropagateHeaders();
            if (propagateHeaders != null) {
                this.processPropagateHeaders(request, propagateHeaders);
            }

            String accessToken = getAccessToken(getHttpClient(), authConfig);
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

        /**
         * 4.3-MA-15（plan 2560 裁定 H）同步抛包络：onRequestBegin 在 fetchAsync/
         * downloadAsync/uploadAsync 的同步段内执行，「返回 CompletionStage 的方法不得
         * 同步抛」——包络为 failed future。fetchServerEventFlow 返回 Flow.Publisher，
         * 包络契约以 onError 信号落地（裁定 H SSE 面）。
         */
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            try {
                return super.fetchAsync(request, cancelToken);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            try {
                return super.downloadAsync(request, targetFile, options, cancelToken);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            try {
                return super.uploadAsync(request, inputFile, options, cancelToken);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            try {
                return super.fetchServerEventFlow(request, cancelToken);
            } catch (Throwable e) {
                return subscriber -> subscriber.onError(e);
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
                if (providerConfig == null) {
                    // 4.3-C6-008（plan 2560 裁定 A 同 diff）：provider 配置已移除时
                    // 同步驱逐残留 token 缓存条目，消除无配置条目的滞留面
                    authTokens.remove(config.getOauthProvider());
                    throw new NopException(ERR_HTTP_OAUTH_UNKNOWN_PROVIDER)
                            .param(ARG_PROVIDER, config.getOauthProvider());
                }

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

            // 4.3-MA-08（plan 2560 裁定 A）：token fetch 留在分 provider 锁内（保住
            // per-provider singleflight 防并发击穿 token endpoint；fetch-outside-then-put
            // 会并发重复取 token——显式否决），但移出 authTokens 全局监视器——
            // 锁内同步 HTTP 往返不再跨 provider 串行化（C1-002/C3-008/C6-008）。
            // fetchToken 经 getHttpClient() raw 委托发起，无递归（已核验）。
            Object lock = providerLocks.computeIfAbsent(providerName, k -> new Object());
            synchronized (lock) {
                token = authTokens.get(providerName);
                if (token != null && !token.isExpired(gap))
                    return token.getAccessToken();

                Oauth2TokenResponseBean response = fetchToken(client, providerConfig);
                authTokens.put(providerName, response);
                return response.getAccessToken();
            }
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
}
