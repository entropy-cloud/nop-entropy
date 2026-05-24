/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.sink;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.SinkFunction;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@Internal
public interface TwoPhaseCommitSinkFunction<IN> extends SinkFunction<IN>, CheckpointParticipant {

    String PENDING_COMMITS_KEY = "pending-commits";

    void beginTransaction() throws Exception;

    @Override
    default void consume(IN value) throws Exception {
        invoke(value);
    }

    void invoke(IN value) throws Exception;

    void preCommit(long checkpointId) throws Exception;

    void commit(long checkpointId) throws Exception;

    void rollback() throws Exception;

    default void recover(long checkpointId) throws Exception {
        rollback();
        beginTransaction();
    }

    Map<Long, Object> getPendingCommits();

    void setPendingCommits(Map<Long, Object> pending);

    @Override
    default TaskStateSnapshot saveState(long epochId) throws Exception {
        return null;
    }

    @Override
    default void prepareCommit(long epochId) throws Exception {
        preCommit(epochId);
    }

    @Override
    @SuppressWarnings("unchecked")
    default void finishCommit(long epochId, boolean success) throws Exception {
        Map<Long, Object> pending = getPendingCommits();

        if (success) {
            if (pending != null && !pending.isEmpty()) {
                TreeMap<Long, Object> toCommit = new TreeMap<>();
                for (Map.Entry<Long, Object> entry : pending.entrySet()) {
                    if (entry.getKey() <= epochId) {
                        toCommit.put(entry.getKey(), entry.getValue());
                    }
                }
                for (Long eid : toCommit.keySet()) {
                    commit(eid);
                    pending.remove(eid);
                }
            } else {
                commit(epochId);
            }
        }
    }

    @Override
    default void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
        Map<Long, Object> pending = getPendingCommits();
        if (pending != null && !pending.isEmpty()) {
            for (Object tx : pending.values()) {
                try {
                    rollback();
                } catch (Exception e) {
                    // best effort
                }
            }
            pending.clear();
        }
        recover(epochId);
    }
}
