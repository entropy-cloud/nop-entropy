package io.nop.job.api.resource;

import java.util.Objects;

/**
 * 多维资源向量（至少 CPU 毫核 + 内存 MB 两维），用于 worker 侧资源自限制
 * （Plan 212）和 dispatcher 侧 best-fit 派发（Plan 215）的成本/容量建模。
 *
 * <p>不可变值类型，所有运算返回新实例。
 *
 * <p>单位约定（见 {@code NopJobCoreConstants}）：
 * <ul>
 *   <li>{@code cpu}：毫核（millicore），1000 = 1 核</li>
 *   <li>{@code memory}：MB</li>
 * </ul>
 *
 * <p>缺失维度按 0 处理（向后兼容未配置的任务/worker）：cost=0 的任务 always fit，
 * capacity=MAX_VALUE 的 worker 退化为 count-based 行为。
 */
public final class ResourceVector {

    /**
     * 零资源：用于未声明 cost 的任务。与任何 capacity 相减不影响剩余，fits 任何 remaining。
     */
    public static final ResourceVector ZERO = new ResourceVector(0, 0);

    /**
     * 无限容量：用于未声明 capacity 的 worker。与任何 reserved 相减仍为无限，loadScore 投影为 0。
     */
    public static final ResourceVector MAX_VALUE =
            new ResourceVector(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int cpu;
    private final int memory;

    public ResourceVector(int cpu, int memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    /**
     * CPU 维（毫核）。
     */
    public int getCpu() {
        return cpu;
    }

    /**
     * 内存维（MB）。
     */
    public int getMemory() {
        return memory;
    }

    /**
     * 逐维相加，返回新实例。用于聚合多个 task 的 reserved 成本。
     */
    public ResourceVector add(ResourceVector other) {
        return new ResourceVector(this.cpu + other.cpu, this.memory + other.memory);
    }

    /**
     * 逐维相减，<b>允许负值结果（不 clamp）</b>。用于 capacity − reserved。
     * 负值由 {@link #isZeroOrNegative()} 判断是否耗尽。
     */
    public ResourceVector subtract(ResourceVector other) {
        return new ResourceVector(this.cpu - other.cpu, this.memory - other.memory);
    }

    /**
     * 逐维比较：当前向量的每一维都 {@code >=} other 对应维度时返回 true（fit check 硬约束）。
     * 任一维度不足即返回 false。用于 worker.available.fits(task.cost)。
     */
    public boolean fits(ResourceVector other) {
        return this.cpu >= other.cpu && this.memory >= other.memory;
    }

    /**
     * 任一维度 {@code <=} 0 即返回 true。用于判断 worker 剩余资源是否已耗尽——
     * 只要一个维度满，worker 就不该再拉取（防止 OOM 或 CPU 抢占）。
     */
    public boolean isZeroOrNegative() {
        return this.cpu <= 0 || this.memory <= 0;
    }

    /**
     * 投影为单个负载分数（软偏好，用于 best-fit 排名）：
     * {@code max(cpu/capacity.cpu, memory/capacity.memory)}。
     *
     * <p>capacity 某维为 {@link Integer#MAX_VALUE}（未声明）时，该维分数按 0 计，
     * 对应"无限容量最闲"的语义。{@code this} 表示 reserved（已占用），{@code capacity} 表示总容量。
     */
    public double loadScore(ResourceVector capacity) {
        double cpuScore = capacity.cpu == Integer.MAX_VALUE ? 0.0 : (double) this.cpu / capacity.cpu;
        double memScore = capacity.memory == Integer.MAX_VALUE ? 0.0 : (double) this.memory / capacity.memory;
        return Math.max(cpuScore, memScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceVector)) return false;
        ResourceVector that = (ResourceVector) o;
        return cpu == that.cpu && memory == that.memory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpu, memory);
    }

    @Override
    public String toString() {
        return "ResourceVector{cpu=" + cpu + ", memory=" + memory + '}';
    }
}
