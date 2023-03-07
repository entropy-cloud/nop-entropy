/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.login;

import io.nop.api.core.annotations.config.ConfigBean;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigBean
public class UserContextConfig {
    private int maxLoginUserCount = 10000;

    private Duration sessionTimeout = Duration.of(60, ChronoUnit.MINUTES);

    private Duration verifyCodeTimeout = Duration.of(60, ChronoUnit.SECONDS);

    private Duration loginFailTimeout = Duration.of(10, ChronoUnit.MINUTES);

    private String verifyKey = "s**j)KimerhNFH#_>DFLL#(4894M388H4FMNDee--39";

    public String getVerifyKey() {
        return verifyKey;
    }

    public void setVerifyKey(String verifyKey) {
        this.verifyKey = verifyKey;
    }

    public int getMaxLoginUserCount() {
        return maxLoginUserCount;
    }

    public void setMaxLoginUserCount(int maxLoginUserCount) {
        this.maxLoginUserCount = maxLoginUserCount;
    }

    public Duration getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Duration getVerifyCodeTimeout() {
        return verifyCodeTimeout;
    }

    public void setVerifyCodeTimeout(Duration verifyCodeTimeout) {
        this.verifyCodeTimeout = verifyCodeTimeout;
    }

    public Duration getLoginFailTimeout() {
        return loginFailTimeout;
    }

    public void setLoginFailTimeout(Duration loginFailTimeout) {
        this.loginFailTimeout = loginFailTimeout;
    }
}