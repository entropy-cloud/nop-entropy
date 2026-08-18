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
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlService;

import jakarta.inject.Inject;

/**
 * {@link SmsCodeStore} 的 Redis 实现（设计 §3.3）。
 * <p>
 * 写路径约束同 {@link RedisMfaChallengeStore}：写一律 {@code putExAsync}（psetex），
 * 读用不刷新 TTL 的 {@code get}（普通 GET），成功原子消费用 {@code removeIfMatch}(Lua CAS)，
 * 失败计数用 {@link INosqlCounter#increment}(INCRBY，独立键 {@code sms:fail:{key}})。
 * <p>
 * key 隔离由调用方传入完整逻辑 key 保证（{@code login:{phone}} / {@code mfa:{userId}}）。
 */
public class RedisSmsCodeStore implements SmsCodeStore {

    private static final String CODE_PREFIX = "sms:code:";
    private static final String FAIL_PREFIX = "sms:fail:";

    private final INosqlService nosql;
    private final SmsCodeStoreConfig config;

    @Inject
    public RedisSmsCodeStore(INosqlService nosql) {
        this(nosql, new SmsCodeStoreConfig());
    }

    public RedisSmsCodeStore(INosqlService nosql, SmsCodeStoreConfig config) {
        this.nosql = nosql;
        this.config = config;
    }

    private String codeKey(String key) {
        return CODE_PREFIX + key;
    }

    private String failKey(String key) {
        return FAIL_PREFIX + key;
    }

    private long ttlMillis() {
        return config.getExpireSeconds() * 1000L;
    }

    @Override
    public String send(String key) {
        long now = System.currentTimeMillis();
        String code = String.format("%06d", MathHelper.secureRandom().nextInt(1_000_000));
        SmsCodeEntry entry = new SmsCodeEntry(code, now + ttlMillis());
        // 覆盖写 + 重置失败计数（重发即新周期）
        FutureHelper.syncGet(nosql.putExAsync(codeKey(key), entry, ttlMillis()));
        nosql.remove(failKey(key));
        return code;
    }

    @Override
    public CodeVerifyResult verify(String key, String code) {
        if (StringHelper.isEmpty(code))
            return CodeVerifyResult.MISMATCH;
        Object obj = nosql.get(codeKey(key));
        if (obj == null)
            return CodeVerifyResult.EXPIRED;
        if (!(obj instanceof SmsCodeEntry))
            throw new NopException(MfaStoreErrors.ERR_MFA_STORE_INVALID_VALUE_TYPE)
                    .param(MfaStoreErrors.ARG_ACTUAL_TYPE, obj.getClass().getName());
        SmsCodeEntry entry = (SmsCodeEntry) obj;

        if (entry.getExpireAtMillis() <= System.currentTimeMillis()) {
            nosql.remove(codeKey(key));
            nosql.remove(failKey(key));
            return CodeVerifyResult.EXPIRED;
        }

        if (entry.getCode().equals(code)) {
            // 成功：原子一次性消费（Lua CAS），并清失败计数。A2-audit D2-F1（P1 修复）：
            // 删除原子 ≠ 裁决原子——CAS 失败 = 并发竞争者已消费，败者必须返回 EXPIRED 而非 VALID
            // （与 Db 实现的 affected==0 → EXPIRED 语义对齐，双花防御）
            if (!nosql.removeIfMatch(codeKey(key), entry)) {
                return CodeVerifyResult.EXPIRED;
            }
            nosql.remove(failKey(key));
            return CodeVerifyResult.VALID;
        }

        // 不匹配：原子递增失败计数
        INosqlCounter counter = nosql.counter(failKey(key));
        long fails = counter.increment(1);
        if (fails == 1L) {
            FutureHelper.syncGet(nosql.setTimeoutAsync(failKey(key), ttlMillis()));
        }
        if (fails >= config.getMaxAttempts()) {
            // 超限作废：删验证码 + 计数
            nosql.remove(codeKey(key));
            nosql.remove(failKey(key));
        }
        return CodeVerifyResult.MISMATCH;
    }

    @Override
    public void consume(String key) {
        nosql.remove(codeKey(key));
        nosql.remove(failKey(key));
    }
}
