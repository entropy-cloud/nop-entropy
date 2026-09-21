/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.ILoginAttemptStore;
import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlService;

import jakarta.inject.Inject;

import java.time.Duration;

/**
 * {@link ILoginAttemptStore} 的 Redis 实现（design nop-auth §3.2，plan 2274 Phase 2）——
 * 多节点部署下全实例共享登录失败计数，锁号阈值不再被节点数稀释。
 * <p>
 * 原子递增 = {@link INosqlCounter#increment}(INCRBY，单键原子)；首次递增经
 * {@code setTimeoutAsync} 设 TTL（{@code loginFailTimeout}，后续不刷新——有界竞态对齐
 * {@code RedisMfaChallengeStore.incrFailCount} 先例裁定）；reset = 删除键（对应原
 * {@code loginFailCache.remove}）；get = {@code counter.get()}（不存在返回 0）。
 * <p>
 * set（覆盖写）为兼容保留面（生产路径仅 increment/reset/get 消费）：经
 * {@code reset(value)} + TTL 重设实现。
 */
public class RedisLoginAttemptStore implements ILoginAttemptStore {

    public static final String KEY_PREFIX = "auth:login-fail:";

    private final INosqlService nosql;
    private Duration loginFailTimeout = Duration.ofMinutes(10);

    @Inject
    public RedisLoginAttemptStore(INosqlService nosql) {
        this.nosql = nosql;
    }

    /** beans 装配用：TTL 与 {@code UserContextConfig.loginFailTimeout} 对齐。 */
    public void setLoginFailTimeout(Duration loginFailTimeout) {
        this.loginFailTimeout = loginFailTimeout;
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }

    @Override
    public int getLoginFailCount(String key) {
        long v = nosql.counter(redisKey(key)).get();
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    @Override
    public void setLoginFailCount(String key, int count) {
        INosqlCounter counter = nosql.counter(redisKey(key));
        counter.reset(count);
        setTtl(redisKey(key));
    }

    @Override
    public void resetLoginFailCount(String key) {
        nosql.remove(redisKey(key));
    }

    @Override
    public int incrementLoginFailCount(String key) {
        INosqlCounter counter = nosql.counter(redisKey(key));
        long v = counter.increment(1);
        if (v == 1L) {
            // 首次递增设 TTL（PEXPIRE；后续递增不刷新——与 RedisMfaChallengeStore 同款裁定）
            setTtl(redisKey(key));
        }
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    private void setTtl(String redisKey) {
        if (loginFailTimeout == null || loginFailTimeout.isNegative() || loginFailTimeout.isZero())
            return;
        FutureHelper.syncGet(nosql.setTimeoutAsync(redisKey, loginFailTimeout.toMillis()));
    }
}
