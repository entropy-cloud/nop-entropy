package io.nop.commons.jdk21;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.core.initialize.ICoreInitializer;

import static io.nop.commons.CommonConfigs.CFG_ENABLE_VIRTUAL_THREAD_POOL;

public class Jdk21CommonsCoreInitializer implements ICoreInitializer {

    @Override
    public void initialize() {
        if (CFG_ENABLE_VIRTUAL_THREAD_POOL.get())
            VirtualThreadTaskExecutor.registerGlobalWorker();
    }

    /**
     * C4-012(a)（plan 2548）：销毁对称——虚拟线程全局池随 initializer 生命周期回收。
     * 未注册时 unregister 返回 null，不 destroy。
     */
    @Override
    public void destroy() {
        IThreadPoolExecutor executor = GlobalExecutors.unregister(GlobalExecutors.NOP_VIRTUAL_THREAD);
        if (executor != null)
            executor.destroy();
    }
}