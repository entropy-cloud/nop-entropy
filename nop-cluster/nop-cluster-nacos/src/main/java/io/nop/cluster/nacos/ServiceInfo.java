/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.nacos;

import io.nop.cluster.discovery.ServiceInstance;

import java.util.Collections;
import java.util.List;

public class ServiceInfo {
    private NacosNamingService.ServiceInfoListener listener;

    private volatile List<ServiceInstance> serviceInstances = Collections.emptyList();

    public List<ServiceInstance> getServiceInstances() {
        return serviceInstances;
    }

    public void setServiceInstances(List<ServiceInstance> instances) {
        this.serviceInstances = serviceInstances;
    }

    public NacosNamingService.ServiceInfoListener getListener() {
        return listener;
    }

    public void setListener(NacosNamingService.ServiceInfoListener listener) {
        this.listener = listener;
    }
}
