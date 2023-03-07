/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.starter;

import io.nop.api.core.config.IConfigExecutor;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

public class SingleThreadConfigExecutor implements IConfigExecutor {
    private IThreadPoolExecutor executor;

    @Override
    public void start() {
        executor = DefaultThreadPoolExecutor.newExecutor("nop-config-executor", 1, 100);
    }

    @Override
    public void stop() {
        executor.destroy();
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}