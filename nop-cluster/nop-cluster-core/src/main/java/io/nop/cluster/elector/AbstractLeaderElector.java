/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.elector;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.io.net.IServerAddrFinder;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.StringHelper;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractLeaderElector extends LifeCycleSupport implements ILeaderElector {
    static final Logger LOG = LoggerFactory.getLogger(AbstractLeaderElector.class);

    private String addr;
    private int port;
    private IServerAddrFinder serverAddrFinder;

    private volatile LeaderEpoch leaderEpoch; //NOSONAR
    protected volatile CompletableFuture<LeaderEpoch> electionPromise = new CompletableFuture<>(); // NOSONAR

    /**
     * C1-004 (plan 2521): stop terminal-state guard. A dedicated flag — NOT
     * LifeCycleSupport.isStopping(), which is structurally blind in the post-stop
     * window (allowRestart=true returns the lifecycle to CREATED where isStopping()
     * is false) — set as the FIRST action of the locked doStop and cleared by
     * {@link #resetStoppedState()} from both doStart overrides (base + polling
     * subclass, which does not call super.doStart()).
     */
    protected volatile boolean stopped;

    /**
     * C1-004/C2-003: single private lock serializing the five state-write sites
     * (doStop/onStop-adjacent cleanup/newElection/onRestartElection/onElectionCompleted).
     * Only state changes happen inside; promise completion/cancellation and listener
     * dispatch run outside (C2-005 dispatch discipline — waiter continuations execute
     * synchronously on the completing thread).
     */
    private final Object stateLock = new Object();

    /**
     * C3-004 waiter contract: promises replaced by newElection/onRestartElection while
     * still incomplete are NOT cancelled — they are drained with the next winning
     * epoch. stop() cancels current + outstanding (honest cancellation). Growth is
     * bounded by the restart rate (no un-restared-promise pathological loop), same
     * order as the promise objects themselves.
     */
    private final Set<CompletableFuture<LeaderEpoch>> outstandingPromises = new HashSet<>();

    private String hostId;

    private String clusterId;

    private int leaseMs = 30000;
    private int checkIntervalMs = 2000;
    private int leaseSafeGap = 4000;

    private final CopyOnWriteArrayList<ILeaderElectionListener> listeners = new CopyOnWriteArrayList<>();

    @InjectValue("@cfg:nop.cluster.leader.lease-time-ms|30000")
    public void setLeaseMs(int leaseMs) {
        this.leaseMs = leaseMs;
    }

    public int getLeaseMs() {
        return leaseMs;
    }

    @InjectValue("@cfg:nop.cluster.leader.check-interval-ms|2000")
    public void setCheckIntervalMs(int checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public int getCheckIntervalMs() {
        return checkIntervalMs;
    }

    @InjectValue("@cfg:nop.cluster.id,nop.application.name")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public int getLeaseSafeGap() {
        return leaseSafeGap;
    }

    @InjectValue("@cfg:nop.cluster.leader.lease-safe-gap|4000")
    public void setLeaseSafeGap(int leaseSafeGap) {
        this.leaseSafeGap = leaseSafeGap;
    }

    public String getClusterId() {
        return clusterId;
    }

    public int getPort() {
        return port;
    }

    public String getAddr() {
        return addr;
    }

    @InjectValue("@cfg:nop.server.addr|")
    public void setAddr(String addr) {
        this.addr = addr;
    }

    @InjectValue("@cfg:nop.server.port|0")
    public void setPort(int port) {
        this.port = port;
    }

    public IServerAddrFinder getServerAddrFinder() {
        return serverAddrFinder;
    }

    @Inject
    public void setServerAddrFinder(@Nullable IServerAddrFinder serverAddrFinder) {
        this.serverAddrFinder = serverAddrFinder;
    }

    /**
     * C1-004: shared stop→start reset — clears the stopped flag and reinstalls a fresh
     * promise when the current one is already done/cancelled. MUST be invoked by every
     * doStart override (the polling subclass overrides doStart without calling
     * super.doStart(), so a base-only clear would never run for production electors).
     */
    protected final void resetStoppedState() {
        synchronized (stateLock) {
            stopped = false;
            CompletableFuture<LeaderEpoch> promise = this.electionPromise;
            if (promise != null && promise.isDone()) {
                this.electionPromise = new CompletableFuture<>();
            }
        }
    }

    protected boolean isStopped() {
        return stopped;
    }

    @Override
    protected void doStart() {
        resetStoppedState();
        if (StringHelper.isEmpty(addr)) {
            if (serverAddrFinder != null) {
                addr = serverAddrFinder.findAddr();
            } else {
                addr = NetHelper.findLocalIp();
            }
        }
    }

    @Override
    protected void doStop() {
        List<CompletableFuture<LeaderEpoch>> toCancel;
        synchronized (stateLock) {
            stopped = true; // first action — every guard consumer sees it immediately
            this.leaderEpoch = null;
            toCancel = new ArrayList<>(outstandingPromises);
            outstandingPromises.clear();
            CompletableFuture<LeaderEpoch> promise = this.electionPromise;
            if (promise != null)
                toCancel.add(promise);
        }
        // C2-005 dispatch discipline: cancellation continuations and the onStop
        // listener dispatch run outside the state lock
        for (CompletableFuture<LeaderEpoch> promise : toCancel) {
            promise.cancel(false);
        }
        onStop();
        listeners.clear();
    }

    protected String getLeaderAddr() {
        if (port > 0)
            return addr + ':' + port;
        return addr;
    }

    @Override
    public String getHostId() {
        if (hostId != null)
            return hostId;
        return AppConfig.hostId();
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    @Override
    public LeaderEpoch getLeaderEpoch() {
        return leaderEpoch;
    }

    @Override
    public AutoCloseable addElectionListener(ILeaderElectionListener listener) {
        listeners.add(listener);
        return () -> {
            listeners.remove(listener);
        };
    }

    @Override
    public CompletionStage<LeaderEpoch> whenElectionCompleted() {
        return electionPromise;
    }

    protected void newElection() {
        synchronized (stateLock) {
            rotatePromise();
        }
    }

    /**
     * C3-004: promise rotation WITHOUT cancellation — an incomplete promise being
     * replaced is parked in the outstanding set and drained with the next winning
     * epoch (waiters attached before a restart complete with the restart's result
     * instead of a spurious CancellationException).
     */
    private void rotatePromise() {
        CompletableFuture<LeaderEpoch> promise = this.electionPromise;
        if (promise != null && !promise.isDone()) {
            outstandingPromises.add(promise);
        }
        this.electionPromise = new CompletableFuture<>();
    }

    protected void onRestartElection() {
        synchronized (stateLock) {
            this.leaderEpoch = null;
            rotatePromise();
        }
    }

    protected void onElectionCompleted(LeaderEpoch epoch) {
        List<CompletableFuture<LeaderEpoch>> toComplete;
        synchronized (stateLock) {
            if (stopped) {
                // C1-004: post-stop completion — deterministic rejection, no revival of
                // leaderEpoch and no waiter notification after stop
                return;
            }
            this.leaderEpoch = epoch;
            CompletableFuture<LeaderEpoch> promise = this.electionPromise;
            if (promise.isDone()) {
                promise = new CompletableFuture<>();
                this.electionPromise = promise;
            }
            toComplete = new ArrayList<>(outstandingPromises);
            outstandingPromises.clear();
            toComplete.add(promise);
        }
        // C2-005: waiter continuations execute synchronously on the completing thread —
        // dispatch them outside the state lock
        for (CompletableFuture<LeaderEpoch> promise : toComplete) {
            promise.complete(epoch);
        }
    }

    protected void onBecomeLeader(LeaderEpoch epoch) {
        if (stopped) {
            // C1-004/C10-004 half: listener callbacks are dead after stop
            return;
        }
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.becomeLeader(epoch);
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-become-leader-listener-error", e);
            }
        }
    }

    protected void onBecomeFollower(LeaderEpoch epoch) {
        if (stopped) {
            return;
        }
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.becomeFollower(epoch);
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-become-follower-listener-error", e);
            }
        }
    }

    protected void onException(Throwable err) {
        if (stopped) {
            return;
        }
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.onException(err);
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-exception-listener-error", e);
            }
        }
    }

    /**
     * Stop-path listener dispatch, invoked from the locked doStop after state cleanup.
     * State mutation (epoch reset / promise cancellation) lives in doStop's critical
     * section — this method only dispatches.
     */
    protected void onStop() {
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.onStop();
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-stop-listener-error", e);
            }
        }
    }
}
