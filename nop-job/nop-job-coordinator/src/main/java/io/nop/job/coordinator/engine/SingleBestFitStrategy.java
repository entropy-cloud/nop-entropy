/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.job.api.resource.ResourceVector;

import java.util.Comparator;
import java.util.List;

/**
 * SingleBestFit 策略：在 available.fits(taskCost) 的候选中选 loadScore 最小的。
 * 平手时按 instanceId 字典序 tiebreaker。
 */
public class SingleBestFitStrategy implements IWorkerAssignmentStrategy {

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
