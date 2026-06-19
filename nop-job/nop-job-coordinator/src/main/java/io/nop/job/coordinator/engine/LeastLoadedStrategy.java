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
 * 最少负载（least-loaded / spread）派发策略：在 {@code available.fits(taskCost)} 的候选 worker 中
 * 选 {@code loadScore} 最<b>小</b>的（即利用率最低、最空闲的），把负载铺开到所有 worker。
 *
 * <p>命名澄清（AR-89）：装箱（bin-packing）意义上的 best-fit 应选最<b>紧</b>的 worker（最高利用率）；
 * 本实现选最闲的，实为 worst-fit / spread。原类名 {@code SingleBestFitStrategy} 名实不符，已重命名为
 * {@code LeastLoadedStrategy}。dispatchMode 值 {@code bestFit} 与 bean id
 * {@code nopJobTaskBuilder_bestFit} 保持不变（避免路由断裂 + 存量数据迁移）；仅策略类与文档对齐。
 *
 * <p>平手时按 instanceId 字典序 tiebreaker（确定性）。
 */
public class LeastLoadedStrategy implements IWorkerAssignmentStrategy {

    @Override
    public AssignmentPlan assign(ResourceVector taskCost, List<WorkerLoad> workers) {
        if (workers == null || workers.isEmpty() || taskCost == null) {
            return AssignmentPlan.empty();
        }

        WorkerLoad best = null;
        for (WorkerLoad load : workers) {
            ResourceVector available = load.getAvailable();
            if (available == null || !available.fits(taskCost)) {
                continue;
            }
            if (best == null) {
                best = load;
            } else {
                int cmp = Double.compare(load.loadScore(), best.loadScore());
                if (cmp < 0 || (cmp == 0 && load.getInstance().getInstanceId()
                        .compareTo(best.getInstance().getInstanceId()) < 0)) {
                    best = load;
                }
            }
        }

        if (best == null) {
            return AssignmentPlan.empty();
        }
        Assignment assignment = new Assignment();
        assignment.setWorkerInstanceId(best.getInstance().getInstanceId());
        assignment.setCost(taskCost);
        return new AssignmentPlan(List.of(assignment));
    }
}
