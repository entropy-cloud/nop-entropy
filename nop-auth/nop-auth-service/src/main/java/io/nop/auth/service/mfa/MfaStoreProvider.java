/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStoreConfig;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import io.nop.auth.service.mfa.store.MfaStoreErrors;
import io.nop.auth.service.mfa.store.RedisMfaChallengeStore;
import io.nop.auth.service.mfa.store.RedisSmsCodeStore;
import io.nop.nosql.core.INosqlService;

/**
 * MFA 存储装配点（设计 §3.3 / §3.7 {@code nop.auth.mfa.store-type}）。
 * <p>
 * <b>预留读取点</b>：{@link #setStoreType(String)} 由 W5 绑定到配置
 * {@code nop.auth.mfa.store-type}（默认 {@code local}）。本 phase 仅提供装配逻辑与默认值，
 * 配置条目本身在 W5 落地。
 * <p>
 * 选择规则：
 * <ul>
 *   <li>{@code local}（默认）→ Local 实现（JVM 内，零外部依赖）。</li>
 *   <li>{@code redis} + {@link INosqlService} 可用 → Redis 实现（委托 nop-nosql 原语）。</li>
 *   <li>{@code redis} 但 {@link INosqlService} 缺失 → <b>显式抛异常</b>（fail-closed，
 *       不静默回退到 Local 假装成功；见 No-Silent-No-Op 规则）。</li>
 * </ul>
 */
public class MfaStoreProvider {

    public static final String STORE_TYPE_LOCAL = "local";
    public static final String STORE_TYPE_REDIS = "redis";

    /** 预留读取点：W5 绑定 nop.auth.mfa.store-type，默认 local。 */
    private String storeType = STORE_TYPE_LOCAL;

    /** 可选：仅 redis 后端装配时注入。 */
    private INosqlService nosqlService;

    private MfaChallengeStoreConfig challengeConfig = new MfaChallengeStoreConfig();
    private SmsCodeStoreConfig smsConfig = new SmsCodeStoreConfig();

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    public String getStoreType() {
        return storeType;
    }

    public void setNosqlService(INosqlService nosqlService) {
        this.nosqlService = nosqlService;
    }

    public void setChallengeConfig(MfaChallengeStoreConfig challengeConfig) {
        this.challengeConfig = challengeConfig;
    }

    public void setSmsConfig(SmsCodeStoreConfig smsConfig) {
        this.smsConfig = smsConfig;
    }

    public MfaChallengeStore getMfaChallengeStore() {
        if (isRedis()) {
            return new RedisMfaChallengeStore(requireNosql(), challengeConfig);
        }
        return new LocalMfaChallengeStore(challengeConfig);
    }

    public SmsCodeStore getSmsCodeStore() {
        if (isRedis()) {
            return new RedisSmsCodeStore(requireNosql(), smsConfig);
        }
        return new LocalSmsCodeStore(smsConfig);
    }

    private boolean isRedis() {
        return STORE_TYPE_REDIS.equalsIgnoreCase(storeType);
    }

    private INosqlService requireNosql() {
        if (nosqlService == null) {
            // fail-closed：redis 被请求但后端不可用 —— 显式失败，不静默回退 Local
            throw new NopException(MfaStoreErrors.ERR_MFA_STORE_REDIS_BACKEND_NOT_AVAILABLE)
                    .param(MfaStoreErrors.ARG_STORE_TYPE, storeType)
                    .param(MfaStoreErrors.ARG_REASON,
                            "INosqlService is not configured; set nop.auth.mfa.store-type=local "
                                    + "or provide a nop-nosql backend");
        }
        return nosqlService;
    }
}
