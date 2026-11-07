package io.nop.job.core;

import static io.nop.job.core.JobCoreErrors.ARG_CONFIG_NAME;
import static io.nop.job.core.JobCoreErrors.ARG_CONFIG_VALUE;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVALID_CONFIG_VALUE;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.core.partition.JobPartitionResolver;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    // CRA 8.47 R4-07 W-07 (1.4-C7-007, adjudication F): observe-only stall watchdog.
    // Default 0=disabled (2496 invoke-timeout-ms|0 precedent); >0 enables; <0 fail-fast.
    // NEVER cancels/interrupts the main chain (observe-only — 2496 MA-06 discipline).
    // F-m4 conflation guard: scanCycleCount advancing = chain alive (not stalled).
    private int stallWatchdogMs;
    private final java.util.concurrent.atomic.AtomicLong stallDetectedCount =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile long lastObservedScanCycleCount;
    private volatile long oldestInFlightAgeMillis;
    private volatile java.util.concurrent.Future<?> stallWatchdogFuture;
    protected volatile boolean running;
    protected Future<?> scanFuture;

    private final List<Cursor> registeredCursors = new ArrayList<>();

    private JobPartitionResolver partitionResolver;

    // CRA 8.46 R4-06 W-05 (1.4-C1-003): in-flight guard for the PUBLIC scanOnce
    // entry — "single-threaded" was only a scheduling declaration, an external
    // caller racing a timer tick used to reset the in-flight cursors (duplicate
    // processing / torn cursor). AtomicBoolean + mirror of the 2496 counter family.
    private final AtomicBoolean scanningInFlight = new AtomicBoolean(false);
    private final AtomicLong scanCycleConflictCount = new AtomicLong();

    // ========== scanner 存活指标（plan 2496 1.4-C4-003 #12 模块侧义务）==========
    private final AtomicLong scanCycleCount = new AtomicLong();
    private final AtomicLong scanFailureCount = new AtomicLong();
    private final AtomicLong scanChainRejectedCount = new AtomicLong();
    private volatile long lastScanStartTimeMillis;
    private volatile long lastScanEndTimeMillis;
    private volatile long lastScanFailureTimeMillis;

    public synchronized void startScanning() {
        if (running) {
            return;
        }
        running = true;
        String scannerName = getClass().getSimpleName();
        // 平台绝缘入口（带业务标识）：任务体 Throwable 由设施捕获观测并重臂，链存活至显式
        // stopScanning 或设施级拒绝（设计契约 ai-dev/design/concurrency/periodic-task-insulation.md）
        scanFuture = getExecutor().scheduleWithFixedDelay(
                this::doScan, 0, scanIntervalMs, TimeUnit.MILLISECONDS, scannerName, null);
        // CRA 8.47 R4-07 W-07: stall watchdog registration (same executor — C-type
        // carrier, millisecond memory check per 2497 §七 discipline). observe-only.
        if (stallWatchdogMs > 0) {
            this.stallWatchdogFuture = getExecutor().scheduleWithFixedDelay(
                    () -> checkStall(), stallWatchdogMs, stallWatchdogMs,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        // C4-003：返回 future 不再丢弃（默认 whenComplete 挂接，不改对外 API）。绝缘契约下异常终态是
        // 失败/取消/拒绝信号而非链死亡指标；仅设施级拒绝（链真死且不复活）时把 running 状态真实化为 false
        if (scanFuture instanceof CompletableFuture<?>) {
            ((CompletableFuture<?>) scanFuture).whenComplete((result, error) -> {
                if (error != null && !(error instanceof CancellationException)) {
                    scanChainRejectedCount.incrementAndGet();
                    running = false;
                    LOG.error("nop.job.scan-chain-rejected:scanner={}", scannerName, error);
                }
            });
        }
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
     * <p>
     * 整个周期体（含 try-外段 cursor.reset/onCycleStart）在站点内统一守卫：任何 Throwable（含 Error）
     * 都在本方法边界被捕获、计数、留痕，不再逃逸到周期设施边界——失败轮次可观测，{@link #running}
     * 与链状态不说谎（链由绝缘设施保持存活）。
     */
    /** CRA 8.47 R4-07 W-07: observe-only stall check (volatile reads, zero IO). */
    private void checkStall() {
        if (!running || stallWatchdogMs <= 0) {
            return;
        }
        long start = lastScanStartTimeMillis;
        if (start <= 0) {
            return;
        }
        long now = CoreMetrics.currentTimeMillis();
        long inFlightAge = now - start;
        // F-m4 conflation guard: scanCycleCount advancing = chain making progress
        long cycleCount = scanCycleCount.get();
        boolean cycleAdvanced = cycleCount != lastObservedScanCycleCount;
        lastObservedScanCycleCount = cycleCount;
        if (cycleAdvanced) {
            oldestInFlightAgeMillis = 0;
            return;
        }
        boolean inFlight = lastScanEndTimeMillis < start;
        if (inFlight && inFlightAge > stallWatchdogMs) {
            oldestInFlightAgeMillis = inFlightAge;
            stallDetectedCount.incrementAndGet();
            LOG.warn("nop.job.scan-stall-detected:scanner={},inFlightAgeMs={},thresholdMs={}",
                    getClass().getSimpleName(), inFlightAge, stallWatchdogMs);
        }
    }

    public void scanOnce() {
        // CRA 8.46 R4-06 W-05 (1.4-C1-003): in-flight CAS guard. The loser is
        // REJECTED, not failed: WARN + conflict counter + return WITHOUT touching
        // any cursor/chain state (the rejected entrant runs no cursor reset — the
        // in-flight cycle keeps its cursors intact and the next fixed-delay tick
        // covers its window). The rejection deliberately stays OUT of the 2496
        // insulation counters below (those count REAL cycles only) and out of the
        // insulation-failure stream — a typed rejection here would escape through
        // the executor facility and be miscounted as a task failure, polluting the
        // chain-health observation semantics. The 2496 insulation boundary
        // (Throwable catch / onScanCycleFailed / counters) is untouched — the guard
        // wraps the SAME body inside the insulation boundary.
        if (!scanningInFlight.compareAndSet(false, true)) {
            scanCycleConflictCount.incrementAndGet();
            LOG.warn("nop.job.scan-cycle-conflict:scanner={}", getClass().getSimpleName());
            return;
        }
        try {
            long begin = CoreMetrics.currentTimeMillis();
            scanCycleCount.incrementAndGet();
            lastScanStartTimeMillis = begin;
            try {
                for (Cursor c : registeredCursors) {
                    c.reset();
                }
                onCycleStart();
                scanBatches();
                lastScanEndTimeMillis = CoreMetrics.currentTimeMillis();
            } catch (Throwable e) {
                lastScanFailureTimeMillis = CoreMetrics.currentTimeMillis();
                scanFailureCount.incrementAndGet();
                onScanCycleFailed(e);
            }
        } finally {
            scanningInFlight.set(false);
        }
    }

    /** CRA 8.46 R4-06 W-05: concurrent-entry rejections observed on this scanner. */
    public long getScanCycleConflictCount() {
        return scanCycleConflictCount.get();
    }

    private void scanBatches() {
        for (int i = 0; i < maxScanLoops; i++) {
            try {
                if (!scanBatch()) {
                    return;
                }
            } catch (Exception e) {
                lastScanFailureTimeMillis = CoreMetrics.currentTimeMillis();
                scanFailureCount.incrementAndGet();
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

    /**
     * 扫描周期体的 Error 级（Throwable 非 Exception）失败回调。默认实现记录带 scanner 名的 ERROR 日志；
     * 子类可 override 扩展上报。该回调在 {@link #scanOnce()} 的站点守卫内触发，异常不逃逸、链不死亡。
     */
    protected void onScanCycleFailed(Throwable e) {
        LOG.error("nop.job.scan-cycle-failed:scanner={}", getClass().getSimpleName(), e);
    }

    // ========== scanner 存活指标（C4-003 模块侧义务：存活/最后扫描时间可观测）==========

    /** 已完成的扫描周期数（每轮 {@link #scanOnce()} 计一次）。 */
    public long getScanCycleCount() {
        return scanCycleCount.get();
    }

    /** 扫描失败轮次数（含 Exception 级与 Error 级周期失败）。 */
    public long getScanFailureCount() {
        return scanFailureCount.get();
    }

    /** 周期链遭设施级拒绝（真死、不复活）的次数。拒绝后 {@link #running} 已真实化为 false。 */
    public long getScanChainRejectedCount() {
        return scanChainRejectedCount.get();
    }

    /** 最近一轮扫描的开始时间（毫秒；0 = 从未扫描）。 */
    public long getLastScanStartTimeMillis() {
        return lastScanStartTimeMillis;
    }

    /** 最近一轮成功走完的扫描结束时间（毫秒；0 = 从未完整走完一轮）。 */
    public long getLastScanEndTimeMillis() {
        return lastScanEndTimeMillis;
    }

    /** 最近一次扫描失败的时间（毫秒；0 = 从未失败）。 */
    public long getLastScanFailureTimeMillis() {
        return lastScanFailureTimeMillis;
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
     * CRA 8.47 R4-07 W-07: stall watchdog threshold. {@code 0}=disabled (default,
     * observe-only precedent); {@code <0}=config error (fail-fast — protective face
     * silently disabled is a pseudo-guard, same convention as applyMaxScanLoops).
     * Subclass {@code @InjectValue} setter delegates here (apply* delegation pattern).
     */
    protected final void applyStallWatchdogMs(int ms) {
        if (ms < 0) {
            throw new NopException(ERR_JOB_INVALID_CONFIG_VALUE)
                    .param(ARG_CONFIG_NAME, "stallWatchdogMs").param(ARG_CONFIG_VALUE, ms);
        }
        this.stallWatchdogMs = ms;
    }

    /** CRA 8.47 R4-07 W-07: stall detections observed on this scanner (observe-only face). */
    public long getStallDetectedCount() {
        return stallDetectedCount.get();
    }

    /** CRA 8.47 R4-07 W-07: age of the oldest in-flight scan in milliseconds (gauge face). */
    public long getOldestInFlightAgeMillis() {
        return oldestInFlightAgeMillis;
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
