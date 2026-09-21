/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;

import static io.nop.commons.cache.CacheConfig.newConfig;

/**
 * {@link ILoginAttemptStore} 的 Local（JVM 内）实现（design nop-auth §3.2，plan 2274 Phase 2）。
 * <p>
 * TTL 语义与原 {@code loginFailCache}（{@code expireAfterWrite(loginFailTimeout)}）一致：
 * 每次写入（含递增）重置 TTL。原子递增在实例锁内 read-modify-write——原
 * {@code LoginServiceImpl.loginFailCountLock} 的职责内聚于此（plan 2274 B2 裁定），
 * 并发递增 N 次读取值恰为 N（{@code TestLoginFailCountAtomicity} 语义保持）。
 * <p>
 * 配置：{@code UserContextConfig.loginFailTimeout}；{@link #refreshConfig()} 跟随配置刷新
 * （保持原 {@code AbstractUserContextCache.refreshConfig} 对 fail-count 缓存的刷新语义）。
 */
public class LocalLoginAttemptStore implements ILoginAttemptStore, IConfigRefreshable {

    private final ICache<String, int[]> cache;
    private final UserContextConfig config;

    public LocalLoginAttemptStore(UserContextConfig config) {
        this.config = config;
        this.cache = LocalCache.newCache("login-attempt-cache",
                newConfig(config.getMaxLoginUserCount() * 2)
                        .expireAfterWrite(config.getLoginFailTimeout()).useMetrics(), null);
    }

    /** 缺省配置构造（手工 wiring / 共享缺省单例路径）。 */
    public LocalLoginAttemptStore() {
        this(new UserContextConfig());
    }

    @Override
    public void refreshConfig() {
        cache.getConfig().setExpireAfterWrite(config.getLoginFailTimeout());
        cache.refreshConfig();
    }

    @Override
    public int getLoginFailCount(String key) {
        int[] v = cache.get(key);
        return v == null ? 0 : v[0];
    }

    @Override
    public void setLoginFailCount(String key, int count) {
        cache.put(key, new int[]{count});
    }

    @Override
    public void resetLoginFailCount(String key) {
        cache.remove(key);
    }

    @Override
    public int incrementLoginFailCount(String key) {
        // 原子区：读取-递增-写回在同一临界区（丢失更新防护），put 重置 TTL（对齐原 loginFailCache）
        synchronized (this) {
            int[] v = cache.get(key);
            int next = (v == null ? 0 : v[0]) + 1;
            cache.put(key, new int[]{next});
            return next;
        }
    }

    /** 测试辅助：当前缓存条目数。 */
    int estimatedSize() {
        return (int) cache.estimatedSize();
    }
}
