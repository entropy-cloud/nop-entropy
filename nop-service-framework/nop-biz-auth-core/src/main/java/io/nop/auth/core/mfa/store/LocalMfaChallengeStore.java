/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link MfaChallengeStore} 的 Local（JVM 内）实现。
 * <p>
 * 基于 {@link ConcurrentHashMap} 原子 compute，无 TTL 竞态：每条 challenge 的 expireAt 在
 * create 时固化为绝对时间戳，peek 不会修改它（结构性"不刷新 TTL"）。失败计数用独立的
 * AtomicInteger map，原子 increment 不丢计数。
 */
public class LocalMfaChallengeStore implements MfaChallengeStore {

    static final class Entry {
        final MfaChallenge challenge;
        final long expireAtMillis;

        Entry(MfaChallenge challenge, long expireAtMillis) {
            this.challenge = challenge;
            this.expireAtMillis = expireAtMillis;
        }

        boolean expired(long now) {
            return expireAtMillis <= now;
        }
    }

    private final Map<String, Entry> challenges = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failCounts = new ConcurrentHashMap<>();
    private final MfaChallengeStoreConfig config;

    public LocalMfaChallengeStore() {
        this(new MfaChallengeStoreConfig());
    }

    public LocalMfaChallengeStore(MfaChallengeStoreConfig config) {
        this.config = config;
    }

    @Override
    public String create(String userId, String mfaType, int loginType, String tenantId, String phone) {
        long now = System.currentTimeMillis();
        long ttlMs = config.getExpireSeconds() * 1000L;
        String token = StringHelper.generateUUID();
        MfaChallenge c = new MfaChallenge(token, userId, mfaType, loginType, tenantId, phone, now, now + ttlMs);
        challenges.put(token, new Entry(c, now + ttlMs));
        return token;
    }

    @Override
    public MfaChallenge peek(String challengeToken) {
        Entry e = challenges.get(challengeToken);
        if (e == null)
            return null;
        if (e.expired(System.currentTimeMillis())) {
            challenges.remove(challengeToken, e);
            failCounts.remove(challengeToken);
            return null;
        }
        // peek 不修改 expireAtMillis —— 结构性"不刷新 TTL"
        return e.challenge;
    }

    @Override
    public int incrFailCount(String challengeToken) {
        // 验证 challenge 存在且未过期，否则计数无意义（返回 0 让调用方随后 consume/丢弃）
        Entry e = challenges.get(challengeToken);
        if (e == null || e.expired(System.currentTimeMillis()))
            return 0;
        return failCounts.computeIfAbsent(challengeToken, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public MfaChallenge consume(String challengeToken) {
        Entry e = challenges.remove(challengeToken);
        failCounts.remove(challengeToken);
        if (e == null)
            return null;
        if (e.expired(System.currentTimeMillis()))
            return null;
        return e.challenge;
    }

    // ---- package-private test helpers（仅 Local 行为断言用）----

    /** 返回 challenge 的绝对过期时间戳，用于断言 peek 不刷新 TTL；不存在返回 -1。 */
    long peekExpireAtMillis(String challengeToken) {
        Entry e = challenges.get(challengeToken);
        return e == null ? -1L : e.expireAtMillis;
    }

    /** 当前失败计数（测试用）。 */
    int failCount(String challengeToken) {
        AtomicInteger c = failCounts.get(challengeToken);
        return c == null ? 0 : c.get();
    }
}
