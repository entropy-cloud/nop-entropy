/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.time.CoreMetrics;
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
    private MfaChallengeStoreConfig config;

    @Inject
    public RedisMfaChallengeStore(INosqlService nosql) {
        this(nosql, new MfaChallengeStoreConfig());
    }

    public RedisMfaChallengeStore(INosqlService nosql, MfaChallengeStoreConfig config) {
        this.nosql = nosql;
        this.config = config;
    }

    /** beans 装配用（nopMfaChallengeConfig 同一配置 bean，对齐 local/db 实现）。 */
    public void setConfig(MfaChallengeStoreConfig config) {
        this.config = config;
    }

    private String challengeKey(String token) {
        return CHALLENGE_PREFIX + token;
    }

    /** 操作级票派生键（SETNX 承载一次性，独立 TTL=op-ticket-expire，设计 §3.3）。 */
    private String ticketKey(String token) {
        return CHALLENGE_PREFIX + token + ":v";
    }

    private String failKey(String token) {
        return FAIL_PREFIX + token;
    }

    private long ttlMillis() {
        return config.getExpireSeconds() * 1000L;
    }

    private long opTicketMillis() {
        return config.getOpTicketExpireSeconds() * 1000L;
    }

    @Override
    public String create(String scene, String userId, String mfaType, int loginType, String tenantId, String phone,
                         String payload) {
        long now = CoreMetrics.currentTimeMillis();
        String token = StringHelper.generateUUID();
        MfaChallenge c = new MfaChallenge(token, userId, mfaType, loginType, tenantId, phone, now, now + ttlMillis());
        c.setScene(scene);
        c.setPayload(payload);
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
        if (c.getExpireAt() <= CoreMetrics.currentTimeMillis()) {
            nosql.remove(challengeKey(challengeToken));
            return null;
        }
        // 票键存在 ⇒ 已验证且在票窗口内（键 TTL=op-ticket-expire 由 SETNX 设定，过期即失效）；
        // 票键不存在 ⇒ verifiedAt=null（未验证，或票已过期的 challenge 自身仍存活）。
        // 显式赋值（含清 null）：peek 对同一对象的重复调用幂等，不残留旧票状态。
        // 对调用方不变式：peek().verifiedAt 非空 ⇒ 票在窗口内（三实现一致）
        Object ticket = nosql.get(ticketKey(challengeToken));
        if (ticket instanceof Number) {
            c.setVerifiedAt(((Number) ticket).longValue());
        } else {
            c.setVerifiedAt(null);
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
        // 必须以本次 get 读到的存储原对象做 CAS 比对值（不得复用 peek 修饰过的对象——
        // peek 可能已写入 verifiedAt，序列化值与存储值不一致导致 removeIfMatch 永不命中）
        Object obj = nosql.get(key);
        if (obj == null)
            return null;
        if (!(obj instanceof MfaChallenge))
            throw new NopException(MfaStoreErrors.ERR_MFA_STORE_INVALID_VALUE_TYPE)
                    .param(MfaStoreErrors.ARG_ACTUAL_TYPE, obj.getClass().getName());
        MfaChallenge c = (MfaChallenge) obj;
        if (c.getExpireAt() <= CoreMetrics.currentTimeMillis()) {
            nosql.remove(key);
            return null;
        }
        // 原子 CAS 删除：仅当值仍匹配时删除（一次性语义）
        boolean removed = nosql.removeIfMatch(key, c);
        if (removed) {
            // 票键随 challenge 消费一并清理（best-effort；键自身短 TTL 兜底）
            nosql.remove(ticketKey(challengeToken));
            return c;
        }
        return null;
    }

    @Override
    public boolean markVerified(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return false;
        // challenge 必须存在且未过期（票不脱离 challenge 单独存在）
        Object obj = nosql.get(challengeKey(challengeToken));
        if (!(obj instanceof MfaChallenge))
            return false;
        if (((MfaChallenge) obj).getExpireAt() <= CoreMetrics.currentTimeMillis()) {
            nosql.remove(challengeKey(challengeToken));
            return false;
        }
        // 派生票键 SETNX（putIfAbsentExAsync → SETNX+PX 原子）：恰首个调用者成功；
        // 重复调用键已存在返回 false（票不续命）。键值=验证时刻（peek 映射为 verifiedAt）。
        Boolean ok = FutureHelper.syncGet(
                nosql.putIfAbsentExAsync(ticketKey(challengeToken), CoreMetrics.currentTimeMillis(), opTicketMillis()));
        return Boolean.TRUE.equals(ok);
    }
}
