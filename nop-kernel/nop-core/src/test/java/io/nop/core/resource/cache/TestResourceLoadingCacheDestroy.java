/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.core.resource.IResourceObjectLoader;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C6-005）ResourceLoadingCache 销毁链载体钉。
 */
public class TestResourceLoadingCacheDestroy {


    static class DestroyableModel implements io.nop.commons.lang.IDestroyable {
        final AtomicInteger destroyed = new AtomicInteger();

        @Override
        public void destroy() {
            destroyed.incrementAndGet();
        }
    }

    private static void awaitDestroyed(java.util.concurrent.atomic.AtomicInteger counter) throws InterruptedException {
        // 销毁链三级异步：Caffeine removal 通知（executor）→ entry.destroy() → 退役队列（grace 排空）。
        // 轮询期间反复手动排空退役队列，保证不依赖全局调度器（确定性）
        long deadline = System.currentTimeMillis() + 5000;
        while (counter.get() == 0 && System.currentTimeMillis() < deadline) {
            io.nop.commons.concurrent.ResourceRetirementQueue.global().drainAllNow();
            Thread.sleep(10);
        }
        io.nop.commons.concurrent.ResourceRetirementQueue.global().drainAllNow();
    }

    /** pin6005：驱逐（remove）与关闭（destroy）路径都必须执行 entry 的 destroy 回调。 */
    @Test
    public void pin6005_evictionAndCloseTriggerDestroyCallback() throws Exception {
        DestroyableModel evicted = new DestroyableModel();
        DestroyableModel remaining = new DestroyableModel();
        IResourceObjectLoader<DestroyableModel> loader =
                path -> path.endsWith("a") ? evicted : remaining;
        ResourceLoadingCache<DestroyableModel> cache =
                new ResourceLoadingCache<>("rlc-destroy-probe", loader, null);
        try {
            cache.get("/probe/model-a");
            cache.get("/probe/model-b");
            assertEquals(0, evicted.destroyed.get());
            assertEquals(0, remaining.destroyed.get());

            // 驱逐路径：remove → removalListener → destroyOnRemove → entry.destroy() → 模型销毁
            cache.remove("/probe/model-a");
            // 销毁唯一执行位 = 退役队列排空（plan 2504 宽限语义）；测试手动排空
            awaitDestroyed(evicted.destroyed);
            assertTrue(evicted.destroyed.get() >= 1,
                    "缺陷态：驱逐路径 entry.destroy 结构性不可达");
        } finally {
            // 关闭路径：destroy → invalidateAll → 剩余条目的销毁回调（经退役队列）
            cache.destroy();
            awaitDestroyed(remaining.destroyed);
            assertTrue(remaining.destroyed.get() >= 1,
                    "关闭路径也应触发销毁回调");
            assertEquals(1, evicted.destroyed.get(), "已驱逐条目不应重复销毁");
        }
    }
}
