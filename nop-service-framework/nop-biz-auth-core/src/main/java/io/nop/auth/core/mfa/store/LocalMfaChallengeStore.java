/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public String create(String scene, String userId, String mfaType, int loginType, String tenantId, String phone,
                         String payload) {
        long now = CoreMetrics.currentTimeMillis();
        long ttlMs = config.getExpireSeconds() * 1000L;
        String token = StringHelper.generateUUID();
        MfaChallenge c = new MfaChallenge(token, userId, mfaType, loginType, tenantId, phone, now, now + ttlMs);
        c.setScene(scene);
        c.setPayload(payload);
        ensureCapacity(now);
        challenges.put(token, new Entry(c, now + ttlMs));
        return token;
    }

    /**
     * 容量上界：条目只在被再次访问时惰性清理，"发起但从未回来验证"的条目会永久驻留。
     * 超过 maxEntries 时先清扫过期条目；仍满（攻击灌互异 token）则按插入序驱逐若干条目
     * 保证上界——被驱逐的有效 challenge 需重新发起，属可接受的攻击降级（优于 OOM）。
     */
    private void ensureCapacity(long now) {
        int maxEntries = config.getMaxEntries();
        if (challenges.size() < maxEntries) {
            return;
        }
        final long cutoff = now;
        challenges.entrySet().removeIf(e -> e.getValue().expired(cutoff));
        if (challenges.size() >= maxEntries) {
            int over = challenges.size() - maxEntries + 1;
            java.util.Iterator<String> it = challenges.keySet().iterator();
            while (over-- > 0 && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        // 计数 map 随 challenge 清理，不独立膨胀
        if (!failCounts.isEmpty()) {
            failCounts.keySet().removeIf(k -> !challenges.containsKey(k));
        }
    }

    @Override
    public MfaChallenge peek(String challengeToken) {
        Entry e = challenges.get(challengeToken);
        if (e == null)
            return null;
        long now = CoreMetrics.currentTimeMillis();
        if (e.expired(now)) {
            challenges.remove(challengeToken, e);
            failCounts.remove(challengeToken);
            return null;
        }
        // 票窗口判定（设计 §3.3）：已验证票仅在 verifiedAt + op-ticket-expire 内可见，
        // 窗口外票失效即 challenge 整体失效（票不续命）
        Long verifiedAt = e.challenge.getVerifiedAt();
        if (verifiedAt != null && now >= verifiedAt + opTicketMs()) {
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
        if (e == null || e.expired(CoreMetrics.currentTimeMillis()))
            return 0;
        return failCounts.computeIfAbsent(challengeToken, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public MfaChallenge consume(String challengeToken) {
        Entry e = challenges.remove(challengeToken);
        failCounts.remove(challengeToken);
        if (e == null)
            return null;
        if (e.expired(CoreMetrics.currentTimeMillis()))
            return null;
        return e.challenge;
    }

    @Override
    public boolean markVerified(String challengeToken) {
        if (challengeToken == null)
            return false;
        long now = CoreMetrics.currentTimeMillis();
        AtomicBoolean marked = new AtomicBoolean(false);
        // JVM 原子 compute：恰好首个未验证调用者迁移成功（并发二次 false，票不续命）
        challenges.compute(challengeToken, (k, e) -> {
            if (e == null || e.expired(now))
                return e;
            if (e.challenge.getVerifiedAt() != null)
                return e;
            e.challenge.setVerifiedAt(now);
            marked.set(true);
            return e;
        });
        return marked.get();
    }

    private long opTicketMs() {
        return config.getOpTicketExpireSeconds() * 1000L;
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
