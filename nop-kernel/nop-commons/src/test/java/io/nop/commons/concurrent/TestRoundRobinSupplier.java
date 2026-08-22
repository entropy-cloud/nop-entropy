/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRoundRobinSupplier {

    static class TestResource implements AutoCloseable {
        final int id;
        boolean closed;

        TestResource(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void testResizeCloseNewResourcesOnFailure() {
        AtomicInteger created = new AtomicInteger();
        List<TestResource> all = new ArrayList<>();

        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> {
            int id = created.incrementAndGet();
            TestResource res = new TestResource(id);
            all.add(res);
            if (id > 2)
                throw new IllegalStateException("mock factory failure");
            return res;
        }, 1);

        assertEquals(1, supplier.get().id);

        // 扩容到3: index1创建成功(id=2)，index2创建时抛异常
        assertThrows(Exception.class, () -> supplier.resize(3));

        // 扩容失败时，本次新建的资源(id=2)必须被关闭，从旧数组复制来的资源(id=1)不受影响
        assertTrue(all.get(0).id == 1 && !all.get(0).closed, "old resource must not be closed");
        assertTrue(all.get(1).id == 2 && all.get(1).closed, "newly created resource must be closed");

        // 扩容失败后旧数组仍然可用
        assertEquals(1, supplier.get().id);
    }

    @Test
    public void testResizeShrinkAndExpand() {
        AtomicInteger created = new AtomicInteger();
        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> new TestResource(created.incrementAndGet()), 2);
        supplier.resize(1);
        assertEquals(1, supplier.get().id);
        supplier.resize(2);
        int a = supplier.get().id;
        int b = supplier.get().id;
        // 扩容后旧资源(id=1)保留，新增槽位创建新资源(id=3)
        assertTrue((a == 1 && b == 3) || (a == 3 && b == 1), "a=" + a + ",b=" + b);
    }
}
