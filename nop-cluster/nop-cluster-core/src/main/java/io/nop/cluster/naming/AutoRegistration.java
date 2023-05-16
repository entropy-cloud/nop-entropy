/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.naming;

import io.nop.api.core.config.AppConfig;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.io.net.IServerAddrFinder;
import io.nop.commons.util.StringHelper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

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

    protected ServiceInstance getServiceInstance() {
        ServiceInstance instance = new ServiceInstance();
        instance.setEnabled(true);
        instance.setEphemeral(true);

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
        // instance.setInstanceId(UUID.randomUUID().toString());
        this.instance = instance;
        return instance;
    }

    @PostConstruct
    public void start() {
        namingService.registerInstance(getServiceInstance());
    }

    @PreDestroy
    public void stop() {
        if (instance == null)
            return;
        namingService.unregisterInstance(instance);
        instance = null;
    }
}