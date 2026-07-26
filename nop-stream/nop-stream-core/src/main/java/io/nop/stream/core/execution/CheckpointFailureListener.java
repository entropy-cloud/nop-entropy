/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

/**
 * Channel for {@link CheckpointBarrierTracker} to report an operator-snapshot
 * failure (an {@code OperatorSnapshotResult} carrying {@code hasError()}) to the
 * coordinator so the matching pending checkpoint is aborted instead of being
 * marked complete.
 *
 * <p>P1-11 closure: prior to this interface the tracker had only a success
 * channel and silently treated failed snapshots as successful ACKs.
 */
@FunctionalInterface
public interface CheckpointFailureListener {

    /**
     * Report a snapshot failure for the given checkpoint.
     *
     * @param checkpointId the checkpoint that failed
     * @param error        the snapshot error
     */
    void reportFailure(long checkpointId, Exception error);
}
