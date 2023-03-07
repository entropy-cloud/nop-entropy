/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import java.util.concurrent.Executor;

/**
 * 配置的更新在单一线程上进行
 */
public interface IConfigExecutor extends Executor {
    default String getName() {
        return getClass().getName();
    }

    void start();

    void stop();
}
