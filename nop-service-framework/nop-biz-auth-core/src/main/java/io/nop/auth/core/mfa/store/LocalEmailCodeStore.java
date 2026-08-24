/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link EmailCodeStore} 的 Local（JVM 内）实现（设计 §5.3.3，W15-impl）。
 * <p>
 * 基于 {@link ConcurrentHashMap} 原子 compute：6 位随机码 + 绝对过期时间戳，失败计数独立。
 * 三态语义与 {@code LocalSmsCodeStore} 逐条对齐（平行实现，不泛化合并）。
 */
public class LocalEmailCodeStore implements EmailCodeStore {

    static final class Entry {
        final String code;
        final long expireAtMillis;
        final AtomicInteger failCount;

        Entry(String code, long expireAtMillis) {
            this.code = code;
            this.expireAtMillis = expireAtMillis;
            this.failCount = new AtomicInteger(0);
        }

        boolean expired(long now) {
            return expireAtMillis <= now;
        }
    }

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final EmailCodeStoreConfig config;

    public LocalEmailCodeStore() {
        this(new EmailCodeStoreConfig());
    }

    public LocalEmailCodeStore(EmailCodeStoreConfig config) {
        this.config = config;
    }

    @Override
    public String send(String key) {
        long now = System.currentTimeMillis();
        long ttlMs = config.getExpireSeconds() * 1000L;
        // 6 位数字验证码（前导零保留）
        String code = String.format("%06d", MathHelper.secureRandom().nextInt(1_000_000));
        ensureCapacity(now);
        // 覆盖写（重发作废旧码）
        codes.put(key, new Entry(code, now + ttlMs));
        return code;
    }

    /**
     * 容量上界：条目只在被再次访问时惰性清理，"发出但从未回来验证"的条目会永久驻留。
     * 超过 maxEntries 时先清扫过期条目；仍满（攻击灌互异 key）则按插入序驱逐若干条目
     * 保证上界——被驱逐的有效码需重发，属可接受的攻击降级（优于 OOM）。
     */
    private void ensureCapacity(long now) {
        int maxEntries = config.getMaxEntries();
        if (codes.size() < maxEntries) {
            return;
        }
        final long cutoff = now;
        codes.entrySet().removeIf(e -> e.getValue().expired(cutoff));
        if (codes.size() >= maxEntries) {
            int over = codes.size() - maxEntries + 1;
            java.util.Iterator<String> it = codes.keySet().iterator();
            while (over-- > 0 && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    @Override
    public CodeVerifyResult verify(String key, String code) {
        if (StringHelper.isEmpty(code))
            return CodeVerifyResult.MISMATCH;
        long now = System.currentTimeMillis();
        CodeVerifyResult[] result = new CodeVerifyResult[1];
        codes.compute(key, (k, e) -> {
            if (e == null || e.expired(now)) {
                result[0] = CodeVerifyResult.EXPIRED;
                return null; // remove
            }
            if (e.code.equals(code)) {
                result[0] = CodeVerifyResult.VALID;
                return null; // 原子消费
            }
            int fails = e.failCount.incrementAndGet();
            if (fails >= config.getMaxAttempts()) {
                result[0] = CodeVerifyResult.MISMATCH;
                return null; // 超限作废
            }
            result[0] = CodeVerifyResult.MISMATCH;
            return e;
        });
        return result[0];
    }

    @Override
    public void consume(String key) {
        codes.remove(key);
    }

    // ---- package-private test helpers ----

    String rawCode(String key) {
        Entry e = codes.get(key);
        return e == null ? null : e.code;
    }

    int failCount(String key) {
        Entry e = codes.get(key);
        return e == null ? 0 : e.failCount.get();
    }
}
