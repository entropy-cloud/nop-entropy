/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.service.mfa.store.MfaStoreErrors;

import java.util.Map;

/**
 * 发码限流器装配点（design nop-auth §3.1，plan 2274 Phase 1——复刻 {@code MfaStoreProvider}
 * 的 collect-beans 装配模式）。
 * <p>
 * 实现按命名型扩展点前缀 {@code nopAuthRateLimiter_} 注册，本类经
 * {@code ioc:collect-beans name-prefix} 收集（纯字符串匹配 bean id，不加载类），按
 * {@code nop.auth.rate-limit.store-type}（{@code local}|{@code redis}，默认 {@code local}）
 * 选择。请求的类型未注册则显式抛异常（fail-closed，不静默回退）。
 * <p>
 * 类加载安全（ai-dev/lessons/15）：本类零 {@code io.nop.nosql.core} 类型引用。Redis 实现
 * 的 bean 定义经 {@code ioc:condition}（{@code store-type=redis} + {@code on-class}）条件
 * 激活——classpath 无 nosql 时根本不注册、不被收集、类不加载。
 */
public class SendCodeRateLimiterProvider {

    public static final String STORE_TYPE_LOCAL = "local";
    public static final String STORE_TYPE_REDIS = "redis";

    private String storeType = STORE_TYPE_LOCAL;

    private Map<String, ISendCodeRateLimiter> rateLimiters;

    public void setStoreType(String storeType) { this.storeType = storeType; }
    public String getStoreType() { return storeType; }

    public void setRateLimiters(Map<String, ISendCodeRateLimiter> rateLimiters) { this.rateLimiters = rateLimiters; }
    public Map<String, ISendCodeRateLimiter> getRateLimiters() { return rateLimiters; }

    public ISendCodeRateLimiter getRateLimiter() {
        if (rateLimiters == null || rateLimiters.isEmpty())
            throw failClosed("no rate limiter implementations registered");
        ISendCodeRateLimiter limiter = lookup(rateLimiters, storeType);
        if (limiter == null)
            throw failClosed("store-type=" + storeType + " not found among " + rateLimiters.keySet());
        return limiter;
    }

    private ISendCodeRateLimiter lookup(Map<String, ISendCodeRateLimiter> limiters, String type) {
        if (type == null) return null;
        ISendCodeRateLimiter s = limiters.get(type);
        if (s != null) return s;
        s = limiters.get("_" + type);
        if (s != null) return s;
        for (Map.Entry<String, ISendCodeRateLimiter> e : limiters.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            String normalized = key.startsWith("_") ? key.substring(1) : key;
            if (type.equalsIgnoreCase(key) || type.equalsIgnoreCase(normalized)) return e.getValue();
        }
        return null;
    }

    private NopException failClosed(String reason) {
        return new NopException(MfaStoreErrors.ERR_MFA_STORE_REDIS_BACKEND_NOT_AVAILABLE)
                .param(MfaStoreErrors.ARG_STORE_TYPE, storeType)
                .param(MfaStoreErrors.ARG_REASON, "SendCodeRateLimiter: " + reason);
    }
}
