/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.service.mfa.store.FakeNosqlService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SendCodeRateLimiterProvider} 选择逻辑（plan 2274 Phase 1，collect-beans 装配模型）——
 * 对照 {@code TestMfaStoreProvider} 形态：默认 local、显式选择、fail-closed（不静默回退）。
 */
public class TestSendCodeRateLimiterProvider {

    private SendCodeRateLimiterProvider providerWith(Map<String, ISendCodeRateLimiter> limiters) {
        SendCodeRateLimiterProvider p = new SendCodeRateLimiterProvider();
        p.setRateLimiters(limiters);
        return p;
    }

    /** 默认拓扑：仅 local 注册（无 nosql classpath 时 Redis bean 不注册）。 */
    @Test
    public void testDefaultIsLocal() {
        Map<String, ISendCodeRateLimiter> m = new HashMap<>();
        m.put("local", new LocalSendCodeRateLimiter());
        SendCodeRateLimiterProvider provider = providerWith(m);
        assertInstanceOf(LocalSendCodeRateLimiter.class, provider.getRateLimiter(),
                "default store-type must be local");
    }

    @Test
    public void testRedisWhenRegistered() {
        Map<String, ISendCodeRateLimiter> m = new HashMap<>();
        m.put("local", new LocalSendCodeRateLimiter());
        m.put("redis", new RedisSendCodeRateLimiter(new FakeNosqlService()));
        SendCodeRateLimiterProvider provider = providerWith(m);
        provider.setStoreType(SendCodeRateLimiterProvider.STORE_TYPE_REDIS);
        assertInstanceOf(RedisSendCodeRateLimiter.class, provider.getRateLimiter());
    }

    /** fail-closed：请求 redis 但未注册（classpath 无 nosql）必须显式抛异常，不静默回退 local。 */
    @Test
    public void testRedisWithoutRegistrationFailsClosed() {
        Map<String, ISendCodeRateLimiter> m = new HashMap<>();
        m.put("local", new LocalSendCodeRateLimiter());
        SendCodeRateLimiterProvider provider = providerWith(m);
        provider.setStoreType(SendCodeRateLimiterProvider.STORE_TYPE_REDIS);
        assertThrows(NopException.class, provider::getRateLimiter,
                "redis requested without registration must throw, not silently fall back");
    }

    @Test
    public void testUnknownStoreTypeFailsClosed() {
        Map<String, ISendCodeRateLimiter> m = new HashMap<>();
        m.put("local", new LocalSendCodeRateLimiter());
        SendCodeRateLimiterProvider provider = providerWith(m);
        provider.setStoreType("memcached");
        assertThrows(NopException.class, provider::getRateLimiter);
    }

    @Test
    public void testNoImplementationsRegisteredFailsClosed() {
        SendCodeRateLimiterProvider provider = new SendCodeRateLimiterProvider();
        assertThrows(NopException.class, provider::getRateLimiter);
    }
}
