/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;

import java.io.Serializable;
import java.util.List;

public interface IOperatorStateBackend extends Serializable {

    OperatorSnapshotResult snapshotState(long checkpointId) throws Exception;

    void restoreState(OperatorSnapshotResult snapshot) throws Exception;

    void restoreState(List<OperatorSnapshotResult> oldSnapshots, int oldParallelism,
                      RedistributionMode mode, int taskIndex, int newParallelism) throws Exception;

    Object getRawState(String key);

    void putRawState(String key, Object value);
}
