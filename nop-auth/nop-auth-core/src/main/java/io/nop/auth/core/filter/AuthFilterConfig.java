/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.filter;

import io.nop.auth.core.AuthCoreConstants;
import io.nop.commons.util.StringHelper;

import java.util.Collections;
import java.util.List;

public class AuthFilterConfig {
    /**
     * 缺省情况下是否都是公开链接，不用检查是否已登录
     */
    private boolean defaultPublic = false;

    private List<String> publicPaths = Collections.emptyList();

    /**
     * 需要检查权限的链接模式
     */
    private List<String> authPaths = Collections.emptyList();

    /**
     * 服务路径。服务总是需要上下文
     */
    private List<String> servicePaths = Collections.emptyList();

    private boolean servicePublic = false;

    /**
     * 发现没有登录时会重定向到登录页
     */
    private String loginUrl;

    private String logoutUrl;

    private String oauthCallbackPath;

    /**
     * 如果非空，则允许通过cookie传递auth token
     */
    private String authCookie = AuthCoreConstants.COOKIE_NOP_TOKEN;

    private boolean rememberMeEnabled;
    private String rememberMeCookie = "nop-me";


    public boolean isRememberMeEnabled() {
        return rememberMeEnabled;
    }

    public void setRememberMeEnabled(boolean rememberMeEnabled) {
        this.rememberMeEnabled = rememberMeEnabled;
    }

    public String getRememberMeCookie() {
        return rememberMeCookie;
    }

    public void setRememberMeCookie(String rememberMeCookie) {
        this.rememberMeCookie = rememberMeCookie;
    }

    public String getAuthCookie() {
        return authCookie;
    }

    public void setAuthCookie(String authCookie) {
        this.authCookie = authCookie;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public boolean isDefaultPublic() {
        return defaultPublic;
    }

    public void setDefaultPublic(boolean defaultPublic) {
        this.defaultPublic = defaultPublic;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? Collections.emptyList() : publicPaths;
    }

    public List<String> getAuthPaths() {
        return authPaths;
    }

    public void setAuthPaths(List<String> authPaths) {
        this.authPaths = authPaths == null ? Collections.emptyList() : authPaths;
    }

    public List<String> getServicePaths() {
        return servicePaths;
    }

    public void setServicePaths(List<String> servicePaths) {
        this.servicePaths = servicePaths == null ? Collections.emptyList() : servicePaths;
    }

    public boolean isServicePublic() {
        return servicePublic;
    }

    public void setServicePublic(boolean servicePublic) {
        this.servicePublic = servicePublic;
    }

    public boolean isServicePath(String path) {
        for (String servicePath : servicePaths) {
            if (StringHelper.matchSimplePattern(path, servicePath))
                return true;
        }
        return false;
    }

    public boolean isPublicPath(String path) {
        for (String publicUrl : publicPaths) {
            if (StringHelper.matchSimplePattern(path, publicUrl))
                return true;
        }

        // 登录页必须是public的
        if (loginUrl != null && loginUrl.startsWith(path)) {
            int pos = loginUrl.indexOf('?');
            if (pos > 0) {
                if (loginUrl.substring(0, pos).equals(path))
                    return true;
            } else {
                if (loginUrl.equals(path))
                    return true;
            }
        }

        for (String authUrl : authPaths) {
            if (StringHelper.matchSimplePattern(path, authUrl))
                return false;
        }

        if (defaultPublic) {
            return true;
        }

        return false;
    }

    public String getOauthCallbackPath() {
        return oauthCallbackPath;
    }

    public void setOauthCallbackPath(String oauthCallbackPath) {
        this.oauthCallbackPath = oauthCallbackPath;
    }
}