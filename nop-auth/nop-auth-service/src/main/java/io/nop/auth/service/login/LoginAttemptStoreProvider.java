/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.ILoginAttemptStore;
import io.nop.auth.service.mfa.store.MfaStoreErrors;

import java.util.Map;

/**
 * 登录失败计数 store 装配点（design nop-auth §3.2，plan 2274 Phase 2——复刻
 * {@code MfaStoreProvider} / {@code SendCodeRateLimiterProvider} 的 collect-beans 模式）。
 * <p>
 * 实现按命名型扩展点前缀 {@code nopLoginAttemptStore_} 注册（Local 注册于
 * auth-core-defaults，Redis 注册于 auth-service 并经 {@code ioc:condition} 条件激活——
 * classpath 无 nosql 时不注册不加载，ai-dev/lessons/15），按
 * {@code nop.auth.login-attempt.store-type}（{@code local|redis}，默认 {@code local}）
 * 选择。请求类型未注册显式抛异常（fail-closed，不静默回退）。
 */
public class LoginAttemptStoreProvider {

    public static final String STORE_TYPE_LOCAL = "local";
    public static final String STORE_TYPE_REDIS = "redis";

    private String storeType = STORE_TYPE_LOCAL;

    private Map<String, ILoginAttemptStore> attemptStores;

    public void setStoreType(String storeType) { this.storeType = storeType; }
    public String getStoreType() { return storeType; }

    public void setAttemptStores(Map<String, ILoginAttemptStore> attemptStores) { this.attemptStores = attemptStores; }
    public Map<String, ILoginAttemptStore> getAttemptStores() { return attemptStores; }

    public ILoginAttemptStore getLoginAttemptStore() {
        if (attemptStores == null || attemptStores.isEmpty())
            throw failClosed("no login attempt store implementations registered");
        ILoginAttemptStore store = lookup(attemptStores, storeType);
        if (store == null)
            throw failClosed("store-type=" + storeType + " not found among " + attemptStores.keySet());
        return store;
    }

    private ILoginAttemptStore lookup(Map<String, ILoginAttemptStore> stores, String type) {
        if (type == null) return null;
        ILoginAttemptStore s = stores.get(type);
        if (s != null) return s;
        s = stores.get("_" + type);
        if (s != null) return s;
        for (Map.Entry<String, ILoginAttemptStore> e : stores.entrySet()) {
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
                .param(MfaStoreErrors.ARG_REASON, "LoginAttemptStore: " + reason);
    }
}
