/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.naming;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

/**
 * 服务注册中心
 */
public interface INamingService extends IDiscoveryClient {
    void registerInstance(ServiceInstance instance);

    void unregisterInstance(ServiceInstance instance);

    default void updateInstance(ServiceInstance instance) {

    }

    List<String> getServices();

    List<ServiceInstance> getInstances(String serviceName);
}