package io.nop.job.core;

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
}
