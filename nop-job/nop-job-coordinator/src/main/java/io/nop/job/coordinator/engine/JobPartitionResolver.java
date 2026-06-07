package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.cluster.naming.PartitionAssignHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JobPartitionResolver {
    static final Logger LOG = LoggerFactory.getLogger(JobPartitionResolver.class);

    private INamingService namingService;
    private String serviceName;
    private boolean enableCluster;
    private long stableWindowMs = 30000;
    private IntRangeSet assignedPartitions;

    private volatile List<ServiceInstance> lastSeenServers;
    private volatile long lastChangeTime;

    private volatile long lastResolveTime;
    private volatile IntRangeSet cachedPartitions;
    private static final long CACHE_TTL_MS = 10_000;

    public void setNamingService(INamingService namingService) {
        this.namingService = namingService;
    }

    @InjectValue("@cfg:nop.job.cluster.service-name|")
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @InjectValue("@cfg:nop.job.cluster.enable-cluster|false")
    public void setEnableCluster(boolean enableCluster) {
        this.enableCluster = enableCluster;
    }

    @InjectValue("@cfg:nop.job.cluster.stable-window-ms|30000")
    public void setStableWindowMs(long stableWindowMs) {
        this.stableWindowMs = stableWindowMs;
    }

    public void setAssignedPartitions(String partitions) {
        if (partitions != null && !partitions.isEmpty()) {
            this.assignedPartitions = IntRangeSet.parse(partitions);
        }
    }

    public IntRangeSet resolvePartitions() {
        if (assignedPartitions != null && !assignedPartitions.isEmpty()) {
            return assignedPartitions;
        }

        if (!enableCluster || namingService == null) {
            return null;
        }

        String svcName = serviceName != null && !serviceName.isEmpty()
                ? serviceName : AppConfig.appName();
        List<ServiceInstance> servers = namingService.getInstances(svcName);
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        List<ServiceInstance> sorted = new ArrayList<>(servers);
        sorted.sort(Comparator.comparing(ServiceInstance::getInstanceId));

        if (isUnstable(sorted)) {
            cachedPartitions = null;
            LOG.info("nop.job.cluster.instances-unstable:count={},waiting-stable-window={}ms",
                    sorted.size(), stableWindowMs);
            return null;
        }

        long now = System.currentTimeMillis();
        if (cachedPartitions != null && (now - lastResolveTime) < CACHE_TTL_MS) {
            return cachedPartitions;
        }

        String myInstanceId = AppConfig.hostId();
        IntRangeBean myRange = PartitionAssignHelper.getMyRange(sorted, myInstanceId);
        if (myRange.isEmpty()) {
            LOG.warn("nop.job.cluster.my-instance-not-found:instanceId={}", myInstanceId);
            return null;
        }

        LOG.debug("nop.job.cluster.resolved-partitions:range={}", myRange);
        IntRangeSet result = myRange.toRangeSet();
        cachedPartitions = result;
        lastResolveTime = now;
        return result;
    }

    private boolean isUnstable(List<ServiceInstance> current) {
        List<ServiceInstance> prev = this.lastSeenServers;
        this.lastSeenServers = current;

        if (prev == null) {
            this.lastChangeTime = System.currentTimeMillis();
            return true;
        }

        if (current.size() != prev.size()) {
            this.lastChangeTime = System.currentTimeMillis();
            return true;
        }

        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).getInstanceId().equals(prev.get(i).getInstanceId())) {
                this.lastChangeTime = System.currentTimeMillis();
                return true;
            }
        }

        if (this.lastChangeTime > 0) {
            long elapsed = System.currentTimeMillis() - this.lastChangeTime;
            if (elapsed < stableWindowMs) {
                return true;
            }
            this.lastChangeTime = 0;
        }

        return false;
    }
}
