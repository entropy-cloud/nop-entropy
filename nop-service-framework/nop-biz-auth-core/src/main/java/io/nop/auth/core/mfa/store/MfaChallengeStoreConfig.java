/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

/**
 * MfaChallengeStore 配置（TTL）。max-attempts 由调用方在 W5 串联
 * （{@link MfaChallengeStore#incrFailCount} 返回当前计数，调用方判定是否作废 challenge）。
 */
public class MfaChallengeStoreConfig {

    /** challenge 有效期（秒），默认 300（设计 §3.7）。 */
    private int expireSeconds = 300;

    /**
     * 操作级票窗口（秒）：markVerified 后允许重试原操作的时间窗，默认 60
     * （设计 §3.1 结论 8，{@code nop.auth.operation-mfa.op-ticket-expire-seconds}）。
     * biz-auth-core 不依赖 NopAuthConfigs，配置连线由 nop-auth-service beans.xml 装配。
     */
    private int opTicketExpireSeconds = 60;

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(int expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public int getOpTicketExpireSeconds() {
        return opTicketExpireSeconds;
    }

    public void setOpTicketExpireSeconds(int opTicketExpireSeconds) {
        this.opTicketExpireSeconds = opTicketExpireSeconds;
    }
}
