/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.job.api.resource.ResourceVector;

import java.util.List;

/**
 * Worker 分配策略：根据 task cost 和当前 worker 负载决定 task 给哪个 worker。
 */
public interface IWorkerAssignmentStrategy {

    /**
     * @param taskCost 任务资源开销
     * @param workers  候选 worker 负载列表
     * @return 分配计划，空 plan 表示无 fitting worker
     */
    AssignmentPlan assign(ResourceVector taskCost, List<WorkerLoad> workers);
}
