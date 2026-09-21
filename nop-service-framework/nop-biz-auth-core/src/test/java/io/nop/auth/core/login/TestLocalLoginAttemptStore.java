/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalLoginAttemptStore} 原子递增回归（plan 2274 Phase 2）：
 * {@code TestLoginFailCountAtomicity}（nop-auth-service）的组件级对应——
 * 32 线程并发递增无丢失更新（原 {@code loginFailCountLock} 职责内聚到实现），
 * 以及 user/ip 键维度读写一致性（ipKey 前缀缺陷修正的组件侧钉定）。
 */
public class TestLocalLoginAttemptStore {

    @Test
    public void testConcurrentIncrementNoLostUpdates() throws Exception {
        LocalLoginAttemptStore store = new LocalLoginAttemptStore();
        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger maxReturned = new AtomicInteger();
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    int v = store.incrementLoginFailCount(ILoginAttemptStore.userKey("atomic-user"));
                    maxReturned.accumulateAndGet(v, Math::max);
                } catch (Exception e) {
                    // 断言阶段暴露
                }
            });
        }
        for (Thread t : ts)
            t.start();
        for (Thread t : ts)
            t.join(30000);

        assertEquals(threads, store.getLoginFailCount(ILoginAttemptStore.userKey("atomic-user")),
                "no lost updates: N concurrent increments must yield count N");
        assertEquals(threads, maxReturned.get(), "increment must return distinct sequential values");
    }

    @Test
    public void testUserAndIpKeysAreDistinctDimensions() {
        LocalLoginAttemptStore store = new LocalLoginAttemptStore();
        store.setLoginFailCount(ILoginAttemptStore.userKey("alice"), 3);
        assertEquals(0, store.getLoginFailCount(ILoginAttemptStore.ipKey("alice")),
                "user and ip dimensions must be isolated (ip: prefix bug fix)");
        store.setLoginFailCount(ILoginAttemptStore.ipKey("10.0.0.1"), 5);
        assertEquals(5, store.getLoginFailCount(ILoginAttemptStore.ipKey("10.0.0.1")));
        assertEquals(0, store.getLoginFailCount("10.0.0.1"), "bare key must not collide with prefixed key");
    }

    @Test
    public void testResetRemovesEntry() {
        LocalLoginAttemptStore store = new LocalLoginAttemptStore();
        store.incrementLoginFailCount(ILoginAttemptStore.userKey("bob"));
        assertTrue(store.getLoginFailCount(ILoginAttemptStore.userKey("bob")) > 0);
        store.resetLoginFailCount(ILoginAttemptStore.userKey("bob"));
        assertEquals(0, store.getLoginFailCount(ILoginAttemptStore.userKey("bob")));
    }

    @Test
    public void testSharedDefaultIsSameInstance() {
        assertTrue(LoginAttemptStores.sharedLocalDefault() == LoginAttemptStores.sharedLocalDefault(),
                "shared default must resolve to the same instance (design §3.2 default-path identity)");
    }
}
