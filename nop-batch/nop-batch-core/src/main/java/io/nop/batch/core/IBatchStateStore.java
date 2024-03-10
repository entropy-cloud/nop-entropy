/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

/**
 * 批处理任务的持久化状态存储
 */
public interface IBatchStateStore {
    void loadTaskState(IBatchTaskContext context);

    void saveTaskState(IBatchTaskContext context);

    void loadChunkState(IBatchChunkContext context);
}
