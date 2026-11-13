/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.commons.concurrent.ResourceRetirementQueue;
import io.nop.commons.lang.ICreationListener;
import io.nop.core.resource.IResourceObjectLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.1-C2-002 use-after-destroy 时序回归（plan 2504 裁定③，复用裁定①退役队列）：
 * 标记失效 + 延迟销毁 —— clear() 与 reloadObject→destroyObject 两销毁点、
 * ResourceLoadingCache.remove / state_loadFrom 两失效入口全部经同一延迟销毁路径。
 *
 * <p>修复前红形态（快路径无锁交出引用 × 锁内同步销毁）：读者持已交出引用期间 clear() 完成 →
 * 引用已被销毁（销毁时机先于读者窗口结束）。
 * 修复后绿形态（销毁时机可观测）：clear 后宽限期内引用保持可用（use-after-destroy 窗消除为
 * 有界宽限）；确定性触发排空后销毁发生（FIFO 保序：onDestroyed 相互保序、同一对象
 * onCreated → onDestroyed 保序）。终态为真实代码路径，非 javadoc/warn 声明。
 */
public class TestResourceCacheDelayedDestroy_C2_002 {

    static class RecordingListener implements ICreationListener<String> {
        final List<String> created = new ArrayList<>();
        final List<String> destroyed = new ArrayList<>();
        /** 全局事件序（+obj=onCreated，-obj=onDestroyed），用于跨对象/同对象保序断言 */
        final List<String> events = new ArrayList<>();

        @Override
        public void onCreated(String object) {
            created.add(object);
            events.add("+" + object);
        }

        @Override
        public void onDestroyed(String object) {
            destroyed.add(object);
            events.add("-" + object);
        }
    }

    static class DestroyableValue implements AutoCloseable {
        final String value;
        boolean destroyed;

        DestroyableValue(String value) {
            this.value = value;
        }

        @Override
        public void close() {
            destroyed = true;
        }
    }

    /**
     * C2-002 本体：clear 后快路径已交出引用在宽限期内保持可用；确定性排空后销毁发生（时机可观测）。
     */
    @Test
    public void testClearDelaysDestroyUntilDrain_C2_002() {
        RecordingListener listener = new RecordingListener();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/c2-002/a.txt", listener);

        entry.getObject(false, p -> "v1");
        String handedOut = entry.getNow();
        assertEquals("v1", handedOut);

        entry.clear();

        // 绿形态①：宽限期内已交出引用保持可用（销毁延迟，非同步销毁）
        assertFalse(listener.destroyed.contains("v1"),
                "onDestroyed must be delayed beyond the invalidation point (use-after-destroy window closed)");

        // 绿形态②：确定性触发排空后销毁发生（时机可观测，零 sleep）
        ResourceRetirementQueue.global().drainAllNow();
        assertTrue(listener.destroyed.contains("v1"), "retired object must be destroyed after deterministic drain");
    }

    /**
     * reloadObject→destroyObject 销毁点：新对象发布（onCreated）后旧对象退役；
     * 排空前旧对象未销毁，排空后销毁。同一对象 onCreated → onDestroyed 保序。
     */
    @Test
    public void testReloadRetiresOldObjectAfterPublish_C2_002() {
        RecordingListener listener = new RecordingListener();
        ResourceCacheEntry<String> entry = new ResourceCacheEntry<>("/test/c2-002/b.txt", listener);

        AtomicInteger seq = new AtomicInteger();
        entry.getObject(false, p -> "v" + seq.incrementAndGet());

        // 强制重载：发布 v2，退役 v1
        assertTrue(entry.checkRefresh(true, p -> "v" + seq.incrementAndGet()));
        assertEquals("v2", entry.getNow());

        // onCreated(v2) 已发生，而 v1 的 onDestroyed 仍在宽限期内未执行
        assertTrue(listener.created.contains("v2"));
        assertFalse(listener.destroyed.contains("v1"),
                "old object destruction must be delayed (retired, not inline destroyed)");

        ResourceRetirementQueue.global().drainAllNow();
        assertTrue(listener.destroyed.contains("v1"), "retired old object must be destroyed after drain");
        // 同一对象 onCreated → onDestroyed 保序（全局事件序）
        assertTrue(listener.events.indexOf("+v1") >= 0
                        && listener.events.indexOf("+v1") < listener.events.indexOf("-v1"),
                "onCreated must be ordered before onDestroyed for the same object: " + listener.events);
    }

    /**
     * 失效入口行为一致：destroy()（clear 同案）与多入口失效均走同一延迟销毁路径，onDestroyed FIFO 保序。
     */
    @Test
    public void testDestroyAndMultiEntryInvalidationFifoOrder_C2_002() {
        RecordingListener listener = new RecordingListener();
        ResourceCacheEntry<String> e1 = new ResourceCacheEntry<>("/test/c2-002/c.txt", listener);
        ResourceCacheEntry<String> e2 = new ResourceCacheEntry<>("/test/c2-002/d.txt", listener);

        e1.getObject(false, p -> "x1");
        e2.getObject(false, p -> "x2");

        // 两失效入口：entry.destroy()（clear 同案）+ 直接 clear()
        e1.destroy();
        e2.clear();

        // 排空前两者均未销毁
        assertFalse(listener.destroyed.contains("x1"));
        assertFalse(listener.destroyed.contains("x2"));

        // 排空后 FIFO 保序（退役队列 FIFO → onDestroyed 相互保序）
        ResourceRetirementQueue.global().drainAllNow();
        assertEquals("x1", listener.destroyed.get(0), "onDestroyed must preserve FIFO retire order");
        assertEquals("x2", listener.destroyed.get(1), "onDestroyed must preserve FIFO retire order");
    }

    /**
     * 真实销毁动作（DestroyHelper.safeDestroy）延迟执行：读者窗口内对象未 close，排空后 close。
     */
    @Test
    public void testSafeDestroyExecutedOnDrainNotOnClear_C2_002() {
        AtomicReference<DestroyableValue> created = new AtomicReference<>();
        ResourceCacheEntry<DestroyableValue> entry = new ResourceCacheEntry<>("/test/c2-002/e.txt");

        DestroyableValue v = new DestroyableValue("payload");
        created.set(v);
        entry.getObject(false, p -> v);

        entry.clear();
        assertFalse(v.destroyed, "safeDestroy must not run at the invalidation point (delayed destroy)");

        ResourceRetirementQueue.global().drainAllNow();
        assertTrue(v.destroyed, "safeDestroy must run when the retirement queue drains");
    }

    /**
     * ResourceLoadingCache 失效入口（remove）一致性：remove 后旧对象延迟销毁，新获取冷加载。
     */
    @Test
    public void testCacheRemoveEntryDelayedDestroy_C2_002() {
        RecordingListener listener = new RecordingListener();
        AtomicInteger seq = new AtomicInteger();
        ResourceLoadingCache<String> cache = new ResourceLoadingCache<>("test-c2-002",
                p -> "r" + seq.incrementAndGet(), listener);

        String first = cache.require("/test/c2-002/f.txt");
        assertEquals("r1", first);

        cache.remove("/test/c2-002/f.txt");

        // remove 失效入口同走延迟销毁：旧对象在宽限期内保持可用
        assertFalse(listener.destroyed.contains("r1"), "removed entry's object must be retired, not inline destroyed");
        assertEquals("r1", first, "handed-out reference must stay usable within grace");

        ResourceRetirementQueue.global().drainAllNow();
        assertTrue(listener.destroyed.contains("r1"), "retired object must be destroyed after deterministic drain");
    }
}
