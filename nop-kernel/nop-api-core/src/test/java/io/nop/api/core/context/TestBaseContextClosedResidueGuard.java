/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * context 平台根因半边回归（CRA R1-04，plan 2499 Phase 2，裁定③平台根因统一裁定。
 * 设计权威来源：{@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.3）。
 *
 * <p>契约：BaseContextProvider.currentContext 读到 closed context 时惰性清除 ThreadLocal
 * 残留并返回 null——与 getOrCreateContext 既有 isClosed 检查构成对称双读点守卫，残留
 * context 从「可被读到」变为「不可观测」；BaseContext.close 维持条件 detach
 * （holder==this 才同步清除），跨线程关闭的残留由读点守卫承接。
 */
public class TestBaseContextClosedResidueGuard {

    /**
     * isClosed 残留不可观测：远端线程关闭 context 后，本线程 holder 仍残留该 context，
     * currentContext 读点守卫返回 null 并惰性清除残留。
     */
    @Test
    public void testCurrentContextDoesNotReturnClosedResidue() throws Exception {
        BaseContextProvider provider = new BaseContextProvider();
        try {
            IContext context = new BaseContext();
            provider.attachContext(context);

            // 模拟跨线程关闭：close 发生在另一线程，本线程 holder 不会被 close 同步清除
            Thread closer = new Thread(context::close);
            closer.start();
            closer.join(5000);

            assertTrue(context.isClosed());
            assertSame(context, provider.contextHolder().get(),
                    "remote close must not synchronously clear this thread's holder (conditional detach)");

            assertNull(provider.currentContext(),
                    "closed residue must not be observable via currentContext");
            assertNull(provider.contextHolder().get(),
                    "the guard must lazily clear the closed residue from the holder");
        } finally {
            BaseContextProvider.clear();
        }
    }

    /**
     * close 对称 detach 半边钉：在本线程 close 恰好附着的 context 时 holder 被同步清除
     * （条件 detach holder==this 成立路径）。
     */
    @Test
    public void testCloseOnAttachedThreadClearsHolderSymmetrically() {
        BaseContextProvider provider = new BaseContextProvider();
        try {
            BaseContext context = new BaseContext();
            provider.attachContext(context);
            assertSame(context, provider.currentContext());

            context.close();

            assertTrue(context.isClosed());
            assertNull(provider.contextHolder().get(),
                    "close on the attaching thread must clear the holder (conditional detach)");
            assertNull(provider.currentContext());
        } finally {
            BaseContextProvider.clear();
        }
    }

    /**
     * 对称双读点守卫钉：getOrCreateContext 对 closed 残留的既有检查保持——不再复用，
     * 新建并附着 open context。
     */
    @Test
    public void testGetOrCreateContextReplacesClosedResidue() throws Exception {
        BaseContextProvider provider = new BaseContextProvider();
        try {
            IContext context = new BaseContext();
            provider.attachContext(context);
            Thread closer = new Thread(context::close);
            closer.start();
            closer.join(5000);

            IContext created = provider.getOrCreateContext();

            assertNotEquals(context, created, "closed residue must not be reused");
            assertFalse(created.isClosed());
            assertSame(created, provider.contextHolder().get(),
                    "the replacement context must be attached");
        } finally {
            BaseContextProvider.clear();
        }
    }

    /**
     * close 幂等钉：重复 close 不重复清理、无副作用。
     */
    @Test
    public void testCloseIsIdempotent() {
        BaseContextProvider provider = new BaseContextProvider();
        try {
            BaseContext context = new BaseContext();
            provider.attachContext(context);
            context.close();
            context.close();
            assertNull(provider.contextHolder().get());
        } finally {
            BaseContextProvider.clear();
        }
    }
}
