/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.scanner;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.cluster.naming.INamingService;
import io.nop.cluster.naming.PartitionResolver;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.NopRetryConstants;
import io.nop.retry.engine.store.IRetryRecordStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static io.nop.retry.engine.NopRetryErrors.ARG_CONFIG_KEY;
import static io.nop.retry.engine.NopRetryErrors.ARG_CONFIG_VALUE;
import static io.nop.retry.engine.NopRetryErrors.ERR_RETRY_SCANNER_CONFIG_INVALID;

public class RetryScannerImpl implements IRetryScanner {

    static final Logger LOG = LoggerFactory.getLogger(RetryScannerImpl.class);

    private IRetryRecordStore recordStore;
    private final PartitionResolver partitionResolver = new PartitionResolver();
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long retryingTimeoutMs = NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS;

    // 1.6-C8-013（plan 2543 W-14）：参数合法域（fail-fast 边界）
    static final int MIN_SCAN_INTERVAL_MS = 100;
    static final int MIN_BATCH_SIZE = 1;
    static final int MAX_BATCH_SIZE = 1000;

    // 1.6-C7-015（plan 2543 W-12）：单轮上界（批次数 + 时长）
    private int maxBatchesPerRound = 10;
    private long maxRoundDurationMs = 60_000L;
    private volatile RoundEndReason lastRoundEndReason;

    private volatile boolean running;
    private Future<?> scanFuture;

    // ========== scanner 存活指标（plan 2496 1.6-C8-001 模块侧义务：存活/最后扫描时间可观测）==========
    private final AtomicLong scanCount = new AtomicLong();
    private final AtomicLong scanFailureCount = new AtomicLong();
    private volatile long lastScanTimeMillis;
    private volatile long lastScanDurationMillis;
    private volatile long lastScanFailureTimeMillis;

    @Inject
    public void setRecordStore(IRetryRecordStore recordStore) {
        this.recordStore = recordStore;
    }

    public void setNamingService(INamingService namingService) {
        this.partitionResolver.setNamingService(namingService);
    }

    @InjectValue("@cfg:nop.retry.scanner.service-name|")
    public void setServiceName(String serviceName) {
        this.partitionResolver.setServiceName(serviceName);
    }

    @InjectValue("@cfg:nop.retry.scanner.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        // 1.6-C8-013（plan 2543 W-14）：负值/0 间隔使调度链退化为零间隔紧循环（上一轮命令返回
        // 后立即重扫），DB 按 fetch+lock 满频敲击。装配期 fail-fast，不静默回落缺省。
        if (scanIntervalMs < MIN_SCAN_INTERVAL_MS) {
            throw new NopException(ERR_RETRY_SCANNER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.scanner.scan-interval-ms")
                    .param(ARG_CONFIG_VALUE, scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.retry.scanner.batch-size|100")
    public void setBatchSize(int batchSize) {
        // 1.6-C8-013（plan 2543 W-14）：0 值使每轮 fetch 返回空 → 扫描器「运行中」但永久不处理
        // 任何记录（与「无积压」完全不可区分）；负值透传 SQL 层致每轮报错；巨值单批物化无上界。
        if (batchSize < MIN_BATCH_SIZE || batchSize > MAX_BATCH_SIZE) {
            throw new NopException(ERR_RETRY_SCANNER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.scanner.batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.retry.scanner.enable-cluster|false")
    public void setEnableCluster(boolean enableCluster) {
        this.partitionResolver.setEnableCluster(enableCluster);
    }

    @InjectValue("@cfg:nop.retry.scanner.retrying-timeout-ms|600000")
    public void setRetryingTimeoutMs(long retryingTimeoutMs) {
        // 1.6-C8-013（plan 2543 W-14）：非正租约使 tryLock 写出的锁过期时间落在过去，
        // 记录被立即重拾（租约语义完全失效）。装配期 fail-fast。
        if (retryingTimeoutMs <= 0) {
            throw new NopException(ERR_RETRY_SCANNER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.scanner.retrying-timeout-ms")
                    .param(ARG_CONFIG_VALUE, retryingTimeoutMs);
        }
        this.retryingTimeoutMs = retryingTimeoutMs;
    }

    // ========== 1.6-C7-015（plan 2543 W-12）：单轮上界（批次数 + 时长，先到先断）==========

    @InjectValue("@cfg:nop.retry.scanner.max-batches-per-round|10")
    public void setMaxBatchesPerRound(int maxBatchesPerRound) {
        // 0 或负值 = 单轮零处理（扫描器运行中但永不推进）；显式下限 1。
        if (maxBatchesPerRound < 1) {
            throw new NopException(ERR_RETRY_SCANNER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.scanner.max-batches-per-round")
                    .param(ARG_CONFIG_VALUE, maxBatchesPerRound);
        }
        this.maxBatchesPerRound = maxBatchesPerRound;
    }

    @InjectValue("@cfg:nop.retry.scanner.max-round-duration-ms|60000")
    public void setMaxRoundDurationMs(long maxRoundDurationMs) {
        // 0 或负值 = 单轮时长上界失效（退化为无上界排水）；显式下限 1ms。
        if (maxRoundDurationMs < 1) {
            throw new NopException(ERR_RETRY_SCANNER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.scanner.max-round-duration-ms")
                    .param(ARG_CONFIG_VALUE, maxRoundDurationMs);
        }
        this.maxRoundDurationMs = maxRoundDurationMs;
    }

    public int getMaxBatchesPerRound() {
        return maxBatchesPerRound;
    }

    public long getMaxRoundDurationMs() {
        return maxRoundDurationMs;
    }

    /** 单轮上界收束原因（观测口径，测试与指标共用）。 */
    public enum RoundEndReason {
        BACKLOG_DRAINED, MAX_BATCHES, MAX_DURATION, STOPPED, FETCH_ERROR
    }

    /**
     * 单轮上界测试 seam（plan 2543 W-12）：单测子类覆写即可确定性驱动时长上界，
     * 不被真实墙钟影响（draft review W-12 的可确定性要求）。
     */
    protected long currentTimeMillis() {
        return CoreMetrics.currentTimeMillis();
    }

    @InjectValue("@cfg:nop.retry.scanner.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        this.partitionResolver.setAssignedPartitions(partitions);
    }

    public int getScanIntervalMs() {
        return scanIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    @Override
    public synchronized void startScanning(Consumer<List<NopRetryRecord>> processor) {
        if (running) {
            return;
        }

        running = true;
        // C8-001（plan 2496）：接入平台周期任务绝缘层（带业务标识 + 观测钩子）。任务体任何 Throwable
        // （含 Error）由设施捕获观测并重臂——原缺陷「被拒/Error 即（近）静默永久停摆、nop_retry_record
        // 表积压无界」由此闭合；设施级拒绝（提交失败，链真死且不复活）时 running 状态真实化为 false。
        scanFuture = getExecutor().scheduleWithFixedDelay(
                () -> doScan(processor),
                0,
                scanIntervalMs,
                TimeUnit.MILLISECONDS,
                "nop-retry-scanner",
                new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        // R4-32（1.6-C7-011 [R2-M2 裁定]）：对外可观测诊断时间戳（getter 展示 +
                        // 测试 >0 断言），保留墙钟 epoch 语义，不入单调换源清单
                        lastScanFailureTimeMillis = System.currentTimeMillis();
                        scanFailureCount.incrementAndGet();
                        LOG.error("nop.retry.scanner.round-failed:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        running = false;
                        // R4-32（1.6-C7-011 [R2-M2 裁定]）：对外可观测诊断时间戳（getter 展示 +
                        // 测试 >0 断言），保留墙钟 epoch 语义，不入单调换源清单
                        lastScanFailureTimeMillis = System.currentTimeMillis();
                        scanFailureCount.incrementAndGet();
                        LOG.error("nop.retry.scanner.chain-rejected: scan chain terminated by executor rejection,"
                                + " will not resurrect", e);
                    }
                }
        );
    }

    @Override
    public synchronized void stopScanning() {
        running = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
            scanFuture = null;
        }
    }

    // ========== scanner 存活指标读取口 ==========

    /**
     * 扫描链是否在运行。设施级拒绝（链真死且不复活）后为 false——状态真实化（C8-001 子面：
     * 原缺陷 running 仍 true 状态说谎）。
     */
    public boolean isRunning() {
        return running;
    }

    /** 已完成的扫描轮数（每轮 {@link #doScan} 计一次）。 */
    public long getScanCount() {
        return scanCount.get();
    }

    /** 扫描失败轮次数（任务体 Throwable / 设施级拒绝）。 */
    public long getScanFailureCount() {
        return scanFailureCount.get();
    }

    /** 最近一轮扫描完成时间（毫秒；0 = 从未扫描）——存活/最后扫描时间指标。 */
    public long getLastScanTimeMillis() {
        return lastScanTimeMillis;
    }

    /** 最近一轮扫描耗时（毫秒）。 */
    public long getLastScanDurationMillis() {
        return lastScanDurationMillis;
    }

    /** 最近一次扫描失败时间（毫秒；0 = 从未失败）。 */
    public long getLastScanFailureTimeMillis() {
        return lastScanFailureTimeMillis;
    }

    @SingleSession
    protected void doScan(Consumer<List<NopRetryRecord>> processor) {
        if (!running) {
            return;
        }
        // 单轮计时经 currentTimeMillis() seam（测试子类可确定性推进）
        long begin = currentTimeMillis();
        int batches = 0;
        RoundEndReason endReason = RoundEndReason.STOPPED;
        try {
            do {
                if (!running) {
                    endReason = RoundEndReason.STOPPED;
                    break;
                }

                // 1.6-C7-015（plan 2543 W-12）：单轮时长上界——每批开始前检查。
                // 排水式 do-while 原先单轮时长 = 批次数 × 最慢批耗时，下游批量超时时可达小时级；
                // 上界只控本轮起点，不打断在途批（保 2515 裁定⑥ 的 future 终态完备前提）。
                if (currentTimeMillis() - begin >= maxRoundDurationMs) {
                    endReason = RoundEndReason.MAX_DURATION;
                    LOG.info("nop.retry.scanner.round-max-duration:batches={},elapsedMs={},maxDurationMs={}",
                            batches, currentTimeMillis() - begin, maxRoundDurationMs);
                    break;
                }

                IntRangeSet partitions = resolvePartitions();

                // 1. 获取待处理记录
                List<NopRetryRecord> records = recordStore.fetchPendingRecords(batchSize, partitions);
                LOG.debug("nop.retry.scanner.fetched-records:count={}", records.size());

                // fetch 为空 —— 积压已排空，本轮正常结束
                if (records.isEmpty()) {
                    endReason = RoundEndReason.BACKLOG_DRAINED;
                    break;
                }

                // 2. 批量锁定
                List<NopRetryRecord> lockedRecords = recordStore.tryLockRecordsForProcess(records, retryingTimeoutMs);
                LOG.debug("nop.retry.scanner.locked-records:count={}", lockedRecords.size());

                // lock 为空（并发竞争），继续下一轮——不消耗批次预算（W-12 口径：只计非空批），
                // 否则锁竞争的 store 会静默烧完预算而不处理任何记录
                if (lockedRecords.isEmpty()) {
                    continue;
                }

                // 3. 处理成功锁定的记录
                try {
                    processor.accept(lockedRecords);
                } catch (Exception e) {
                    LOG.error("nop.retry.execute-retry-fail", e);
                }

                // 1.6-C7-015（plan 2543 W-12）：非空批计数上界——达上限即结束本轮，
                // 余量交下一 fixedDelay 轮（上一轮的「排水整个积压」不设上界已消除）
                if (++batches >= maxBatchesPerRound) {
                    endReason = RoundEndReason.MAX_BATCHES;
                    LOG.info("nop.retry.scanner.round-max-batches:batches={},maxBatches={}",
                            batches, maxBatchesPerRound);
                    break;
                }
            } while (running);
        } catch (Exception e) {
            // Exception 级失败仍由站点自身兜底（既有语义）；Error 级逃逸到平台绝缘设施边界，
            // 由 onRunFailure 观测计数并重臂（C8-001 原缺陷 = Error/被拒即近静默永久停摆）
            endReason = RoundEndReason.FETCH_ERROR;
            // R4-32（1.6-C7-011 [R2-M2 裁定]）：对外诊断时间戳，保留墙钟（同上）
            lastScanFailureTimeMillis = System.currentTimeMillis();
            scanFailureCount.incrementAndGet();
            LOG.error("nop.retry.scanner.scan-failed", e);
        } finally {
            // C8-001 存活指标：每轮扫描结束（含异常路径）都刷新存活时间戳
            lastScanDurationMillis = currentTimeMillis() - begin;
            lastScanTimeMillis = CoreMetrics.currentTimeMillis();
            scanCount.incrementAndGet();
            lastRoundEndReason = endReason;
        }
    }

    /** 1.6-C7-015（plan 2543 W-12）：最近一轮的结束原因（排空/批数/时长/停机/取数失败）。 */
    public RoundEndReason getLastRoundEndReason() {
        return lastRoundEndReason;
    }

    /**
     * 解析分区范围：优先使用配置的 assignedPartitions，否则从 NamingService 动态获取。
     * 委托给通用的 {@link PartitionResolver}，避免与 nop-job 等模块重复实现。
     */
    private IntRangeSet resolvePartitions() {
        return partitionResolver.resolvePartitions();
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
