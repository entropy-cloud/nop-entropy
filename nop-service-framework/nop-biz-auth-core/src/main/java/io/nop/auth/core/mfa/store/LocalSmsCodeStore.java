/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link SmsCodeStore} 的 Local（JVM 内）实现。
 * <p>
 * 基于 {@link ConcurrentHashMap} 原子 compute：6 位随机码 + 绝对过期时间戳，失败计数独立。
 */
public class LocalSmsCodeStore implements SmsCodeStore {

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
    private final SmsCodeStoreConfig config;

    public LocalSmsCodeStore() {
        this(new SmsCodeStoreConfig());
    }

    public LocalSmsCodeStore(SmsCodeStoreConfig config) {
        this.config = config;
    }

    @Override
    public String send(String key) {
        long now = CoreMetrics.currentTimeMillis();
        long ttlMs = config.getExpireSeconds() * 1000L;
        // 6 位数字验证码（前导零保留）
        String code = String.format("%06d", MathHelper.secureRandom().nextInt(1_000_000));
        // 覆盖写（重发作废旧码）
        codes.put(key, new Entry(code, now + ttlMs));
        return code;
    }

    @Override
    public CodeVerifyResult verify(String key, String code) {
        if (StringHelper.isEmpty(code))
            return CodeVerifyResult.MISMATCH;
        long now = CoreMetrics.currentTimeMillis();
        Entry[] holder = new Entry[1];
        CodeVerifyResult[] result = new CodeVerifyResult[1];
        codes.compute(key, (k, e) -> {
            if (e == null || e.expired(now)) {
                result[0] = CodeVerifyResult.EXPIRED;
                return null; // remove
            }
            holder[0] = e;
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
