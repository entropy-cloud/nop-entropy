/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.sink;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import io.nop.api.core.annotations.core.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.SinkFunction;

@Internal
public abstract class TwoPhaseCommitSinkFunction<IN> implements SinkFunction<IN>, CheckpointParticipant {

    public static final String PENDING_COMMITS_KEY = "pending-commits";

    protected static final Logger LOG = LoggerFactory.getLogger(TwoPhaseCommitSinkFunction.class);

    private Map<Long, Object> pendingCommits;

    protected TwoPhaseCommitSinkFunction() {
        this.pendingCommits = Collections.synchronizedMap(new TreeMap<>());
    }

    public abstract void beginTransaction() throws Exception;

    @Override
    public void consume(IN value) throws Exception {
        invoke(value);
    }

    public abstract void invoke(IN value) throws Exception;

    public abstract void preCommit(long checkpointId) throws Exception;

    public abstract void commit(long checkpointId) throws Exception;

    public abstract void rollback() throws Exception;

    public void recover(long checkpointId) throws Exception {
        rollback();
        beginTransaction();
    }

    public Map<Long, Object> getPendingCommits() {
        return pendingCommits;
    }

    public void setPendingCommits(Map<Long, Object> pending) {
        this.pendingCommits = pending;
    }

    @Override
    public TaskStateSnapshot saveState(long epochId) throws Exception {
        return null;
    }

    @Override
    public void prepareCommit(long epochId) throws Exception {
        preCommit(epochId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void finishCommit(long epochId, boolean success) throws Exception {
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
    public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
        Map<Long, Object> pending = getPendingCommits();
        if (pending != null && !pending.isEmpty()) {
            for (Object tx : pending.values()) {
                try {
                    rollback();
                } catch (Exception e) {
                    LOG.warn("Rollback failed for pending transaction during recovery: {}", tx, e);
                }
            }
            pending.clear();
        }
        recover(epochId);
    }
}
