/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.naming;

import com.vdurmont.semver4j.Requirement;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.io.net.IServerAddrFinder;
import io.nop.commons.util.StringHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.cluster.ClusterErrors.ARG_VERSION;
import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_APP_VERSION_MUST_BE_NPM_LIKE;

/**
 * 启动时自动把本服务注册到NamingService上，并在停止时自动注销注册
 */
public class AutoRegistration {

    private INamingService namingService;

    private String serviceName;
    private String clusterName = "DEFAULT";
    private String addr;
    private int port = 9001;
    private int weight = 100;

    private IServerAddrFinder addrFinder;

    private Set<String> tags;
    private Map<String, String> metadata;

    private ServiceInstance instance;

    private Duration autoUpdateInterval;

    private Future<?> autoUpdateTimerFuture;

    private boolean ephemeral = true;

    public AutoRegistration(INamingService namingService) {
        this.namingService = namingService;
    }

    public AutoRegistration() {
    }

    @Inject
    public void setNamingService(INamingService namingService) {
        this.namingService = namingService;
    }

    @Inject
    public void setAddrFinder(IServerAddrFinder addrFinder) {
        this.addrFinder = addrFinder;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void setAutoUpdateInterval(Duration autoUpdateInterval) {
        this.autoUpdateInterval = autoUpdateInterval;
    }

    protected ServiceInstance getServiceInstance() {
        ServiceInstance instance = new ServiceInstance();
        instance.setEnabled(true);
        instance.setEphemeral(ephemeral);

        String serviceName = this.serviceName;
        if (serviceName == null)
            serviceName = AppConfig.appName();

        instance.setServiceName(serviceName);
        instance.setHealthy(true);
        instance.setClusterName(this.clusterName);
        String addr = this.addr;
        if (StringHelper.isEmpty(addr))
            addr = addrFinder.findAddr();

        instance.setAddr(addr);
        instance.setPort(port);
        instance.setWeight(weight);
        instance.setMetadata(metadata);
        instance.setTags(tags);
        instance.setInstanceId(AppConfig.hostId());
        this.instance = instance;
        return instance;
    }

    @PostConstruct
    public void start() {
        if (metadata != null) {
            String version = metadata.get("version");
            if (version != null) {
                try {
                    Requirement.buildNPM(version);
                } catch (Exception e) {
                    throw new NopException(ERR_CLUSTER_APP_VERSION_MUST_BE_NPM_LIKE)
                            .param(ARG_VERSION, version);
                }
            }
        }
        namingService.registerInstance(getServiceInstance());

        if (autoUpdateInterval != null && autoUpdateInterval.toMillis() > 0) {
            autoUpdateTimerFuture = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())
                    .scheduleWithFixedDelay(this::refreshRegistration, autoUpdateInterval.toMillis(), autoUpdateInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    protected void refreshRegistration() {
        if (instance != null) {
            namingService.updateInstance(instance);
        }
    }

    @PreDestroy
    public void stop() {
        if (autoUpdateTimerFuture != null)
            autoUpdateTimerFuture.cancel(false);
        autoUpdateTimerFuture = null;
        if (instance == null)
            return;
        namingService.unregisterInstance(instance);
        instance = null;
    }
}