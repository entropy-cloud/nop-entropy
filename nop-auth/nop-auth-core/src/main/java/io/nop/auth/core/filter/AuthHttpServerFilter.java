/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.filter;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.InternalLoginRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.utils.AuthMDCHelper;
import io.nop.auth.core.AuthCoreConfigs;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_NOT_AUTHORIZED;

/**
 * 负责检查登录信息，并初始化IUserContext和IContext上下文对象
 */
public class AuthHttpServerFilter implements IHttpServerFilter {
    static final Logger LOG = LoggerFactory.getLogger(AuthHttpServerFilter.class);

    private static final String OAUTH_TOKEN_REQUEST_STATE = "OAuth_Token_Request_State";
    private static final AtomicLong counter = new AtomicLong();

    private static String genStateCode() {
        return counter.getAndIncrement() + "/" + UUID.randomUUID();
    }


    private AuthFilterConfig config;

    private ILoginService loginService;

    public void setConfig(AuthFilterConfig config) {
        this.config = config;
    }

    @Inject
    public void setLoginService(ILoginService loginService) {
        this.loginService = loginService;
    }

    @Override
    public int order() {
        return NORMAL_PRIORITY - 100;
    }

    @Override
    public CompletionStage<Void> filterAsync(IHttpServerContext routeContext,
                                             Supplier<CompletionStage<Void>> next) {
        try {
            return _filterAsync(routeContext, next);
        } finally {
            // 防御性清理环境。在内部应该已经正常清理
            AuthMDCHelper.unbindMDC();
        }
    }

    private CompletionStage<Void> _filterAsync(IHttpServerContext routeContext, Supplier<CompletionStage<Void>> next) {
        String path = routeContext.getRequestPath();
        // 如果是退出链接，则没有必要检查是否已登录。
        if (checkLogoutUrl(routeContext))
            return handlePublicPath(routeContext, next);

        if (config.isPublicPath(path)) {
            LOG.debug("nop.route.request-public-url:path={}", path);
            return handlePublicPath(routeContext, next);
        }

        // 耗时的操作不能在IO线程上执行
        return routeContext.executeBlocking(() -> {

            try {
                if (hasOAuthCode(routeContext)) {
                    return loginWithOAuthCode(routeContext).thenCompose(userContext -> {
                        String accessToken = userContext.getAccessToken();
                        AuthToken authToken = loginService.parseAuthToken(accessToken);
                        return handleUserContext(userContext, routeContext, next, authToken);
                    }).exceptionally(ex -> {
                        LOG.error("nop.auth.init-user-context-fail", ex);
                        handleError(routeContext, ex);
                        throw NopException.adapt(ex);
                    });
                }

                AuthToken authToken = parseAuthToken(routeContext);

                if (authToken == null) {
                    return responseNotLogin(routeContext);
                }

                return getUserContextAsync(authToken)
                        .thenCompose(userContext -> {
                            return handleUserContext(userContext, routeContext, next, authToken);
                        }).exceptionally(ex -> {
                            LOG.error("nop.auth.init-user-context-fail", ex);
                            handleError(routeContext, ex);
                            throw NopException.adapt(ex);
                        });
            } catch (Exception ex) {
                LOG.error("nop.auth.init-user-context-fail", ex);
                handleError(routeContext, ex);
                throw NopException.adapt(ex);
            }
        }).thenApply(v -> null);
    }

    protected CompletionStage<Void> handlePublicPath(IHttpServerContext routeContext, Supplier<CompletionStage<Void>> next) {
        return next.get();
    }

    private void handleError(IHttpServerContext routeContext, Throwable ex) {
        if (ex instanceof NopLoginException) {
            NopLoginException err = (NopLoginException) ex;
            if (!routeContext.isResponseSent()) {
                this.responseNotLogin(routeContext, err.getErrorCode(), err.getDescription());
            }
        } else {
            routeContext.sendResponse(500, "Server Error");
        }
    }

    boolean checkLogoutUrl(IHttpServerContext routeContext) {
        if (!StringHelper.isEmpty(config.getLogoutUrl())) {
            if (routeContext.getRequestPath().startsWith(config.getLogoutUrl())) {
                if (config.getAuthCookie() != null) {
                    routeContext.removeCookie(config.getAuthCookie());
                }
                return true;
            }
        }
        return false;
    }

    protected boolean isNeedRefresh(AuthToken authToken) {
        if ((authToken.getExpireAt() - CoreMetrics.currentTimeMillis()) * 2 < (authToken.getExpireSeconds() * 1000L))
            return true;
        return false;
    }


    protected CompletionStage<IUserContext> loginWithOAuthCode(IHttpServerContext routeContext) {
        String code = routeContext.getQueryParam(AuthCoreConstants.PARAM_CODE);
        LoginRequest login = new InternalLoginRequest();
        login.setSsoToken(code);
        login.setLoginType(AuthApiConstants.LOGIN_TYPE_SSO);

        Map<String, Object> headers = routeContext.getRequestHeaders();
        return loginService.loginAsync(login, headers);
    }

    CompletionStage<Void> handleUserContext(IUserContext userContext, IHttpServerContext routeContext,
                                            Supplier<CompletionStage<Void>> next, AuthToken authToken) {
        if (userContext == null)
            return responseNotLogin(routeContext);

        // 如果已经超过生命周期的一半，则需要主动更新
        boolean needRefresh = isNeedRefresh(authToken);
        if (needRefresh) {
            LOG.debug("nop.auth.token-need-refresh:sessionId={},expireAt={}", authToken.getSessionId(), new Timestamp(authToken.getExpireAt()));
        }

        IContext ctx = initUserContext(userContext, routeContext);

        AuthMDCHelper.bindMDC(userContext);

        try {
            if (needRefresh) {
                String accessToken = loginService.refreshToken(userContext, authToken);
                routeContext.setResponseHeader(IHttpServerContext.HEADER_X_ACCESS_TOKEN, accessToken);
                if (config.getAuthCookie() != null) {
                    addCookie(config.getAuthCookie(), accessToken, routeContext);
                }
            } else if (config.getAuthCookie() != null) {
                addCookie(config.getAuthCookie(), authToken.getToken(), routeContext);
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            // runOnContext会创建一个任务队列。如果异步调用过程中使用了同步等待，则内部实现会利用任务队列来避免出现死锁。
            ctx.runOnContext(() -> {
                CompletionStage<Void> promise = next.get().whenComplete((v, e) -> {
                    try {
                        loginService.flushUserContextAsync(userContext);
                    } finally {
                        ctx.close();
                    }
                });
                FutureHelper.bindResult(promise, future);
            });

            return future;
        } finally {
            AuthMDCHelper.unbindMDC();
        }
    }

    protected void addCookie(String name, String value, IHttpServerContext context) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE.get());
        context.addCookie("Lax", cookie);
    }

    protected boolean hasOAuthCode(IHttpServerContext routeContext) {
        return routeContext.getQueryParam(AuthCoreConstants.PARAM_CODE) != null
                && routeContext.getQueryParam(AuthCoreConstants.PARAM_STATE) != null;
    }

    protected AuthToken parseAuthToken(IHttpServerContext routeContext) {
        String token = getAuthToken(routeContext);
        if (token == null) {
            // 如果没有传递登录凭证
            return null;
        }

        AuthToken authToken = null;
        try {
            authToken = loginService.parseAuthToken(token);
        } catch (Exception e) {
            LOG.debug("nop.invalid-auth-token:token={}", token, e);
            return null;
        }
        return authToken;
    }

    protected CompletionStage<IUserContext> getUserContextAsync(AuthToken authToken) {
        return loginService.getUserContextAsync(authToken);
    }

    protected String getAuthToken(IHttpServerContext context) {
        String token = getAuthTokenFromHeader(context);
        if (token == null) {
            token = getAuthTokenFromCookie(context);
        }
        return token;
    }

    protected String getAuthTokenFromHeader(IHttpServerContext context) {
        String token = context.getRequestStringHeader(IHttpServerContext.HEADER_AUTHORIZATION);
        if (StringHelper.isEmpty(token)) {
            token = context.getRequestStringHeader(IHttpServerContext.HEADER_X_ACCESS_TOKEN);
        }
        if (StringHelper.isEmpty(token))
            return null;

        if (StringHelper.startsWithIgnoreCase(token, IHttpServerContext.BEARER_PREFIX)) {
            token = token.substring(IHttpServerContext.BEARER_PREFIX.length());
        }

        if (StringHelper.isEmpty(token))
            return null;

        return token;
    }

    protected String getAuthTokenFromCookie(IHttpServerContext context) {
        if (config.getAuthCookie() != null) {
            String cookie = context.getCookie(config.getAuthCookie());
            if (!StringHelper.isEmpty(cookie))
                return cookie;
        }
        return null;
    }

    protected CompletionStage<Void> responseNotLogin(IHttpServerContext context) {
        return responseNotLogin(context, ERR_AUTH_NOT_AUTHORIZED.getErrorCode(), ERR_AUTH_NOT_AUTHORIZED.getDescription());
    }

    protected CompletionStage<Void> responseNotLogin(IHttpServerContext context, String errorCode, String errorDesc) {
        if (isAjaxRequest(context) || StringHelper.isEmpty(config.getLoginUrl())) {
            String locale = (String) context.getRequestHeader(ApiConstants.HEADER_LOCALE);
            if (StringHelper.isEmpty(locale))
                locale = I18nMessageManager.instance().getDefaultLocale();

            ApiResponse<Object> error = new ApiResponse<>();
            error.setStatus(401);
            error.setCode(errorCode);
            String msg = ErrorMessageManager.instance().getErrorDescription(locale,
                    errorCode, Collections.emptyMap());
            if (msg == null) {
                msg = errorDesc;
            }
            error.setMsg(msg);
            error.setData(Collections.emptyMap());

            context.setResponseHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_JSON);
            if (context.getRequestUrl().endsWith("/graphql")) {
                GraphQLResponseBean response = new GraphQLResponseBean();
                response.setErrorCode(error.getCode());
                response.setMsg(error.getMsg());
                response.setStatus(error.getStatus());
                context.sendResponse(401, JsonTool.stringify(response));
            } else {
                context.sendResponse(401, JsonTool.stringify(error));
            }
        } else {
            // auth?client_id=xx&response_type=code&state=xxx&redirect_uri=zz&scope=vv
            String url = config.getLoginUrl();
            url = StringHelper.replace(url, AuthCoreConstants.PLACEHOLDER_HOST, context.getHost());
            url = StringHelper.replace(url, AuthCoreConstants.PLACEHOLDER_BACK_URL, StringHelper.encodeURL(context.getRequestUrl()));
            url = StringHelper.replace(url, AuthCoreConstants.PLACEHOLDER_ERR_CODE, StringHelper.encodeURL(errorCode));
            String state = genStateCode();
            int pos = url.lastIndexOf('#');
            if (pos < 0) {
                url = StringHelper.appendQuery(url, "state=" + state);
            } else {
                String fragment = url.substring(pos);
                url = url.substring(0, pos);
                url = StringHelper.appendQuery(url, "state=" + state);
                url += fragment;
            }
            addCookie(OAUTH_TOKEN_REQUEST_STATE, state, context);
            context.sendRedirect(url);
        }
        return FutureHelper.success(null);
    }

    protected boolean isAjaxRequest(IHttpServerContext context) {
        String header = (String) context.getRequestHeader(IHttpServerContext.HEADER_X_REQUESTED_WITH);
        if (!StringHelper.isEmpty(header))
            return true;
        header = (String) context.getRequestHeader(IHttpServerContext.HEADER_ACCEPT);
        return HttpApiConstants.CONTENT_TYPE_JSON.equals(header);
    }

    protected IContext initUserContext(IUserContext userContext, IHttpServerContext context) {

        String locale = context.getRequestStringHeader(ApiConstants.HEADER_LOCALE);
        if (locale == null) {
            locale = userContext.getLocale();
        }

        IContext ctx = ContextProvider.getOrCreateContext();
        context.setContext(ctx);
        ctx.setUserId(userContext.getUserId());
        ctx.setUserName(userContext.getUserName());
        ctx.setTenantId(userContext.getTenantId());
        ctx.setUserRefNo(userContext.getUserName());
        ctx.setLocale(locale);
        ctx.setTimezone(userContext.getTimeZone());

        IUserContext.set(userContext);
        return ctx;
    }
}