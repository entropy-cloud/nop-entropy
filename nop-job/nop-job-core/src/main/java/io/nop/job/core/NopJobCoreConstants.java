package io.nop.job.core;

import java.util.List;

public interface NopJobCoreConstants extends _NopJobCoreConstants {

    /**
     * 资源单位: CPU 毫核（millicore），1000m = 1 核。用于 taskCostCpu / capacity.cpu。
     */
    String RESOURCE_UNIT_CPU_MILLICORE = "m";

    /**
     * 资源单位: 内存 MB。用于 taskCostMemory / capacity.memory。
     */
    String RESOURCE_UNIT_MEMORY_MB = "MB";

    /**
     * 未声明 capacity 时的默认值，表示"无限容量"。worker 退化为现有 count-based 行为。
     */
    int DEFAULT_CAPACITY_IF_UNDECLARED = Integer.MAX_VALUE;

    /**
     * ServiceInstance.metadata key: worker CPU capacity（毫核）。未声明时退化为 MAX_VALUE。
     */
    String METADATA_KEY_CAPACITY_CPU = "nop.job.capacity.cpu";

    /**
     * ServiceInstance.metadata key: worker memory capacity（MB）。未声明时退化为 MAX_VALUE。
     */
    String METADATA_KEY_CAPACITY_MEMORY = "nop.job.capacity.memory";

    /**
     * 计入 reserved 的任务状态集：WAITING(0)、CLAIMED(10)、SUSPICIOUS(15)、RUNNING(20)。
     * 全部非终态。worker 侧 {@code sumReservedCost} 和 dispatcher 侧 {@code sumReservedCostByWorker}
     * 共用此状态集（见 worker-assignment-design.md §3.3.4）。
     * <p>
     * 与 {@code countInFlightTasks} 的状态集 [CLAIMED, RUNNING] 故意不对称：
     * count-based 度量并发执行数，resource-based 度量资源承诺（含已派发未执行）。
     */
    List<Integer> RESERVED_TASK_STATUSES = List.of(
            TASK_STATUS_WAITING,
            TASK_STATUS_CLAIMED,
            TASK_STATUS_SUSPICIOUS,
            TASK_STATUS_RUNNING);
}
