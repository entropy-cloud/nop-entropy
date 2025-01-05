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

    @InjectValue("@cfg;nop.cluster.leader.lease-safe-gap|4000")
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

    @Override
    protected void doStart() {
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
        electionPromise.cancel(false);
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
        this.electionPromise = new CompletableFuture<>();
    }

    protected synchronized void onRestartElection() {
        this.leaderEpoch = null;
        CompletableFuture<?> promise = this.electionPromise;
        if (promise != null)
            promise.cancel(false);
        this.electionPromise = new CompletableFuture<>();
    }

    protected synchronized void onElectionCompleted(LeaderEpoch epoch) {
        this.leaderEpoch = epoch;
        CompletableFuture<LeaderEpoch> promise = this.electionPromise;
        if(promise.isDone()){
            promise = new CompletableFuture<>();
            this.electionPromise = promise;
        }
        promise.complete(epoch);
    }

    protected void onBecomeLeader(LeaderEpoch epoch) {
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.becomeLeader(epoch);
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-become-leader-listener-error", e);
            }
        }
    }

    protected void onBecomeFollower(LeaderEpoch epoch) {
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.becomeFollower(epoch);
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-become-follower-listener-error", e);
            }
        }
    }

    protected void onException(Throwable err) {
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.onException(err);
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-exception-listener-error", e);
            }
        }
    }

    protected void onStop() {
        for (ILeaderElectionListener listener : listeners) {
            try {
                listener.onStop();
            } catch (Exception e) {
                LOG.error("nop.cluster.invoke-stop-listener-error", e);
            }
        }
        this.leaderEpoch = null;
        CompletableFuture<LeaderEpoch> promise = this.electionPromise;
        if (promise != null)
            promise.cancel(false);
    }
}
