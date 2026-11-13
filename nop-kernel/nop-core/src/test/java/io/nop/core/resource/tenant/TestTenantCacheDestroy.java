/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.tenant;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.lang.IDestroyable;
import io.nop.core.resource.IResourceObjectLoader;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C6-012）租户缓存容器 destroyOnRemove 载体钉。
 */
public class TestTenantCacheDestroy {

    static class DestroyableModel implements IDestroyable {
        final AtomicInteger destroyed = new AtomicInteger();

        @Override
        public void destroy() {
            destroyed.incrementAndGet();
        }
    }

    private static void awaitDestroyed(AtomicInteger counter) throws InterruptedException {
        // 销毁链三级异步：容器 removal 通知 → 租户缓存 destroy() → entry 退役队列排空
        long deadline = System.currentTimeMillis() + 5000;
        while (counter.get() == 0 && System.currentTimeMillis() < deadline) {
            io.nop.commons.concurrent.ResourceRetirementQueue.global().drainAllNow();
            Thread.sleep(10);
        }
        io.nop.commons.concurrent.ResourceRetirementQueue.global().drainAllNow();
    }

    /** pin6012：clearForTenant 驱逐租户缓存 → 容器 destroyOnRemove → 租户缓存内 entry 销毁回调可达。 */
    @Test
    public void pin6012_tenantEvictionTriggersDestroyCallback() throws Exception {
        DestroyableModel model = new DestroyableModel();
        IResourceObjectLoader<DestroyableModel> loader = path -> model;
        TenantAwareResourceLoadingCache<DestroyableModel> cache =
                new TenantAwareResourceLoadingCache<>("tenant-destroy-probe", loader, null);
        try {
            ContextProvider.runWithTenant("t-destroy-probe", () -> {
                cache.get("/probe/tenant/model", loader);
                return null;
            });

            assertEquals(0, model.destroyed.get());
            // 驱逐租户缓存：容器 destroyOnRemove → 租户缓存 destroy() → entry 销毁回调
            cache.clearForTenant("t-destroy-probe");

            awaitDestroyed(model.destroyed);
            assertTrue(model.destroyed.get() >= 1,
                    "缺陷态：租户容器驱逐路径销毁回调不可达（假阳性：只开容器 destroyOnRemove 而值类型无销毁语义）");
        } finally {
            cache.clear();
        }
    }
}
