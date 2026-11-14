package io.nop.commons.jdk21;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2573 补充复查钉（ThreadPoolStats 共享可变常量修复）：
 * {@code VirtualThreadTaskExecutor.stats()} 不得返回共享可变单例——原形态
 * {@code ThreadPoolStats.ZERO} 是带 public setter 的 {@code @DataBean} 静态常量，
 * 任何调用方对返回实例调 setter 即污染全局、波及后续所有 zero-stats 消费方。
 * 修复后 {@code ThreadPoolStats.zero()} 工厂每次返回新实例。
 *
 * <p>变异探针：把 {@code zero()} 改回共享常量（或 stats() 返回同一实例）时，两次 stats()
 * 为 same 实例 → 断言红。
 */
public class TestVirtualThreadStatsFreshInstance {

    @Test
    void statsReturnsFreshInstancePerCall_notSharedMutableSingleton() {
        // 经 registerGlobalWorker 注册到 GlobalExecutors 的虚拟线程池（键 = NOP_VIRTUAL_THREAD）；
        // 单测环境 CoreInitializer 可能未跑，缺注册时补注册
        if (GlobalExecutors.getExecutor(GlobalExecutors.NOP_VIRTUAL_THREAD) == null)
            VirtualThreadTaskExecutor.registerGlobalWorker();
        Object exec = GlobalExecutors.getExecutor(GlobalExecutors.NOP_VIRTUAL_THREAD);
        assertTrue(exec instanceof VirtualThreadTaskExecutor,
                "precondition: virtual-thread executor must be registered under "
                        + GlobalExecutors.NOP_VIRTUAL_THREAD + " (registerGlobalWorker)");

        VirtualThreadTaskExecutor executor = (VirtualThreadTaskExecutor) exec;
        ThreadPoolStats first = executor.stats();
        ThreadPoolStats second = executor.stats();
        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second,
                "stats() must return a fresh instance per call — a shared mutable ZERO singleton "
                        + "lets any caller's setter pollute the global constant");
        assertEquals(0, first.getActiveCount());
        // 对返回实例的修改不得影响下一次返回（共享常量形态下此断言必红）
        first.setActiveCount(42);
        assertEquals(0, executor.stats().getActiveCount(),
                "mutating a returned stats instance must not leak into subsequent stats()");
    }
}
