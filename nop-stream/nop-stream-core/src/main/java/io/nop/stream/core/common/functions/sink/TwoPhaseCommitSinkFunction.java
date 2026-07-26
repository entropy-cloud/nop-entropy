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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
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

    /**
     * Abort a transaction that was prepared for an epoch strictly greater than the
     * durable epoch {@code epochId} (i.e. non-durable, in-flight work that the runtime
     * never durably committed). The default implementation delegates to {@link #rollback()}
     * so existing subclasses (including the 13+ test sinks) keep their pre-existing
     * behavior without override. Subclasses that distinguish per-epoch transactions
     * should override this to abort exactly the transaction for {@code epochId}.
     *
     * <p>See {@code checkpoint-design.md} §6.4: durable-but-not-committed transactions
     * must NOT be aborted here — they are re-committed by {@link #restoreFromEpoch}.
     */
    public void abort(long epochId) throws Exception {
        rollback();
    }

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
        TaskStateSnapshot snapshot = new TaskStateSnapshot(new TaskLocation(), epochId);
        Map<Long, Object> copy = new TreeMap<>(pendingCommits);
        snapshot.putOperatorState(PENDING_COMMITS_KEY, copy);
        return snapshot;
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
                TreeMap<Long, Object> toCommit;
                synchronized (pending) {
                    toCommit = new TreeMap<>();
                    for (Map.Entry<Long, Object> entry : pending.entrySet()) {
                        if (entry.getKey() <= epochId) {
                            toCommit.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                for (Map.Entry<Long, Object> entry : toCommit.entrySet()) {
                    Long eid = entry.getKey();
                    synchronized (pending) {
                        if (!pending.containsKey(eid))
                            continue;
                    }
                    commit(eid);
                    synchronized (pending) {
                        pending.remove(eid);
                    }
                }
            } else {
                commit(epochId);
            }
        }
    }

    /**
     * Restore the sink from a given durable {@code epochId}.
     *
     * <p>This method consumes the in-memory {@link #getPendingCommits()} map that the
     * upstream {@code StreamSinkOperator.restoreState} has already rebuilt from the
     * durable checkpoint (the {@code participant-" + PENDING_COMMITS_KEY} operator-state
     * entry). It then separates durable-but-not-committed pending transactions
     * ({@code epochId <= N}, committed on restore to honor exactly-once) from non-durable
     * in-flight transactions ({@code epochId > N}, aborted since they were never durable).
     *
     * <p>Implements {@code checkpoint-design.md} §6.4 invariant: durable-but-not-committed
     * sink transactions MUST be re-committed, not blindly rolled back. The prior
     * implementation blindly called {@link #rollback()} N times on the same current tx
     * and then {@code pending.clear()} — that silently dropped durable pending
     * transactions and broke exactly-once.
     *
     * <p>Signature strategy: a new {@link #abort(long)} method (with a default
     * implementation delegating to {@link #rollback()}) is added so 13+ existing test
     * subclasses that only override {@code rollback()} continue to work — the per-epoch
     * abort path stays equivalent to the previous rollback path for them, but durable
     * pending transactions ({@code epochId <= N}) are now committed via {@link #commit(long)}.
     *
     * @param epochId the durable epoch restored from the checkpoint
     * @param state   the task-level state snapshot (not used by this base implementation;
     *                the per-sink pending map has already been rebuilt upstream)
     */
    @Override
    public void restoreFromEpoch(long epochId, TaskStateSnapshot state) throws Exception {
        Map<Long, Object> pending = getPendingCommits();
        if (pending != null && !pending.isEmpty()) {
            TreeMap<Long, Object> toCommit = new TreeMap<>();
            TreeMap<Long, Object> toAbort = new TreeMap<>();
            synchronized (pending) {
                for (Map.Entry<Long, Object> entry : pending.entrySet()) {
                    Long eid = entry.getKey();
                    if (eid <= epochId) {
                        toCommit.put(eid, entry.getValue());
                    } else {
                        toAbort.put(eid, entry.getValue());
                    }
                }
            }

            for (Map.Entry<Long, Object> entry : toCommit.entrySet()) {
                Long eid = entry.getKey();
                try {
                    commit(eid);
                } catch (Exception e) {
                    LOG.error("Commit failed for durable pending transaction epoch={} on restore; "
                            + "retaining in pending for subsuming commit", eid, e);
                    continue;
                }
                synchronized (pending) {
                    pending.remove(eid);
                }
            }

            for (Map.Entry<Long, Object> entry : toAbort.entrySet()) {
                Long eid = entry.getKey();
                try {
                    abort(eid);
                } catch (Exception e) {
                    LOG.warn("Abort failed for non-durable pending transaction epoch={} on restore", eid, e);
                }
                synchronized (pending) {
                    pending.remove(eid);
                }
            }
        }
        beginTransaction();
    }
}
