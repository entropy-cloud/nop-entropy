/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.filter;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.InternalLoginRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.utils.AuthMDCHelper;
import io.nop.auth.core.AuthCoreConfigs;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.auth.core.AuthCoreConfigs.CFG_AUTH_USE_USER_ID_FOR_AUDIT_FIELDS;
import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_INVALID_REDIRECT_URI;
import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_NOT_AUTHORIZED;

/**
 * 负责检查登录信息，并初始化IUserContext和IContext上下文对象
 */
public class AuthHttpServerFilter implements IHttpServerFilter {
    static final Logger LOG = LoggerFactory.getLogger(AuthHttpServerFilter.class);

    private static final String OAUTH_TOKEN_REQUEST_STATE = "OAuth_Token_Request_State";
    private StateCookieHelper stateCookieHelper; // 新增


    private AuthFilterConfig config;

    private ILoginService loginService;

    private boolean autoRefreshToken;

    @InjectValue("@cfg:nop.auth.auto-refresh-token|true")
    public void setAutoRefreshToken(boolean autoRefreshToken) {
        this.autoRefreshToken = autoRefreshToken;
    }

    @Inject
    public void setConfig(AuthFilterConfig config) {
        this.config = config;
    }

    @Inject
    public void setLoginService(ILoginService loginService) {
        this.loginService = loginService;
    }

    public void setStateCookieHelper(StateCookieHelper stateCookieHelper) {
        this.stateCookieHelper = stateCookieHelper;
    }

    // 如果没有注入，提供默认实例
    @PostConstruct
    public void init() {
        if (stateCookieHelper == null) {
            this.stateCookieHelper = new StateCookieHelper();
        }
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
        boolean publicPath = config.isPublicPath(path);
        boolean logoutPath = checkLogoutUrl(routeContext);
        boolean servicePath = config.isServicePath(path);

        boolean requireContext = !publicPath || servicePath;

        if (!requireContext)
            return handlePublicPath(routeContext, next);

        if (config.isPublicPath(path)) {
            LOG.debug("nop.route.request-public-url:path={}", path);
            return handlePublicPath(routeContext, next);
        }

        IContext ctx = routeContext.getContext();
        Guard.notNull(ctx, "context not inited");

        // 耗时的操作不能在IO线程上执行
        return routeContext.executeBlocking(ctx.wrapWithContext(() -> {

            try {
                if (logoutPath)
                    return handlePublicPath(routeContext, next);

                if (hasOAuthCode(routeContext)) {
                    return processOAuthCode(routeContext, servicePath, next);
                }

                AuthToken authToken = parseAuthToken(routeContext);

                return getUserContextAsync(authToken, routeContext)
                        .thenCompose(userContext -> {
                            if (userContext == null) {
                                // 如果没有用户上下文，但是是公开服务，则创建一个sys用户上下文
                                if (config.isServicePublic() && servicePath) {
                                    userContext = newSysUserContext(routeContext);
                                }
                            }

                            if (userContext == null) {
                                return responseNotLogin(routeContext, servicePath);
                            } else {
                                return handleUserContext(userContext, routeContext, next, authToken);
                            }
                        }).exceptionally(ex -> {
                            LOG.error("nop.auth.init-user-context-fail", ex);
                            handleError(routeContext, ex, servicePath);
                            throw NopException.adapt(ex);
                        });
            } catch (Exception ex) {
                LOG.error("nop.auth.init-user-context-fail", ex);
                handleError(routeContext, ex, servicePath);
                throw NopException.adapt(ex);
            }
        })).thenApply(v -> null);
    }

    protected CompletionStage<Void> processOAuthCode(IHttpServerContext routeContext, boolean servicePath,
                                                     Supplier<CompletionStage<Void>> next) {
        return loginWithOAuthCode(routeContext).thenCompose(userContext -> {
            String accessToken = userContext.getAccessToken();
            AuthToken authToken = loginService.parseAuthToken(accessToken);
            String redirectUri = routeContext.getQueryParam(AuthCoreConstants.PARAM_REDIRECT_URI);
            if (redirectUri != null) {
                // 应验证redirectUri是否为允许的域名
                if (!isAllowedRedirectUri(redirectUri)) {
                    throw new NopException(ERR_AUTH_INVALID_REDIRECT_URI);
                }

                routeContext.sendRedirect(redirectUri);
                return next.get();
            }
            return handleUserContext(userContext, routeContext, next, authToken);
        }).exceptionally(ex -> {
            LOG.error("nop.auth.init-user-context-fail", ex);
            handleError(routeContext, ex, servicePath);
            throw NopException.adapt(ex);
        });
    }

    protected boolean isAllowedRedirectUri(String redirectUri) {
        if (StringHelper.isEmpty(redirectUri)) {
            LOG.debug("nop.auth.redirect-uri-empty");
            return false;
        }

        // 快速检查：默认允许相对路径
        if (isRelativePath(redirectUri)) {
            LOG.debug("nop.auth.redirect-relative-allowed:uri={}", redirectUri);
            return true;
        }

        // 绝对URL需要进一步验证
        return config.isAllowedRedirectUri(redirectUri);
    }

    /**
     * 检查是否为相对路径
     */
    private boolean isRelativePath(String uri) {
        // 相对路径特征：以/开头，且不包含://
        return uri.startsWith("/") && !uri.contains("://");
    }

    protected IUserContext newSysUserContext(IHttpServerContext context) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(AuthCoreConstants.USER_ID_SYS);
        userContext.setUserName(AuthCoreConstants.USER_ID_SYS);
        String tenantId = context.getRequestStringHeader(ApiConstants.HEADER_TENANT);
        userContext.setTenantId(tenantId);
        userContext.setTimeZone(context.getRequestStringHeader(ApiConstants.HEADER_TIMEZONE));
        userContext.setLocale(context.getRequestStringHeader(ApiConstants.HEADER_LOCALE));
        return userContext;
    }

    protected CompletionStage<Void> handlePublicPath(IHttpServerContext routeContext, Supplier<CompletionStage<Void>> next) {
        return next.get();
    }

    private void handleError(IHttpServerContext routeContext, Throwable ex, boolean forceJson) {
        if (ex instanceof NopLoginException) {
            NopLoginException err = (NopLoginException) ex;
            if (!routeContext.isResponseSent()) {
                this.responseNotLogin(routeContext, forceJson, err.getErrorCode(), err.getDescription());
            }
        } else {
            routeContext.sendResponse(500, "Server Error");
        }
    }

    boolean checkLogoutUrl(IHttpServerContext routeContext) {
        if (!StringHelper.isEmpty(config.getLogoutUrl())) {
            if (routeContext.getRequestPath().startsWith(config.getLogoutUrl())) {
                if (config.getAuthCookie() != null) {
                    // 内部实现是通过setMaxAge(0)等机制
                    routeContext.removeCookie(config.getAuthCookie());
                }
                return true;
            }
        }
        return false;
    }

    protected boolean isNeedRefresh(AuthToken authToken) {
        if (authToken == null || !autoRefreshToken) return false;

        long currentTime = CoreMetrics.currentTimeMillis();
        long timeToLive = authToken.getExpireAt() - currentTime;
        long halfLife = authToken.getExpireSeconds() * 500L; // 半衰期

        // 增加最小刷新间隔，避免频繁刷新
        return timeToLive < halfLife;
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
        // 如果已经超过生命周期的一半，则需要主动更新
        boolean needRefresh = isNeedRefresh(authToken);
        if (needRefresh) {
            LOG.debug("nop.auth.token-need-refresh:sessionId={},expireAt={}", authToken.getSessionId(), new Timestamp(authToken.getExpireAt()));
        }

        IContext ctx = initUserContext(userContext, routeContext);

        AuthMDCHelper.bindMDC(userContext);

        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            // runOnContext会创建一个任务队列。如果异步调用过程中使用了同步等待，则内部实现会利用任务队列来避免出现死锁。
            ctx.runOnContext(() -> {
                if (needRefresh) {
                    String accessToken = loginService.refreshToken(userContext, authToken);
                    // 如果不支持刷新，则可能返回null
                    if (accessToken != null) {
                        routeContext.setResponseHeader(IHttpServerContext.HEADER_X_ACCESS_TOKEN, accessToken);
                        if (config.getAuthCookie() != null) {
                            addCookie(config.getAuthCookie(), accessToken, routeContext);
                        }
                    }
                } else if (config.getAuthCookie() != null && authToken != null) {
                    // 如果cookie不一致，则增加cookie
                    if (!authToken.getToken().equals(getAuthTokenFromCookie(routeContext)))
                        addCookie(config.getAuthCookie(), authToken.getToken(), routeContext);
                }


                CompletionStage<Void> promise = next.get().whenComplete((v, e) -> {
                    loginService.flushUserContextAsync(userContext);
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
        cookie.setMaxAge(config.getCookieMaxAge());
        context.addCookie("Lax", cookie);
    }

    protected boolean hasOAuthCode(IHttpServerContext routeContext) {
        // 检查是否在OAuth回调路径上
        if (!StringHelper.isEmpty(config.getOauthCallbackPath())) {
            if (!config.getOauthCallbackPath().equals(routeContext.getRequestPath())) {
                return false;
            }
        }

        // 检查必需的OAuth参数
        String code = routeContext.getQueryParam(AuthCoreConstants.PARAM_CODE);
        String state = routeContext.getQueryParam(AuthCoreConstants.PARAM_STATE);

        if (StringHelper.isEmpty(code) || StringHelper.isEmpty(state)) {
            return false;
        }

        // 使用StateCookieHelper验证state
        return stateCookieHelper.validateAndClearState(state, routeContext);
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

    protected CompletionStage<IUserContext> getUserContextAsync(AuthToken authToken, IHttpServerContext routeContext) {
        if (authToken == null)
            return FutureHelper.success(null);
        return loginService.getUserContextAsync(authToken, routeContext.getRequestHeaders());
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

    protected CompletionStage<Void> responseNotLogin(IHttpServerContext context, boolean forceJson) {
        return responseNotLogin(context, forceJson, ERR_AUTH_NOT_AUTHORIZED.getErrorCode(), ERR_AUTH_NOT_AUTHORIZED.getDescription());
    }

    protected CompletionStage<Void> responseNotLogin(IHttpServerContext context, boolean forceJson, String errorCode, String errorDesc) {
        if (forceJson || isAjaxRequest(context) || StringHelper.isEmpty(config.getLoginUrl())) {
            writeJsonResponse(context, errorCode, errorDesc);
        } else {
            String url = buildLoginUrlWithState(context, errorCode);
            context.sendRedirect(url);
        }
        return FutureHelper.success(null);
    }

    /**
     * 构建带state参数的登录URL
     */
    protected String buildLoginUrlWithState(IHttpServerContext context, String errorCode) {
        String url = config.getLoginUrl();
        url = StringHelper.replace(url, AuthCoreConstants.PLACEHOLDER_HOST, context.getHost());
        url = StringHelper.replace(url, AuthCoreConstants.PLACEHOLDER_BACK_URL,
                StringHelper.encodeURL(context.getRequestUrl()));
        url = StringHelper.replace(url, AuthCoreConstants.PLACEHOLDER_ERR_CODE,
                StringHelper.encodeURL(errorCode));

        // 生成并设置state
        String state = stateCookieHelper.generateStateCode();
        stateCookieHelper.setStateCookie(state, context);

        // 添加state参数到URL
        int pos = url.lastIndexOf('#');
        if (pos < 0) {
            url = StringHelper.appendQuery(url, "state=" + StringHelper.encodeURL(state));
        } else {
            String fragment = url.substring(pos);
            url = url.substring(0, pos);
            url = StringHelper.appendQuery(url, "state=" + StringHelper.encodeURL(state));
            url += fragment;
        }

        return url;
    }

    protected void writeJsonResponse(IHttpServerContext context, String errorCode, String errorDesc) {
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
        if (!StringHelper.isEmpty(locale)) {
            ApiHeaders.checkLocaleFormat(locale);
        }

        IContext ctx = context.getContext();
        ctx.setUserId(userContext.getUserId());
        ctx.setUserName(userContext.getUserName());
        ctx.setTenantId(userContext.getTenantId());
        ctx.setLocale(locale);
        ctx.setTimezone(userContext.getTimeZone());

        if (CFG_AUTH_USE_USER_ID_FOR_AUDIT_FIELDS.get()) {
            ctx.setUserRefNo(userContext.getUserId());
        }

        IUserContext.set(userContext);
        return ctx;
    }
}