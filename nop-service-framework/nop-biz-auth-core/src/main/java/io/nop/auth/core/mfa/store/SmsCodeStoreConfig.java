/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * SmsCodeStore 配置（TTL + 失败计数上限）。
 * max-attempts 是 store 内部失败计数：验证码错误达到上限后作废（需重发），
 * 与 challenge 的 incrFailCount 相互独立（设计 §3.3）。
 */
public class SmsCodeStoreConfig {

    /** 验证码有效期（秒），默认 300（设计 §3.7）。 */
    private int expireSeconds = 300;

    /** 同一验证码最大尝试次数，超限作废（需重发），默认 5（设计 §3.7）。 */
    private int maxAttempts = 5;

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
}
