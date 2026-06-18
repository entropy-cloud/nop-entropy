/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.api.resource.ResourceVector;

/**
 * Worker 的负载快照（capacity + reserved + available + loadScore）。
 * 由 {@link IWorkerLoadProvider} 从服务发现 + task 表聚合派生。
 */
@DataBean
public class WorkerLoad {
    private ServiceInstance instance;
    private ResourceVector capacity;
    private ResourceVector reserved;

    public ServiceInstance getInstance() {
        return instance;
    }

    public void setInstance(ServiceInstance instance) {
        this.instance = instance;
    }

    public ResourceVector getCapacity() {
        return capacity;
    }

    public void setCapacity(ResourceVector capacity) {
        this.capacity = capacity;
    }

    public ResourceVector getReserved() {
        return reserved;
    }

    public void setReserved(ResourceVector reserved) {
        this.reserved = reserved;
    }

    public ResourceVector getAvailable() {
        if (capacity == null) return ResourceVector.ZERO;
        if (reserved == null) return capacity;
        return capacity.subtract(reserved);
    }

    /**
     * 负载得分 = max(reserved.cpu/capacity.cpu, reserved.memory/capacity.memory)。
     * capacity 为 MAX_VALUE 时返回 0（无上限 = 无负载）。
     */
    public double loadScore() {
        if (reserved == null || capacity == null) return 0;
        return reserved.loadScore(capacity);
    }
}
