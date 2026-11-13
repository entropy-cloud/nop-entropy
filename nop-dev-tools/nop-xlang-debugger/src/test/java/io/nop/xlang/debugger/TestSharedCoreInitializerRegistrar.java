/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.ICancellable;
import io.nop.core.initialize.ICoreInitializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.40（6.5 单元）W-05 验收载体红⑤（C10-005 缺保护面）：SharedCoreInitializerRegistrar
 * 引用计数——acquire×2 → release×1 全局设施存活；release×2 → 保留集按获取逆序全部 destroy[m4]；
 * 同一凭据重复释放无效；失败替身两态（有其他持有者→计数不变销毁 0 / 唯一持有者→销毁本次 1 份
 * 防孤儿注册）。真实 XLangCoreInitializer 不入 Maven 测试：plain JVM 下全局
 * ResourceComponentManager 非 per-project，二次 registerXDef 必触 :226 Guard——真实接线验证属
 * Gradle 面（通道③ 登记，红⑥ harness）。每用例 new 独立 registrar 实例（包内构造器，M3——
 * 静态单例共享计数会使用例间串扰）。
 */
public class TestSharedCoreInitializerRegistrar {

    /** 计数替身：实现与 XLangCoreInitializer 相同的 ICoreInitializer 使用面 */
    static class CountingInit implements ICoreInitializer {
        final String name;
        int initialized;
        int destroyed;
        boolean failInitialize;

        CountingInit(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            if (failInitialize) {
                throw new IllegalStateException("stub initialize failure: " + name);
            }
            initialized++;
        }

        @Override
        public void destroy() {
            destroyed++;
        }
    }

    @Test
    public void testReferenceCountingGatesDestroyOnly() {
        SharedCoreInitializerRegistrar registrar = new SharedCoreInitializerRegistrar();
        CountingInit first = new CountingInit("first");
        CountingInit second = new CountingInit("second");

        // acquire×2：初始化各自执行（只门控销毁不门控初始化）
        ICancellable hold1 = registrar.acquire(first);
        ICancellable hold2 = registrar.acquire(second);
        assertEquals(1, first.initialized);
        assertEquals(1, second.initialized);

        // release×1：计数未归零，全局设施存活（销毁计数 0）
        hold1.cancel("test");
        assertEquals(0, first.destroyed);
        assertEquals(0, second.destroyed);

        // release×2：计数归零 → 保留集按获取逆序全部 destroy（后 acquire 的 second 先销毁）
        hold2.cancel("test");
        assertEquals(1, second.destroyed);
        assertEquals(1, first.destroyed);
    }

    @Test
    public void testDuplicateReleaseIsNoOp() {
        SharedCoreInitializerRegistrar registrar = new SharedCoreInitializerRegistrar();
        CountingInit init = new CountingInit("dup");
        ICancellable hold = registrar.acquire(init);

        hold.cancel("first");
        assertEquals(1, init.destroyed);

        // 同一凭据重复释放无效（防 Disposer 重入导致二次扣减）
        hold.cancel("second");
        assertEquals(1, init.destroyed);
        assertTrue(hold.isCancelled());
        assertEquals("first", hold.getCancelReason());
    }

    @Test
    public void testAcquireFailureWithOtherHoldersKeepsSharedState() {
        SharedCoreInitializerRegistrar registrar = new SharedCoreInitializerRegistrar();
        CountingInit holder = new CountingInit("holder");
        registrar.acquire(holder);

        CountingInit failing = new CountingInit("failing");
        failing.failInitialize = true;

        // 失败不计数、原异常上抛
        assertThrows(IllegalStateException.class, () -> registrar.acquire(failing));
        assertEquals(0, failing.initialized);

        // 存在其他持有者 → 全局共享实例不得销毁（跨项目销毁即缺陷复现）
        registrar.destroyIfOrphaned(failing);
        assertEquals(0, failing.destroyed);
        assertEquals(0, holder.destroyed);
    }

    @Test
    public void testAcquireFailureWithoutHoldersCleansOrphanRegistration() {
        SharedCoreInitializerRegistrar registrar = new SharedCoreInitializerRegistrar();
        CountingInit failing = new CountingInit("orphan");
        failing.failInitialize = true;

        assertThrows(IllegalStateException.class, () -> registrar.acquire(failing));

        // 无其他持有者 → destroy 本次初始化器，防孤儿全局注册残留
        registrar.destroyIfOrphaned(failing);
        assertEquals(1, failing.destroyed);
    }

    @Test
    public void testDestroyOrderIsReverseAcquisition() {
        SharedCoreInitializerRegistrar registrar = new SharedCoreInitializerRegistrar();
        List<String> order = new ArrayList<>();
        CountingInit a = new CountingInit("a") {
            @Override
            public void destroy() {
                order.add("a");
            }
        };
        CountingInit b = new CountingInit("b") {
            @Override
            public void destroy() {
                order.add("b");
            }
        };
        CountingInit c = new CountingInit("c") {
            @Override
            public void destroy() {
                order.add("c");
            }
        };

        ICancellable holdA = registrar.acquire(a);
        ICancellable holdB = registrar.acquire(b);
        ICancellable holdC = registrar.acquire(c);

        holdA.cancel("x");
        holdC.cancel("x");
        holdB.cancel("x");

        // 计数归零：全部保留初始化器按获取逆序 destroy（c → b → a）
        assertEquals(List.of("c", "b", "a"), order, "retained initializers destroy in reverse acquisition order");
    }
}
