package io.nop.job.coordinator.engine;

import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;
import java.util.Map;

/**
 * Builds task(s) from a fire event. The default implementation creates a single task.
 * Custom implementations (e.g., for broadcast RPC) can create multiple tasks.
 */
public interface IJobTaskBuilder {
    /**
     * Build one or more tasks for the given fire event.
     * Each task should have taskNo, taskStatus, taskPayload, partitionIndex, etc. set.
     */
    List<NopJobTask> buildTasks(NopJobFire fire);

    /**
     * AR-99 类型安全的 serviceName 提取。jobParams 中 {@code serviceName} 为 String 时返回之；
     * 缺失、null、或非 String 类型（如数字/JSON 对象）时返回 null（调用方据此 fallback 到 single 模式），
     * 避免 {@code (String) jobParams.get("serviceName")} 在非 String 时抛 ClassCastException 中断批次。
     */
    static String resolveServiceName(Map<String, Object> jobParams) {
        if (jobParams == null) {
            return null;
        }
        Object value = jobParams.get("serviceName");
        return value instanceof String ? (String) value : null;
    }
}
