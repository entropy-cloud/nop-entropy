/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import java.util.concurrent.Executor;

public interface IPartitionedExecutor extends Executor {
    default void execute(Runnable task) {
        executeForPartition(-1, task);
    }

    /**
     * 为不同的Partition选择不同的Executor来执行。例如，属于同一个Partition的任务可能需要按顺序执行，而不同Partition 的任务可以并发执行。
     */
    void executeForPartition(int partitionIndex, Runnable task);
}
