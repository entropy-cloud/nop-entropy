/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.participant;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;

public interface CheckpointParticipant {

    TaskStateSnapshot saveState(long epochId) throws Exception;

    void prepareCommit(long epochId) throws Exception;

    void finishCommit(long epochId, boolean success) throws Exception;

    void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception;
}
