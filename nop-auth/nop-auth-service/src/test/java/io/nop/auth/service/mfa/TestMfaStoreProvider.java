/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.service.mfa.store.DbMfaChallengeStore;
import io.nop.auth.service.mfa.store.DbSmsCodeStore;
import io.nop.auth.service.mfa.store.RedisMfaChallengeStore;
import io.nop.auth.service.mfa.store.RedisSmsCodeStore;
import io.nop.auth.service.mfa.store.FakeNosqlService;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W4/W8 Phase 3: verifies the store assembly point ({@link MfaStoreProvider}) selection logic
 * under the collect-beans (name-prefix) wiring model.
 */
public class TestMfaStoreProvider {

    private static final FakeNosqlService NOSQL = new FakeNosqlService();

    private MfaStoreProvider providerWith(Map<String, MfaChallengeStore> challenge, Map<String, SmsCodeStore> sms) {
        MfaStoreProvider p = new MfaStoreProvider();
        p.setChallengeStores(challenge);
        p.setSmsCodeStores(sms);
        return p;
    }

    /** Default topology: local + db registered (no redis — simulates no nosql on classpath). */
    private MfaStoreProvider defaultTopology() {
        Map<String, MfaChallengeStore> c = new HashMap<>();
        c.put("local", new LocalMfaChallengeStore());
        c.put("db", new DbMfaChallengeStore());
        Map<String, SmsCodeStore> s = new HashMap<>();
        s.put("local", new LocalSmsCodeStore());
        s.put("db", new DbSmsCodeStore());
        return providerWith(c, s);
    }

    @Test
    public void testDefaultIsDb() {
        MfaStoreProvider provider = defaultTopology();
        assertInstanceOf(DbMfaChallengeStore.class, provider.getMfaChallengeStore(), "default store-type must be db");
        assertInstanceOf(DbSmsCodeStore.class, provider.getSmsCodeStore());
    }

    @Test
    public void testExplicitLocal() {
        MfaStoreProvider provider = defaultTopology();
        provider.setStoreType(MfaStoreProvider.STORE_TYPE_LOCAL);
        assertInstanceOf(LocalMfaChallengeStore.class, provider.getMfaChallengeStore());
        assertInstanceOf(LocalSmsCodeStore.class, provider.getSmsCodeStore());
    }

    @Test
    public void testExplicitDb() {
        MfaStoreProvider provider = defaultTopology();
        provider.setStoreType(MfaStoreProvider.STORE_TYPE_DB);
        assertInstanceOf(DbMfaChallengeStore.class, provider.getMfaChallengeStore());
        assertInstanceOf(DbSmsCodeStore.class, provider.getSmsCodeStore());
    }

    @Test
    public void testRedisWhenRegistered() {
        Map<String, MfaChallengeStore> c = new HashMap<>();
        c.put("local", new LocalMfaChallengeStore());
        c.put("db", new DbMfaChallengeStore());
        c.put("redis", new RedisMfaChallengeStore(NOSQL));
        Map<String, SmsCodeStore> s = new HashMap<>();
        s.put("local", new LocalSmsCodeStore());
        s.put("db", new DbSmsCodeStore());
        s.put("redis", new RedisSmsCodeStore(NOSQL));
        MfaStoreProvider provider = providerWith(c, s);
        provider.setStoreType(MfaStoreProvider.STORE_TYPE_REDIS);
        assertInstanceOf(RedisMfaChallengeStore.class, provider.getMfaChallengeStore());
        assertInstanceOf(RedisSmsCodeStore.class, provider.getSmsCodeStore());
    }

    @Test
    public void testRedisWithoutRedisStoreFailsClosed() {
        MfaStoreProvider provider = defaultTopology();
        provider.setStoreType(MfaStoreProvider.STORE_TYPE_REDIS);
        assertThrows(NopException.class, provider::getMfaChallengeStore,
                "redis requested without redis store registered must throw, not silently fall back");
        assertThrows(NopException.class, provider::getSmsCodeStore);
    }

    @Test
    public void testUnknownStoreTypeFailsClosed() {
        MfaStoreProvider provider = defaultTopology();
        provider.setStoreType("memcached");
        assertThrows(NopException.class, provider::getMfaChallengeStore);
    }

    @Test
    public void testNoStoresRegisteredFailsClosed() {
        MfaStoreProvider provider = new MfaStoreProvider();
        assertThrows(NopException.class, provider::getMfaChallengeStore);
    }
}
