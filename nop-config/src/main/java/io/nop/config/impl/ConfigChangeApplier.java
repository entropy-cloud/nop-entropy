/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigChangeApplier {
    private final IConfigExecutor executor;
    private final Runnable changeHandler;

    /**
     * 每当配置发生变化，都标记配置需要更新。如果当前处于active状态，则在configExecutor上执行applyChange操作
     */
    private final AtomicBoolean shouldUpdate = new AtomicBoolean();

    private final AtomicBoolean active = new AtomicBoolean();

    public ConfigChangeApplier(IConfigExecutor executor, Runnable changeHandler) {
        this.executor = executor;
        this.changeHandler = changeHandler;
    }

    public void activate() {
        if (active.compareAndSet(false, true)) {
            this.requestUpdate();
        }
    }

    public void deactivate() {
        active.compareAndSet(true, false);
    }

    public void requestUpdate() {
        if (this.active.get()) {
            if (this.shouldUpdate.compareAndSet(false, true)) {
                executor.execute(this::applyChange);
            }
        }
    }

    void applyChange() {
        this.shouldUpdate.set(false);
        if (!active.get())
            return;

        this.changeHandler.run();
    }
}
