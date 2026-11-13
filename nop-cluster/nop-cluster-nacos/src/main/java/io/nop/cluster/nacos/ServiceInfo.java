/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.nacos;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.lang.IDestroyable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServiceInfo implements IDestroyable {
    private NacosNamingService.ServiceInfoListener listener;

    private List<ServiceInstance> serviceInstances = Collections.emptyList();

    public synchronized List<ServiceInstance> getServiceInstances() {
        return serviceInstances;
    }

    public synchronized List<ServiceInstance> copyServiceInstances(){
        return new ArrayList<>(serviceInstances);
    }

    public synchronized void setServiceInstances(List<ServiceInstance> instances) {
        this.serviceInstances = instances;
    }

    public NacosNamingService.ServiceInfoListener getListener() {
        return listener;
    }

    public void setListener(NacosNamingService.ServiceInfoListener listener) {
        this.listener = listener;
    }

    /**
     * 4.2-MA-03（plan 2559 裁定 A②）: the instance cache is bounded with destroy-on-remove —
     * when an entry is evicted (LRU) or cleared, its remote subscription must be torn down
     * with it, otherwise evicted entries leak nacos listeners.
     */
    @Override
    public void destroy() {
        NacosNamingService.ServiceInfoListener l = this.listener;
        if (l != null) {
            l.unsubscribeQuietly();
        }
    }
}
