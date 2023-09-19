package io.nop.cluster.elector;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.io.net.IServerAddrFinder;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.StringHelper;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public abstract class AbstractLeaderElector implements ILeaderElector {

    private String addr;
    private int port;
    private IServerAddrFinder serverAddrFinder;

    private volatile LeaderEpoch leaderEpoch;
    protected volatile CompletableFuture<LeaderEpoch> electionPromise = new CompletableFuture<>();

    protected IScheduledExecutor scheduledExecutor;

    private String clusterId;

    private int leaseMs = 10000;
    private int checkIntervalMs = 2000;

    private final CopyOnWriteArrayList<ILeaderElectionListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean active;

    @InjectValue("@cfg:nop.leader.lease-time-ms|10000")
    public void setLeaseMs(int leaseMs) {
        this.leaseMs = leaseMs;
    }

    @InjectValue("@cfg:nop.leader.check-interval-ms|2000")
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

    public String getClusterId() {
        return clusterId;
    }

    public int getLeaseMs() {
        return leaseMs;
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

    @Nullable
    @Inject
    public void setServerAddrFinder(IServerAddrFinder serverAddrFinder) {
        this.serverAddrFinder = serverAddrFinder;
    }

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    @PostConstruct
    public void init() {
        active = true;
        if (StringHelper.isEmpty(addr)) {
            if (serverAddrFinder != null) {
                addr = serverAddrFinder.findAddr();
            } else {
                addr = NetHelper.findLocalIp();
            }
        }

        if (scheduledExecutor == null) {
            scheduledExecutor = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
        }

        scheduledExecutor.schedule(this::checkLeader, 0, TimeUnit.MILLISECONDS);
    }

    public boolean isActive() {
        return active;
    }

    @PreDestroy
    public void destroy() {
        active = false;
        electionPromise.cancel(false);
    }

    protected String getLeaderAddr() {
        if (port > 0)
            return addr + ':' + port;
        return addr;
    }

    @Override
    public String getHostId() {
        return AppConfig.hostId();
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
    public boolean isLeader() {
        LeaderEpoch leaderEpoch = this.leaderEpoch;
        return leaderEpoch == null ? false : leaderEpoch.getLeaderId().equals(getHostId());
    }

    @Override
    public CompletionStage<LeaderEpoch> whenElectionCompleted() {
        return electionPromise;
    }

    protected void newElection() {
        this.electionPromise = new CompletableFuture<>();
    }

    protected void updateLeader(LeaderEpoch leader) {
        LeaderEpoch current = this.leaderEpoch;
        if (current == null) {

        }
    }

    protected abstract Void checkLeader();
}
