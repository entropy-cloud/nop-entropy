/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions;

import io.nop.stream.core.checkpoint.FunctionInitializationContext;
import io.nop.stream.core.checkpoint.FunctionSnapshotContext;

/**
 * Interface for stream functions that participate in checkpointing.
 * Analogous to Flink's {@code CheckpointedFunction} but adapted for nop-stream.
 */
public interface ICheckpointedFunction extends StreamFunction {

    /**
     * Called when a checkpoint is being taken. The function should persist its state.
     *
     * @param context the context for the snapshot
     * @throws Exception if snapshotting fails
     */
    void snapshotState(FunctionSnapshotContext context) throws Exception;

    /**
     * Called when the function's state is being restored from a checkpoint.
     *
     * @param context the context for the initialization
     * @throws Exception if restoration fails
     */
    void initializeState(FunctionInitializationContext context) throws Exception;
}
