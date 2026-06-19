/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ARG_TASK_COST;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_FITTING_WORKER;

/**
 * Best-fit 派发的 task builder：调用 {@link IWorkerLoadProvider} 获取 worker 负载，
 * 通过 {@link IWorkerAssignmentStrategy} 决策最优 worker，显式写 task.workerInstanceId。
 * <p>
 * 无 fitting worker 时抛 {@link NopException}（不静默 fallback）。
 * serviceName 缺失时 fallback 到 {@link DefaultJobTaskBuilder}。
 */
public class AdaptiveJobTaskBuilder implements IJobTaskBuilder {

    private IWorkerLoadProvider loadProvider;
    private IWorkerAssignmentStrategy strategy = new LeastLoadedStrategy();
    private IJobScheduleStore scheduleStore;
    private final IJobTaskBuilder fallback = new DefaultJobTaskBuilder();

    @Inject
    public void setLoadProvider(IWorkerLoadProvider loadProvider) {
        this.loadProvider = loadProvider;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    public void setStrategy(IWorkerAssignmentStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        if (loadProvider == null) {
            throw new NopException(io.nop.job.core.JobCoreErrors.ERR_JOB_WORKER_CAPACITY_PROVIDER_REQUIRED)
                    .param("reason", "IWorkerLoadProvider not injected into AdaptiveJobTaskBuilder");
        }

        Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
        if (jobParams == null) {
            return fallback.buildTasks(fire);
        }

        String serviceName = IJobTaskBuilder.resolveServiceName(jobParams);
        if (serviceName == null || serviceName.isBlank()) {
            return fallback.buildTasks(fire);
        }

        NopJobSchedule schedule = scheduleStore != null
                ? scheduleStore.loadSchedule(fire.getJobScheduleId()) : null;
        ResourceVector taskCost = resolveCost(schedule);

        List<WorkerLoad> workers = loadProvider.getWorkerLoads(serviceName);
        AssignmentPlan plan = strategy.assign(taskCost, workers);

        if (plan.isEmpty()) {
            throw new NopException(ERR_JOB_NO_FITTING_WORKER)
                    .param(ARG_TASK_COST, taskCost.toString())
                    .param("serviceName", serviceName);
        }

        Assignment assignment = plan.getAssignments().get(0);
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
        task.setWorkerInstanceId(assignment.getWorkerInstanceId());
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setCostCpu(taskCost.getCpu());
        task.setCostMemory(taskCost.getMemory());
        if (schedule != null) {
            task.setPriority(schedule.getPriority());
        }
        task.setCreatedBy("system");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("system");
        task.setUpdateTime(new Timestamp(now));

        return List.of(task);
    }

    private static ResourceVector resolveCost(NopJobSchedule schedule) {
        if (schedule == null) {
            return ResourceVector.ZERO;
        }
        int cpu = schedule.getTaskCostCpu() != null ? schedule.getTaskCostCpu() : 0;
        int memory = schedule.getTaskCostMemory() != null ? schedule.getTaskCostMemory() : 0;
        return new ResourceVector(cpu, memory);
    }
}
