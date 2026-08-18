/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.service.mfa.store.DbEmailCodeStore;
import io.nop.auth.service.mfa.store.DbMfaChallengeStore;
import io.nop.auth.service.mfa.store.DbSmsCodeStore;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W8 Phase 3 IoC-level wiring verification: the collect-beans (name-prefix) assembly works
 * end-to-end in the real container, and the conditional Redis beans are NOT registered/loaded
 * when {@code store-type != redis} (class-load safety regression gate, ai-dev/lessons/15).
 * <p>
 * The container starting successfully here is itself the class-load-safety evidence: nosql is on
 * the test classpath but no {@code INosqlService} bean is registered, and store-type defaults to db,
 * so the Redis store beans (which would otherwise fail to construct without INosqlService) are
 * conditionally excluded.
 * <p>
 * W15-impl：EmailCodeStore 三实现同装配验证（nopEmailCodeStore_ 前缀 + nopActiveEmailCodeStore
 * 工厂 bean——设计 §5.3.3 复制 W8 模式）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMfaStoreWiringDb extends JunitBaseTestCase {

    @Inject
    MfaStoreProvider mfaStoreProvider;

    @Inject
    MfaChallengeStore mfaChallengeStore;

    @Inject
    SmsCodeStore smsCodeStore;

    @Inject
    EmailCodeStore emailCodeStore;

    @Test
    public void testDefaultStoreTypeIsDb() {
        assertTrue("db".equalsIgnoreCase(mfaStoreProvider.getStoreType()),
                "default store-type must be db");
    }

    @Test
    public void testDbWiringReturnsDbStores() {
        assertInstanceOf(DbMfaChallengeStore.class, mfaStoreProvider.getMfaChallengeStore(),
                "store-type=db must wire DbMfaChallengeStore");
        assertInstanceOf(DbSmsCodeStore.class, mfaStoreProvider.getSmsCodeStore(),
                "store-type=db must wire DbSmsCodeStore");
        assertInstanceOf(DbEmailCodeStore.class, mfaStoreProvider.getEmailCodeStore(),
                "store-type=db must wire DbEmailCodeStore");
        assertInstanceOf(DbMfaChallengeStore.class, mfaChallengeStore);
        assertInstanceOf(DbSmsCodeStore.class, smsCodeStore);
        assertInstanceOf(DbEmailCodeStore.class, emailCodeStore,
                "factory bean nopActiveEmailCodeStore must resolve @Nullable EmailCodeStore injection");
    }

    @Test
    public void testRedisBeansNotCollectedWhenStoreTypeNotRedis() {
        // collect-beans key 为 bean id 去前缀后缀（如 nopMfaChallengeStore_db → _db），故同时断言两种形式。
        assertFalse(mfaStoreProvider.getChallengeStores().containsKey("redis")
                        || mfaStoreProvider.getChallengeStores().containsKey("_redis"),
                "redis store must NOT be collected when store-type != redis (conditional activation)");
        assertFalse(mfaStoreProvider.getSmsCodeStores().containsKey("redis")
                        || mfaStoreProvider.getSmsCodeStores().containsKey("_redis"),
                "redis sms store must NOT be collected when store-type != redis");
        assertFalse(mfaStoreProvider.getEmailCodeStores().containsKey("redis")
                        || mfaStoreProvider.getEmailCodeStores().containsKey("_redis"),
                "redis email store must NOT be collected when store-type != redis");

        assertTrue(mfaStoreProvider.getChallengeStores().containsKey("local")
                        || mfaStoreProvider.getChallengeStores().containsKey("_local"));
        assertTrue(mfaStoreProvider.getChallengeStores().containsKey("db")
                        || mfaStoreProvider.getChallengeStores().containsKey("_db"));
        assertTrue(mfaStoreProvider.getSmsCodeStores().containsKey("local")
                        || mfaStoreProvider.getSmsCodeStores().containsKey("_local"));
        assertTrue(mfaStoreProvider.getSmsCodeStores().containsKey("db")
                        || mfaStoreProvider.getSmsCodeStores().containsKey("_db"));
        assertTrue(mfaStoreProvider.getEmailCodeStores().containsKey("local")
                        || mfaStoreProvider.getEmailCodeStores().containsKey("_local"),
                "local email store must be collected");
        assertTrue(mfaStoreProvider.getEmailCodeStores().containsKey("db")
                        || mfaStoreProvider.getEmailCodeStores().containsKey("_db"),
                "db email store must be collected");
    }
}
