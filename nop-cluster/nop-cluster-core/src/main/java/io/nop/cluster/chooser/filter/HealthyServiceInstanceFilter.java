/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.chooser.filter;

import io.nop.cluster.chooser.IRequestServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

public class HealthyServiceInstanceFilter<R> implements IRequestServiceInstanceFilter<R> {
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void filter(List<ServiceInstance> instances, R request, boolean onlyPreferred) {
        instances.removeIf(instance -> !instance.isHealthy() || !instance.isEnabled());
    }
}
