package io.nop.commons.jdk21;

import io.nop.core.initialize.ICoreInitializer;

import static io.nop.commons.CommonConfigs.CFG_ENABLE_VIRTUAL_THREAD_POOL;

public class Jdk21CommonsCoreInitializer implements ICoreInitializer {

    @Override
    public void initialize() {
        if (CFG_ENABLE_VIRTUAL_THREAD_POOL.get())
            VirtualThreadTaskExecutor.registerGlobalWorker();
    }
}