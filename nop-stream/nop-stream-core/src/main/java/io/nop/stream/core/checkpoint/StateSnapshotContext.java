/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

/**
 * Context provided to operators during checkpoint snapshot.
 */
public class StateSnapshotContext {

    private final long checkpointId;
    private final long timestamp;

    public StateSnapshotContext(long checkpointId, long timestamp) {
        this.checkpointId = checkpointId;
        this.timestamp = timestamp;
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
