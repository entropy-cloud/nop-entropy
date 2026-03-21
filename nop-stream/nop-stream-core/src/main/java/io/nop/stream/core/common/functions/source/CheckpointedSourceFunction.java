/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.source;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

/**
 * Source function with checkpoint support.
 */
public interface CheckpointedSourceFunction<T> extends SourceFunction<T> {

    /**
     * Snapshot the state of this source for a checkpoint.
     *
     * @param checkpointId The ID of the checkpoint
     * @return The snapshot result containing the source state
     */
    default OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
        return OperatorSnapshotResult.empty();
    }

    /**
     * Restore the state of this source from a checkpoint.
     *
     * @param state The state snapshot to restore from
     */
    default void initializeState(TaskStateSnapshot state) throws Exception {
    }
}
