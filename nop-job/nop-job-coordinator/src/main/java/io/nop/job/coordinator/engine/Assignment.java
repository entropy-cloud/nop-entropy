/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.job.api.resource.ResourceVector;

/**
 * 单个 worker 的任务分配结果。
 */
@DataBean
public class Assignment {
    private String workerInstanceId;
    private ResourceVector cost;

    public String getWorkerInstanceId() {
        return workerInstanceId;
    }

    public void setWorkerInstanceId(String workerInstanceId) {
        this.workerInstanceId = workerInstanceId;
    }

    public ResourceVector getCost() {
        return cost;
    }

    public void setCost(ResourceVector cost) {
        this.cost = cost;
    }
}
