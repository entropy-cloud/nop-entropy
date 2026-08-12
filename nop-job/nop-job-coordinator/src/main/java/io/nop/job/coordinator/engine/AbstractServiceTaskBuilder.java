/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.job.core.JobCoreErrors.ARG_DISPATCH_MODE;
import static io.nop.job.core.JobCoreErrors.ARG_HEALTHY_COUNT;
import static io.nop.job.core.JobCoreErrors.ARG_JOB_FIRE_ID;
import static io.nop.job.core.JobCoreErrors.ARG_SERVICE_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_DISCOVERY_CLIENT_REQUIRED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_AVAILABLE_INSTANCE;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED;

/**
 * Plan 339 共享基类：吸收 service 型 task builder（broadcast/partition/bestFit）共有的
 * daoProvider 注入、task 实体填充、serviceName 前置校验、discovery 健康实例过滤。
 * <p>
 * 与旧实现的关键差异（plan 339）：不再内嵌 {@link DefaultJobTaskBuilder} fallback——配置缺失
 * （serviceName 缺失、discoveryClient 未注入）与运行时瞬态（无健康实例）一律显式抛错，由 dispatcher
 * 的 per-fire 错误隔离捕获（fire 留 DISPATCHING 等 timeout 回收），不再静默降级为单任务。
 */
public abstract class AbstractServiceTaskBuilder implements IJobTaskBuilder {

    private IDiscoveryClient discoveryClient;
    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setDiscoveryClient(@Nullable IDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    protected IDaoProvider daoProvider() {
        return daoProvider;
    }

    /**
     * 校验并返回 serviceName：jobParams 缺失、serviceName 缺失/null/非 String → 抛
     * {@code ERR_JOB_SERVICE_NAME_REQUIRED}（显式失败，不 fallback）。
     */
    protected String requireServiceName(NopJobFire fire) {
        Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
        String serviceName = IJobTaskBuilder.resolveServiceName(jobParams);
        if (serviceName == null || serviceName.isBlank()) {
            throw new NopException(ERR_JOB_SERVICE_NAME_REQUIRED)
                    .param(ARG_DISPATCH_MODE, fire.getDispatchMode())
                    .param(ARG_JOB_FIRE_ID, fire.getJobFireId());
        }
        return serviceName;
    }

    /**
     * 返回 healthy+enabled 实例列表：discoveryClient 未注入 → 配置错误
     * {@code ERR_JOB_DISCOVERY_CLIENT_REQUIRED}；无实例或无健康实例 → 运行时瞬态
     * {@code ERR_JOB_NO_AVAILABLE_INSTANCE}（两码拆分便于排障）。
     */
    protected List<ServiceInstance> resolveHealthyInstances(String serviceName) {
        if (discoveryClient == null) {
            throw new NopException(ERR_JOB_DISCOVERY_CLIENT_REQUIRED)
                    .param(ARG_SERVICE_NAME, serviceName);
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new NopException(ERR_JOB_NO_AVAILABLE_INSTANCE)
                    .param(ARG_SERVICE_NAME, serviceName);
        }
        List<ServiceInstance> healthy = instances.stream()
                .filter(instance -> instance.isHealthy() && instance.isEnabled())
                .collect(Collectors.toList());
        if (healthy.isEmpty()) {
            throw new NopException(ERR_JOB_NO_AVAILABLE_INSTANCE)
                    .param(ARG_SERVICE_NAME, serviceName)
                    .param(ARG_HEALTHY_COUNT, 0);
        }
        return healthy;
    }

    /**
     * 创建 task 实体并填充公共列（jobFireId/taskNo/taskStatus/partitionIndex）。
     */
    protected NopJobTask newTask(NopJobFire fire, int taskNo) {
        NopJobTask task = daoProvider.daoFor(NopJobTask.class).newEntity();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(taskNo);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
        task.setPartitionIndex(fire.getPartitionIndex());
        return task;
    }
}
