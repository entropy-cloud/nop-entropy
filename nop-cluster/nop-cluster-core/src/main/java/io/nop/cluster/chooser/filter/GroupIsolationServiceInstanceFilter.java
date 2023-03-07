/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.chooser.filter;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.cluster.chooser.IRequestServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.Objects;

/**
 * 如果启用组隔离机制，则限制服务的消费者和生产者属于同一个group。
 */
public class GroupIsolationServiceInstanceFilter implements IRequestServiceInstanceFilter<ApiRequest<?>> {
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean accept(ServiceInstance serviceInstance, ApiRequest<?> request) {
        if (!isEnabled())
            return true;

        String reqGroup = ApiHeaders.getSvcGroup(request);
        return Objects.equals(reqGroup, serviceInstance.getMetadata(ServiceInstance.META_GROUP));
    }
}