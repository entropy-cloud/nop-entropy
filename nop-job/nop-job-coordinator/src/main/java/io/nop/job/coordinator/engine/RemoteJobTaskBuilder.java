package io.nop.job.coordinator.engine;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote 模式（dispatchMode=remote）任务构建器：为每个健康服务实例创建一个归因 task。
 * 任务由 coordinator 侧 {@code RemoteDispatchScanner} 专属认领并远程执行，DB 模式 worker
 * 的 {@code fetchWaitingTasks} 已显式排除（fire.dispatchMode=remote），两者结果集互斥。
 * <p>
 * 每个 task 携带 {@code workerInstanceId}（目标实例 id）与 {@code targetHost}（实例 host），
 * coordinator 侧远程调用时把 {@code targetHost} 注入 {@code nop-svc-target-host} header，
 * 经平台 {@code SpecificServiceInstanceFilter} 精确路由到该实例。
 * <p>
 * 配置缺失（serviceName 缺失 / discoveryClient 未注入 / 无健康实例）由
 * {@link AbstractServiceTaskBuilder} 显式抛错，不静默降级。
 */
public class RemoteJobTaskBuilder extends AbstractServiceTaskBuilder {

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        String serviceName = requireServiceName(fire);
        List<ServiceInstance> healthyInstances = resolveHealthyInstances(serviceName);

        List<NopJobTask> tasks = new ArrayList<>();
        for (int i = 0; i < healthyInstances.size(); i++) {
            ServiceInstance instance = healthyInstances.get(i);

            NopJobTask task = newTask(fire, i + 1);
            task.setWorkerInstanceId(instance.getInstanceId());
            task.setTargetHost(instance.getHost());

            tasks.add(task);
        }
        return tasks;
    }
}
