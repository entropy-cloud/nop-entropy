/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStoreConfig;
import io.nop.commons.util.StringHelper;
import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlService;

import jakarta.inject.Inject;

/**
 * {@link MfaChallengeStore} 的 Redis 实现（设计 §3.3）。
 * <p>
 * <b>写路径约束（设计 §3.3 裁决）</b>：
 * <ul>
 *   <li>写一律 {@code INosqlKeyValueOperations.putExAsync}（→ Lettuce {@code psetex}，TTL 在此设定）。</li>
 *   <li>读（peek）用 <b>不刷新 TTL</b> 的 {@code get}（→ Lettuce {@code get}，普通 GET，非 GETEX），
 *       禁用 {@code getExAsync}/{@code NosqlCache.getAsync}（后者会 GETEX 滑动刷新，让 300s 失效）。</li>
 *   <li>原子消费用 {@code removeIfMatch}（Lua CAS）。</li>
 *   <li>原子失败计数用 {@link INosqlCounter#incrementAsync}（INCRBY，方案 A），
 *       TTL 经 {@code setTimeoutAsync}(PEXPIRE) 在首次递增时设定一次，后续递增不刷新（窄竞态见设计 §3.3）。</li>
 * </ul>
 * <p>
 * 连通性证据（无嵌入式 Redis 测试基建）：本类逐条委托 nop-nosql 原语——
 * {@code create → putExAsync → psetex}、{@code peek → get}、{@code consume → get + removeIfMatch(Lua)}、
 * {@code incrFailCount → INosqlCounter.increment → INCRBY + setTimeoutAsync → pexpire}；
 * 原子性由 nop-nosql 自身测试建立的 INCRBY / Lua CAS 语义保证，行为由 Local 实现覆盖。
 */
public class RedisMfaChallengeStore implements MfaChallengeStore {

    private static final String CHALLENGE_PREFIX = "mfa:challenge:";
    private static final String FAIL_PREFIX = "mfa:fail:";

    private final INosqlService nosql;
    private final MfaChallengeStoreConfig config;

    @Inject
    public RedisMfaChallengeStore(INosqlService nosql) {
        this(nosql, new MfaChallengeStoreConfig());
    }

    public RedisMfaChallengeStore(INosqlService nosql, MfaChallengeStoreConfig config) {
        this.nosql = nosql;
        this.config = config;
    }

    private String challengeKey(String token) {
        return CHALLENGE_PREFIX + token;
    }

    private String failKey(String token) {
        return FAIL_PREFIX + token;
    }

    private long ttlMillis() {
        return config.getExpireSeconds() * 1000L;
    }

    @Override
    public String create(String userId, String mfaType, int loginType, String tenantId, String phone) {
        long now = System.currentTimeMillis();
        String token = StringHelper.generateUUID();
        MfaChallenge c = new MfaChallenge(token, userId, mfaType, loginType, tenantId, phone, now, now + ttlMillis());
        // 写一律 putExAsync（psetex，TTL 在此设定），sync 确保返回 token 前已持久化
        FutureHelper.syncGet(nosql.putExAsync(challengeKey(token), c, ttlMillis()));
        return token;
    }

    @Override
    public MfaChallenge peek(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return null;
        // 不刷新 TTL 的 get（普通 GET，非 GETEX）。Redis TTL 兜底过期；entry.expireAt 二次判定。
        Object obj = nosql.get(challengeKey(challengeToken));
        if (obj == null)
            return null;
        if (!(obj instanceof MfaChallenge))
            throw new NopException(MfaStoreErrors.ERR_MFA_STORE_INVALID_VALUE_TYPE)
                    .param(MfaStoreErrors.ARG_ACTUAL_TYPE, obj.getClass().getName());
        MfaChallenge c = (MfaChallenge) obj;
        if (c.getExpireAt() <= System.currentTimeMillis()) {
            nosql.remove(challengeKey(challengeToken));
            return null;
        }
        return c;
    }

    @Override
    public int incrFailCount(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return 0;
        // 方案 A：复用 INosqlCounter.increment（INCRBY，原子）
        INosqlCounter counter = nosql.counter(failKey(challengeToken));
        long val = counter.increment(1);
        if (val == 1L) {
            // 首次递增设定 TTL 一次（PEXPIRE）；后续递增不刷新（设计 §3.3 窄竞态裁决）
            FutureHelper.syncGet(nosql.setTimeoutAsync(failKey(challengeToken), ttlMillis()));
        }
        return (int) Math.min(val, Integer.MAX_VALUE);
    }

    @Override
    public MfaChallenge consume(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return null;
        String key = challengeKey(challengeToken);
        Object obj = nosql.get(key);
        if (obj == null)
            return null;
        if (!(obj instanceof MfaChallenge))
            throw new NopException(MfaStoreErrors.ERR_MFA_STORE_INVALID_VALUE_TYPE)
                    .param(MfaStoreErrors.ARG_ACTUAL_TYPE, obj.getClass().getName());
        MfaChallenge c = (MfaChallenge) obj;
        if (c.getExpireAt() <= System.currentTimeMillis()) {
            nosql.remove(key);
            return null;
        }
        // 原子 CAS 删除：仅当值仍匹配时删除（一次性语义）
        boolean removed = nosql.removeIfMatch(key, c);
        return removed ? c : null;
    }
}
