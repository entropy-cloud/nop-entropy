/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.discovery.filter;

import io.nop.cluster.discovery.IServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;

public class HealthyServiceInstanceFilter implements IServiceInstanceFilter {
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean accept(ServiceInstance instance) {
        return instance.isHealthy() && instance.isEnabled();
    }
}
