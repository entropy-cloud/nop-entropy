/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.ILoginAttemptStore;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.service.mfa.store.FakeNosqlService;
import io.nop.commons.cache.ICache;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2274 Phase 2 接线验证：
 * <ul>
 *   <li>{@code AbstractUserContextCache} 失败计数方法委托注入的 store（探针断言调用链连通，
 *       非仅类型存在）。</li>
 *   <li>手工 wiring 下 {@code LoginServiceImpl} 的原子递增与 wired cache 的读取命中同一 store
 *       （缺省路径写读同一性——design §3.2 NEW-1 修复的行为钉定）。</li>
 *   <li>{@code getLoginFailCountForIp} 读写同维（ipKey 前缀缺陷修正）。</li>
 *   <li>verifyCodeCache 注入点：自定义 ICache 被实际使用。</li>
 *   <li>{@code LoginAttemptStoreProvider} fail-closed。</li>
 *   <li>{@code RedisLoginAttemptStore} 委托契约（FakeNosqlService）。</li>
 * </ul>
 */
public class TestLoginAttemptWiring {

    /** 记录调用的探针 store（接线验证：委托确实发生，而非仅类型存在）。 */
    static class ProbeStore implements ILoginAttemptStore {
        final Map<String, Integer> counts = new ConcurrentHashMap<>();
        int increments;

        @Override
        public int getLoginFailCount(String key) {
            return counts.getOrDefault(key, 0);
        }

        @Override
        public void setLoginFailCount(String key, int count) {
            counts.put(key, count);
        }

        @Override
        public void resetLoginFailCount(String key) {
            counts.remove(key);
        }

        @Override
        public int incrementLoginFailCount(String key) {
            increments++;
            return counts.merge(key, 1, Integer::sum);
        }
    }

    private static LocalUserContextCache newCacheWithStore(ILoginAttemptStore store) {
        LocalUserContextCache cache = new LocalUserContextCache();
        cache.setUserContextConfig(new UserContextConfig());
        cache.setLoginAttemptStore(store);
        cache.init();
        return cache;
    }

    @Test
    public void testCacheDelegatesToInjectedStore() {
        ProbeStore probe = new ProbeStore();
        LocalUserContextCache cache = newCacheWithStore(probe);

        cache.setLoginFailCountForUser("u1", 2);
        assertTrue(probe.counts.containsKey(ILoginAttemptStore.userKey("u1")),
                "cache writes must delegate to the injected store under the un: prefixed key (wiring evidence)");
        assertEquals(2, probe.getLoginFailCount(ILoginAttemptStore.userKey("u1")));

        cache.resetLoginFailCountForUser("u1");
        assertTrue(!probe.counts.containsKey(ILoginAttemptStore.userKey("u1")),
                "cache resets must delegate to the injected store");
        assertEquals(0, cache.getLoginFailCountForUser("u1"));
    }

    @Test
    public void testServiceIncrementVisibleThroughWiredCache() {
        LocalUserContextCache cache = new LocalUserContextCache();
        cache.setUserContextConfig(new UserContextConfig());
        cache.init();

        LoginServiceImpl service = new LoginServiceImpl();
        // 反射注入 userContextCache（对齐 TestLoginFailCountAtomicity 的手工 wiring 形态）
        TestReflections.setField(service, "userContextCache", cache);

        // 经 service 的原子递增与经 cache 的读取必须命中同一 store（缺省路径写读一致性）
        assertEquals(1, service.incrementLoginFailCount("wired-user"));
        assertEquals(2, service.incrementLoginFailCount("wired-user"));
        assertEquals(2, cache.getLoginFailCountForUser("wired-user"),
                "service increments must be visible through the wired cache (same store instance)");
    }

    @Test
    public void testIpDimensionReadWriteConsistent() {
        ProbeStore probe = new ProbeStore();
        LocalUserContextCache cache = newCacheWithStore(probe);

        cache.setLoginFailCountForIp("10.0.0.9", 7);
        assertEquals(7, cache.getLoginFailCountForIp("10.0.0.9"),
                "ip read and write must use the same ip: key dimension (prefix bug fix)");
        assertTrue(probe.counts.containsKey(ILoginAttemptStore.ipKey("10.0.0.9")),
                "ip writes must land under the ip: prefixed key");
        cache.resetLoginFailCountForIp("10.0.0.9");
        assertEquals(0, cache.getLoginFailCountForIp("10.0.0.9"));
    }

    @Test
    public void testVerifyCodeCacheInjectionPointUsed() {
        LocalUserContextCache cache = new LocalUserContextCache();
        cache.setUserContextConfig(new UserContextConfig());
        // 注入独立 LocalCache 实例（TTL 保持契约的缺省满足者）：断言写入落在本实例
        ICache<String, String> injected = io.nop.commons.cache.LocalCache.newCache("test-verify-code-cache",
                io.nop.commons.cache.CacheConfig.newConfig(10), null);
        cache.setVerifyCodeCache(injected);
        cache.init();

        cache.setVerifyCode("secret-1", "1234");
        assertEquals("1234", cache.getVerifyCode("secret-1"),
                "injected verify code cache must be used");
        assertEquals("1234", injected.get("vc:" + io.nop.commons.util.StringHelper.md5Hash("secret-1"
                + new UserContextConfig().getVerifyKey())),
                "writes must land in the injected cache instance (key derivation unchanged)");

        // TTL 保持契约（design §3.3）：注入实现必须带写入过期——短 TTL 注入实例到期后不可读
        io.nop.commons.cache.ICache<String, String> shortTtl = io.nop.commons.cache.LocalCache.newCache(
                "test-verify-code-ttl",
                io.nop.commons.cache.CacheConfig.newConfig(10)
                        .expireAfterWrite(java.time.Duration.ofMillis(50)), null);
        LocalUserContextCache cache2 = new LocalUserContextCache();
        cache2.setUserContextConfig(new UserContextConfig());
        cache2.setVerifyCodeCache(shortTtl);
        cache2.init();
        cache2.setVerifyCode("secret-2", "5678");
        org.junit.jupiter.api.Assertions.assertNotNull(cache2.getVerifyCode("secret-2"),
                "fresh write must be readable");
        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        org.junit.jupiter.api.Assertions.assertNull(cache2.getVerifyCode("secret-2"),
                "injected cache must expire writes (TTL-preserving contract, no permanent codes)");
    }

    @Test
    public void testProviderFailsClosed() {
        LoginAttemptStoreProvider provider = new LoginAttemptStoreProvider();
        assertThrows(NopException.class, provider::getLoginAttemptStore, "no stores registered must fail closed");

        provider.setStoreType("redis");
        assertThrows(NopException.class, provider::getLoginAttemptStore,
                "redis requested without registration must fail closed (no silent local fallback)");
    }

    @Test
    public void testRedisLoginAttemptStoreDelegatesToNosqlPrimitives() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(nosql);

        assertEquals(0, store.getLoginFailCount(ILoginAttemptStore.userKey("r1")));
        assertEquals(1, store.incrementLoginFailCount(ILoginAttemptStore.userKey("r1")));
        assertEquals(2, store.incrementLoginFailCount(ILoginAttemptStore.userKey("r1")));
        assertEquals(2, store.getLoginFailCount(ILoginAttemptStore.userKey("r1")));
        assertTrue(nosql.callCount("counter.increment") >= 2, "increment must use INosqlCounter");
        assertTrue(nosql.callCount("setTimeoutAsync") >= 1, "first increment must set TTL");

        store.setLoginFailCount(ILoginAttemptStore.userKey("r1"), 9);
        assertEquals(9, store.getLoginFailCount(ILoginAttemptStore.userKey("r1")));

        store.resetLoginFailCount(ILoginAttemptStore.userKey("r1"));
        assertEquals(0, store.getLoginFailCount(ILoginAttemptStore.userKey("r1")));
        assertTrue(nosql.callCount("remove") >= 1, "reset must remove the key");
    }

    /** 反射工具（对齐 TestLoginFailCountAtomicity.setField）。 */
    static final class TestReflections {
        static void setField(Object target, String name, Object value) {
            Class<?> cls = target.getClass();
            while (cls != null) {
                try {
                    java.lang.reflect.Field f = cls.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                } catch (IllegalAccessException e) {
                    throw NopException.adapt(e);
                }
            }
            throw new IllegalArgumentException("no field " + name + " on " + target.getClass());
        }
    }
}
