/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds one NopJobTask per registered service instance for broadcast RPC.
 * Each task carries the target host, shardingIndex, and shardingTotal as
 * entity columns so that the worker can read them via typed getters and
 * inject the {@code nop-svc-target-host} header for per-instance routing.
 * <p>
 * Plan 339：不再内嵌 {@link DefaultJobTaskBuilder} fallback。serviceName 缺失 /
 * discoveryClient 未注入 / 无健康实例时由 {@link AbstractServiceTaskBuilder} 显式抛错
 * （ERR_JOB_SERVICE_NAME_REQUIRED / ERR_JOB_DISCOVERY_CLIENT_REQUIRED /
 * ERR_JOB_NO_AVAILABLE_INSTANCE），fire 留 DISPATCHING 等 timeout 回收。
 */
public class RpcBroadcastTaskBuilder extends AbstractServiceTaskBuilder {

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        String serviceName = requireServiceName(fire);
        List<ServiceInstance> healthyInstances = resolveHealthyInstances(serviceName);

        List<NopJobTask> tasks = new ArrayList<>();
        int total = healthyInstances.size();
        for (int i = 0; i < total; i++) {
            ServiceInstance instance = healthyInstances.get(i);

            NopJobTask task = newTask(fire, i + 1);
            task.setWorkerInstanceId(instance.getInstanceId());

            // Dispatch routing: columns instead of JSON payload
            task.setTargetHost(instance.getHost());
            task.setShardingIndex(i);
            task.setShardingTotal(total);

            tasks.add(task);
        }
        return tasks;
    }
}
