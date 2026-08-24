package io.nop.job.core;

import static io.nop.job.core.JobCoreErrors.ARG_CONFIG_NAME;
import static io.nop.job.core.JobCoreErrors.ARG_CONFIG_VALUE;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVALID_CONFIG_VALUE;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.core.partition.JobPartitionResolver;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 批量扫描器的公共基类：封装定时调度生命周期 + 有界批循环骨架 + cursor 分页基础设施 + 通用配置 setter。
 * <p>
 * <b>子类职责</b>：实现 {@link #scanBatch()}（标注 {@code @SingleSession}），返回 {@code true} 表示还有更多待处理项需要继续循环，
 * 返回 {@code false} 表示已 drain 完毕。每轮 {@code scanBatch} 在独立的 ORM session 中执行。
 * <p>
 * <b>循环上限</b>：{@link #maxScanLoops}（默认 1000）防止 fire/task 产生速度 ≥ 消费速度时无限循环。
 * {@code scheduleWithFixedDelay} 保证上一轮结束后才开始下一轮，未处理完的项在下一轮紧接着处理。
 * <p>
 * <b>Per-cycle 状态管理</b>：子类的周期级 cursor 状态通过 {@link Cursor}（{@link #newCursor()} 注册）封装；
 * {@link #scanOnce()} 入口自动 reset 所有已注册 cursor + 调 {@link #onCycleStart()}（默认空实现，可 override 处理 cursor 之外的周期初始化）。
 * <p>
 * <b>通用配置</b>：基类吸收 5 个子类的 boilerplate——{@code partitionResolver}/{@code setAssignedPartitions}/
 * {@code resolvePartitions()} helper、{@code scanIntervalMs}/{@code batchSize}/{@code maxScanLoops} 校验（子类的
 * {@code @InjectValue} setter 一行委托给 {@code applyXxx} helper 即可）。
 * <p>
 * <b>Cursor drain 模板</b>：{@link #drainBatch(Cursor, BiFunction, Consumer, Function, Function)} 封装
 * "fetch 一批 → 空? drained : 处理 + cursor 推进 + (size<batchSize? drained)" 骨架，子类的 scanBatch 可声明式调用。
 */
public abstract class AbstractBatchScanner {
    static final Logger LOG = LoggerFactory.getLogger(AbstractBatchScanner.class);

    protected int scanIntervalMs = 5000;
    protected int batchSize = 100;
    protected int maxScanLoops = 1000;
    protected volatile boolean running;
    protected Future<?> scanFuture;

    private final List<Cursor> registeredCursors = new ArrayList<>();

    private JobPartitionResolver partitionResolver;

    public synchronized void startScanning() {
        if (running) {
            return;
        }
        running = true;
        scanFuture = getExecutor().scheduleWithFixedDelay(
                this::doScan, 0, scanIntervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stopScanning() {
        running = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
    }

    protected void doScan() {
        if (!running) {
            return;
        }
        scanOnce();
    }

    /**
     * 触发一次完整扫描周期。对同包测试与 lifecycle 调用方都公开——避免历史子类为"同包可见性提升"
     * 写空壳 override（5 个子类曾有 `@Override protected void scanOnce() { super.scanOnce(); }` 的设计 hack）。
     * <p>
     * 入口顺序：(1) reset 所有已注册 cursor；(2) 调 {@link #onCycleStart()}；(3) 进入批循环。
     */
    public void scanOnce() {
        for (Cursor c : registeredCursors) {
            c.reset();
        }
        onCycleStart();
        for (int i = 0; i < maxScanLoops; i++) {
            try {
                if (!scanBatch()) {
                    return;
                }
            } catch (Exception e) {
                onScanFailed(e);
                return;
            }
        }
    }

    /**
     * 处理一个批次。子类应标注 {@code @SingleSession}。
     *
     * @return {@code true} 表示可能还有更多待处理项，继续下一轮；{@code false} 表示已无待处理项。
     */
    protected abstract boolean scanBatch();

    /**
     * 每个调度周期入口回调（在基类自动 reset cursor 之后）。子类可 override 处理 cursor 之外的周期初始化。
     * 默认空实现是有意的 no-op default（overridable template method），不是缺失功能。
     */
    protected void onCycleStart() {
    }

    /**
     * 创建并注册一个 {@link Cursor}。已注册的 cursor 在每个 {@link #scanOnce()} 入口自动 reset。
     * 推荐用法：在子类中作为 final 字段初始化（如 {@code private final Cursor taskCursor = newCursor();}），
     * 注册时机由 Java 字段初始化顺序保证（基类 {@code registeredCursors} 在子类字段初始化之前已就绪）。
     */
    protected final Cursor newCursor() {
        Cursor c = new Cursor();
        registeredCursors.add(c);
        return c;
    }

    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.scan-failed:scanner={}", getClass().getSimpleName(), e);
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }

    // ========== Plan 338 ad-hoc 重构：通用配置 setter / partition helper ==========

    /**
     * 校验并赋值 {@code scanIntervalMs}。子类的 {@code @InjectValue} setter 一行委托：
     * {@code @InjectValue("@cfg:nop.job.X.scan-interval-ms|5000") public void setScanIntervalMs(int v) { applyScanIntervalMs(v); }}。
     */
    protected final void applyScanIntervalMs(int scanIntervalMs) {
        if (scanIntervalMs < 1000) {
            throw new NopException(ERR_JOB_INVALID_CONFIG_VALUE)
                    .param(ARG_CONFIG_NAME, "scanIntervalMs").param(ARG_CONFIG_VALUE, scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    /**
     * 校验并赋值 {@code batchSize}。子类的 {@code @InjectValue} setter 一行委托。
     */
    protected final void applyBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new NopException(ERR_JOB_INVALID_CONFIG_VALUE)
                    .param(ARG_CONFIG_NAME, "batchSize").param(ARG_CONFIG_VALUE, batchSize);
        }
        this.batchSize = batchSize;
    }

    /**
     * 校验并赋值 {@code maxScanLoops}。子类的 {@code @InjectValue} setter 一行委托。
     */
    protected final void applyMaxScanLoops(int maxScanLoops) {
        if (maxScanLoops < 1) {
            throw new NopException(ERR_JOB_INVALID_CONFIG_VALUE)
                    .param(ARG_CONFIG_NAME, "maxScanLoops").param(ARG_CONFIG_VALUE, maxScanLoops);
        }
        this.maxScanLoops = maxScanLoops;
    }

    /**
     * 注入 {@link JobPartitionResolver}。基类吸收此 setter，4 个 coordinator 子类无需重复。
     * Worker 不使用 partitionResolver（保留自己的 {@code IntRangeSet} 字段方案），不调此 setter。
     */
    @Inject
    public void setPartitionResolver(JobPartitionResolver partitionResolver) {
        this.partitionResolver = partitionResolver;
    }

    /**
     * 解析分配的分区字符串（来自配置 {@code nop.job.coordinator.assigned-partitions}）。基类吸收此 setter，
     * 4 个 coordinator 子类无需重复。Worker 重写此方法以解析到自己的 {@code IntRangeSet} 字段。
     */
    @InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        if (partitionResolver == null) {
            partitionResolver = new JobPartitionResolver();
        }
        partitionResolver.setAssignedPartitions(partitions);
    }

    /**
     * 返回本节点负责的分区集合。4 个 coordinator 子类的 scanBatch 通过此方法获取 partitions，
     * 替代历史 inline 的 {@code partitionResolver != null ? partitionResolver.resolvePartitions() : null}。
     */
    protected IntRangeSet resolvePartitions() {
        return partitionResolver != null ? partitionResolver.resolvePartitions() : null;
    }

    // ========== Plan 338 Phase B：drainBatch 模板方法 ==========

    /**
     * Drain 一批记录的通用模板：fetch → 空? markDrained : process + cursor 推进 + (size<batchSize? markDrained)。
     * 调用方需提供 cursor、fetcher（基于 cursor 返回 List）、processor、从 record 提取 sortKey/id 的两个函数。
     * <p>
     * 典型用法：
     * <pre>{@code
     * drainBatch(taskCursor,
     *         (t, i) -> taskStore.fetchRunningTasks(batchSize, partitions, t, i),
     *         this::scanTaskTimeouts,
     *         NopJobTask::getStartTime, NopJobTask::getJobTaskId);
     * }</pre>
     *
     * @param cursor    子扫描的 cursor（已通过 {@link #newCursor()} 注册）
     * @param fetcher   接收 (cursorTime, cursorId)，返回一批记录
     * @param processor 处理整批记录（per-record 错误隔离由 processor 内部负责）
     * @param timeFn    从记录提取 cursor 的 sortKey 分量
     * @param idFn      从记录提取 cursor 的 id 分量
     */
    protected final <T> void drainBatch(
            Cursor cursor,
            BiFunction<Timestamp, String, List<T>> fetcher,
            Consumer<List<T>> processor,
            Function<T, Timestamp> timeFn,
            Function<T, String> idFn) {
        if (cursor.isDrained()) {
            return;
        }
        List<T> batch = fetcher.apply(cursor.time(), cursor.id());
        if (batch.isEmpty()) {
            cursor.markDrained();
            return;
        }
        processor.accept(batch);
        T last = batch.get(batch.size() - 1);
        Timestamp lastTime = timeFn.apply(last);
        if (lastTime == null) {
            // check2 [P3-8]: 满批末条的排序键为 null（如 DISPATCHING fire 的 startTime 为 null 的
            // 异常数据残留）时，推进 cursor 为 (null, id) 会使下一轮 fetch 抛
            // IllegalArgumentException（cursorId requires cursorTime）、整轮扫描中止。
            // 防御：不推进，直接 markDrained 结束本子扫描周期，warn 留痕。
            LOG.warn("nop.job.scan-null-sort-key:scanner={},lastId={},batchSize={} — cursor not advanced, sub-scan drained",
                    getClass().getSimpleName(), idFn.apply(last), batch.size());
            cursor.markDrained();
            return;
        }
        cursor.advance(lastTime, idFn.apply(last));
        if (batch.size() < batchSize) {
            cursor.markDrained();
        }
    }

    /**
     * 游标分页上下文。封装 cursor 推进状态（time + id）和 drained 标志。基类 {@link #scanOnce()} 在每个
     * 调度周期入口自动调 {@link #reset()}；子类通过 {@link #newCursor()} 创建并持有，在 {@link #scanBatch()}
     * 中调 {@link #time()}/{@link #id()} 读 cursor、{@link #advance(Timestamp, String)} 推进、
     * {@link #markDrained()} 标记 drained、{@link #isDrained()} 检测。
     * <p>
     * 不可变性：状态可变（cursor 推进就是 mutation）；非线程安全（依赖 {@code scheduleWithFixedDelay}
     * 单线程调度语义）。
     */
    public static final class Cursor {
        private Timestamp time;
        private String id;
        private boolean drained;

        public Timestamp time() {
            return time;
        }

        public String id() {
            return id;
        }

        public boolean isDrained() {
            return drained;
        }

        public void advance(Timestamp time, String id) {
            this.time = time;
            this.id = id;
        }

        public void markDrained() {
            this.drained = true;
        }

        void reset() {
            this.time = null;
            this.id = null;
            this.drained = false;
        }
    }
}
