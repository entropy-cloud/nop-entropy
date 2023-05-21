/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.chooser;

import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

public interface IRequestServiceInstanceFilter<R> {
    boolean isEnabled();

    void filter(List<ServiceInstance> instances, R request, boolean onlyPreferred);
}