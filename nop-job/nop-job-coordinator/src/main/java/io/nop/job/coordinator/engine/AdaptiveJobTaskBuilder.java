/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.job.core.JobCoreErrors.ARG_TASK_COST;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_FITTING_WORKER;

/**
 * 基于 worker 负载的 task builder：调用 {@link IWorkerLoadProvider} 获取 worker 负载，
 * 通过可配置的 {@link IWorkerAssignmentStrategy} 决策任务分配，为每个 {@link Assignment} 生成一个 task。
 * <p>
 * 默认策略 {@link LeastLoadedStrategy} 选最空闲的单个 worker（返回 1 个 assignment）。
 * 自定义策略可返回多个 assignment（如广播到所有 worker），builder 会为每个 assignment 生成一个 task。
 * <p>
 * 无 fitting worker 时抛 {@link NopException}（不静默 fallback）。
 * serviceName 缺失时 fallback 到 {@link DefaultJobTaskBuilder}。
 */
public class AdaptiveJobTaskBuilder implements IJobTaskBuilder {

    private IWorkerLoadProvider loadProvider;
    private IWorkerAssignmentStrategy strategy = new LeastLoadedStrategy();
    private IJobScheduleStore scheduleStore;
    private final IJobTaskBuilder fallback = new DefaultJobTaskBuilder();
    private IDaoProvider daoProvider;

    @Inject
    public void setLoadProvider(IWorkerLoadProvider loadProvider) {
        this.loadProvider = loadProvider;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
        if (fallback instanceof DefaultJobTaskBuilder) {
            ((DefaultJobTaskBuilder) fallback).setDaoProvider(daoProvider);
        }
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

        if (plan == null || plan.isEmpty()) {
            throw new NopException(ERR_JOB_NO_FITTING_WORKER)
                    .param(ARG_TASK_COST, taskCost.toString())
                    .param("serviceName", serviceName);
        }

        List<Assignment> assignments = plan.getAssignments();
        List<NopJobTask> tasks = new ArrayList<>(assignments.size());

        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = assignments.get(i);
            if (assignment == null) {
                throw new IllegalStateException("AdaptiveJobTaskBuilder received null assignment at index " + i);
            }
            if (assignment.getWorkerInstanceId() == null || assignment.getWorkerInstanceId().isBlank()) {
                throw new IllegalStateException("AdaptiveJobTaskBuilder requires non-blank workerInstanceId at index " + i);
            }

            ResourceVector assignedCost = assignment.getCost() != null ? assignment.getCost() : taskCost;

            NopJobTask task = daoProvider.daoFor(NopJobTask.class).newEntity();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(i + 1);
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
            task.setWorkerInstanceId(assignment.getWorkerInstanceId());
            task.setTargetHost(assignment.getTargetHost());
            task.setShardingIndex(assignment.getShardingIndex());
            task.setShardingTotal(assignment.getShardingTotal());
            task.setPartitionRange(assignment.getPartitionRange());
            task.setPartitionIndex(fire.getPartitionIndex());
            task.setCostCpu(assignedCost.getCpu());
            task.setCostMemory(assignedCost.getMemory());
            if (schedule != null) {
                task.setPriority(schedule.getPriority());
            }
            tasks.add(task);
        }

        return tasks;
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
