/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import io.nop.cluster.elector.ILeaderElector;
import io.nop.cluster.elector.ILeaderElectionListener;
import io.nop.cluster.elector.LeaderEpoch;

import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * G24/G25: a fully controllable {@link ILeaderElector} test double for HA
 * coordinator tests. Deterministically grants / revokes leadership and fires
 * {@link ILeaderElectionListener} callbacks synchronously on the calling thread.
 *
 * <p>Test scope only — NOT a production component. Mirrors the callback contract
 * of {@code SysDaoLeaderElector}: {@code becomeLeader}/{@code becomeFollower}
 * drive role transitions, while {@link #whenElectionCompleted()} merely signals
 * "a result exists" (which may be that another node won). This separation is the
 * basis of the B1 guard test.
 */
public class TestLeaderElector implements ILeaderElector {

    private final String hostId;
    private volatile LeaderEpoch leaderEpoch;
    private volatile CompletableFuture<LeaderEpoch> electionPromise = new CompletableFuture<>();
    private final CopyOnWriteArrayList<ILeaderElectionListener> listeners = new CopyOnWriteArrayList<>();

    public TestLeaderElector(String hostId) {
        this.hostId = hostId;
    }

    @Override
    public String getHostId() {
        return hostId;
    }

    @Override
    public LeaderEpoch getLeaderEpoch() {
        return leaderEpoch;
    }

    @Override
    public AutoCloseable addElectionListener(ILeaderElectionListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public CompletionStage<LeaderEpoch> whenElectionCompleted() {
        return electionPromise;
    }

    @Override
    public void restartElection() {
        // Test double: re-arm the election promise so the next grant completes a fresh stage.
        this.leaderEpoch = null;
        this.electionPromise = new CompletableFuture<>();
    }

    // ==================== Deterministic control API ====================

    /**
     * Deterministically grants leadership to this node and synchronously fires
     * {@link ILeaderElectionListener#becomeLeader(LeaderEpoch)} on all listeners.
     */
    public void grantLeadership(long epoch) {
        LeaderEpoch le = new LeaderEpoch(hostId, epoch, new Timestamp(System.currentTimeMillis() + 30000L));
        this.leaderEpoch = le;
        completeElection(le);
        for (ILeaderElectionListener listener : listeners) {
            listener.becomeLeader(le);
        }
    }

    /**
     * Deterministically makes this node a follower of {@code otherHostId} and
     * synchronously fires
     * {@link ILeaderElectionListener#becomeFollower(LeaderEpoch)}. The election is
     * also marked completed — this is the critical case for the B1 guard: an
     * election has a result, but this node is NOT the leader.
     */
    public void loseElectionTo(String otherHostId, long epoch) {
        LeaderEpoch le = new LeaderEpoch(otherHostId, epoch, new Timestamp(System.currentTimeMillis() + 30000L));
        this.leaderEpoch = le;
        completeElection(le);
        for (ILeaderElectionListener listener : listeners) {
            listener.becomeFollower(le);
        }
    }

    /**
     * Deterministically revokes leadership (e.g. lease lost) and synchronously
     * fires {@link ILeaderElectionListener#becomeFollower(LeaderEpoch)} with a
     * null epoch — matching the {@code SysDaoLeaderElector} error/expire path.
     */
    public void revokeLeadership() {
        this.leaderEpoch = null;
        for (ILeaderElectionListener listener : listeners) {
            listener.becomeFollower(null);
        }
    }

    private void completeElection(LeaderEpoch le) {
        CompletableFuture<LeaderEpoch> promise = this.electionPromise;
        if (promise.isDone()) {
            promise = new CompletableFuture<>();
            this.electionPromise = promise;
        }
        promise.complete(le);
    }
}
