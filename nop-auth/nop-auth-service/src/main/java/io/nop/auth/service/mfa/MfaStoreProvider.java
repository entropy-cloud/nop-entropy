/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
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

/**
 * MFA 存储装配点（设计 §3.3 / §3.7 {@code nop.auth.mfa.store-type}）。
 * <p>
 * 选择规则：
 * <ul>
 *   <li>{@code local}（默认）→ Local 实现（JVM 内，零外部依赖）。</li>
 *   <li>{@code redis} + Redis store 已注入 → 返回注入的 Redis 实现。</li>
 *   <li>{@code redis} 但 Redis store 未注入 → <b>显式抛异常</b>（fail-closed，
 *       不静默回退到 Local；见 No-Silent-No-Op 规则）。</li>
 * </ul>
 * <p>
 * <b>类加载安全</b>：本类不得在类签名（字段/方法参数/返回类型）中引用
 * {@code io.nop.nosql.core.INosqlService}——该依赖在 {@code nop-auth-service/pom.xml}
 * 中声明为 {@code <optional>}，未引入 nosql 的应用在 IoC 反射本类方法签名时会抛
 * {@code NoClassDefFoundError}（教训 ai-dev/lessons/15）。Redis store 实现通过接口类型
 * ({@link MfaChallengeStore}/{@link SmsCodeStore}) 注入，本类零 nosql 类型引用。
 */
public class MfaStoreProvider {

    public static final String STORE_TYPE_LOCAL = "local";
    public static final String STORE_TYPE_REDIS = "redis";

    private String storeType = STORE_TYPE_LOCAL;

    private MfaChallengeStore redisChallengeStore;
    private SmsCodeStore redisSmsCodeStore;

    private MfaChallengeStoreConfig challengeConfig = new MfaChallengeStoreConfig();
    private SmsCodeStoreConfig smsConfig = new SmsCodeStoreConfig();

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    public String getStoreType() {
        return storeType;
    }

    public void setRedisChallengeStore(MfaChallengeStore redisChallengeStore) {
        this.redisChallengeStore = redisChallengeStore;
    }

    public void setRedisSmsCodeStore(SmsCodeStore redisSmsCodeStore) {
        this.redisSmsCodeStore = redisSmsCodeStore;
    }

    public void setChallengeConfig(MfaChallengeStoreConfig challengeConfig) {
        this.challengeConfig = challengeConfig;
    }

    public void setSmsConfig(SmsCodeStoreConfig smsConfig) {
        this.smsConfig = smsConfig;
    }

    public MfaChallengeStore getMfaChallengeStore() {
        if (isRedis()) {
            if (redisChallengeStore == null) {
                throw failClosed("MfaChallengeStore");
            }
            return redisChallengeStore;
        }
        return new LocalMfaChallengeStore(challengeConfig);
    }

    public SmsCodeStore getSmsCodeStore() {
        if (isRedis()) {
            if (redisSmsCodeStore == null) {
                throw failClosed("SmsCodeStore");
            }
            return redisSmsCodeStore;
        }
        return new LocalSmsCodeStore(smsConfig);
    }

    private boolean isRedis() {
        return STORE_TYPE_REDIS.equalsIgnoreCase(storeType);
    }

    private NopException failClosed(String storeName) {
        return new NopException(MfaStoreErrors.ERR_MFA_STORE_REDIS_BACKEND_NOT_AVAILABLE)
                .param(MfaStoreErrors.ARG_STORE_TYPE, storeType)
                .param(MfaStoreErrors.ARG_REASON,
                        storeName + " is not configured; set nop.auth.mfa.store-type=local "
                                + "or provide a nop-nosql backend");
    }
}
