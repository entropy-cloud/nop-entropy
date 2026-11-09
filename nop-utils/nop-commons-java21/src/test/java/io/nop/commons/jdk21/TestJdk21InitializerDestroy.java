package io.nop.commons.jdk21;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C4-012 a/c/d）虚拟线程池生命周期载体钉。
 */
public class TestJdk21InitializerDestroy {

    /** pin4012：initializer destroy 后注册表不再含 nop-virtual-thread；stats() 返回零值而非 null。 */
    @Test
    public void pin4012_destroyUnregistersVirtualThreadPool() {
        VirtualThreadTaskExecutor.registerGlobalWorker();
        IThreadPoolExecutor executor = GlobalExecutors.getExecutor(GlobalExecutors.NOP_VIRTUAL_THREAD);
        assertNotNull(executor, "注册后应可获取虚拟线程池");
        // (c)：stats() 零值（不返回 null）
        assertNotNull(executor.stats(), "缺陷态：stats() 返回 null → 调用方判空负担");
        assertTrue(executor.stats().getActiveCount() == 0);

        Jdk21CommonsCoreInitializer initializer = new Jdk21CommonsCoreInitializer();
        initializer.destroy();
        assertNull(GlobalExecutors.getExecutor(GlobalExecutors.NOP_VIRTUAL_THREAD),
                "destroy 后注册表不应再含 nop-virtual-thread");
    }
}
