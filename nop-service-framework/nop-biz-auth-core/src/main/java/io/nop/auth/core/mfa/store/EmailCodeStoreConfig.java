/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * EmailCodeStore 配置（TTL + 失败计数上限，设计 §5.3.3——与 {@link SmsCodeStoreConfig}
 * 同形）。配置来源 {@code nop.auth.email-code.*}（nop-auth-service 装配）。
 * max-attempts 是 store 内部失败计数：验证码错误达到上限后作废（需重发）。
 */
public class EmailCodeStoreConfig {

    /** 验证码有效期（秒），默认 300。 */
    private int expireSeconds = 300;

    /** 同一验证码最大尝试次数，超限作废（需重发），默认 5。 */
    private int maxAttempts = 5;

    /**
     * 内存条目数上限，默认 100000。"发出但未回来验证"的条目只被惰性清理，
     * 公网刷库场景必须有容量上界防 OOM：超限时先清扫过期条目，仍满则驱逐最旧
     * 插入的条目（被驱逐的有效码需重发，可接受的攻击降级）。
     */
    private int maxEntries = 100_000;

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(int expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }
}
