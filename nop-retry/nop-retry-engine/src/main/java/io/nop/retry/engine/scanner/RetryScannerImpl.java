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

public class RetryScannerImpl implements IRetryScanner {

    static final Logger LOG = LoggerFactory.getLogger(RetryScannerImpl.class);

    private IRetryRecordStore recordStore;
    private final PartitionResolver partitionResolver = new PartitionResolver();
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long retryingTimeoutMs = NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS;

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
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.retry.scanner.batch-size|100")
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.retry.scanner.enable-cluster|false")
    public void setEnableCluster(boolean enableCluster) {
        this.partitionResolver.setEnableCluster(enableCluster);
    }

    @InjectValue("@cfg:nop.retry.scanner.retrying-timeout-ms|600000")
    public void setRetryingTimeoutMs(long retryingTimeoutMs) {
        this.retryingTimeoutMs = retryingTimeoutMs;
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
                        lastScanFailureTimeMillis = System.currentTimeMillis();
                        scanFailureCount.incrementAndGet();
                        LOG.error("nop.retry.scanner.round-failed:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        running = false;
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
        long begin = CoreMetrics.currentTimeMillis();
        try {
            do {
                if (!running) {
                    break;
                }

                IntRangeSet partitions = resolvePartitions();

                // 1. 获取待处理记录
                List<NopRetryRecord> records = recordStore.fetchPendingRecords(batchSize, partitions);
                LOG.debug("nop.retry.scanner.fetched-records:count={}", records.size());

                // fetch 为空，跳出循环
                if (records.isEmpty()) {
                    break;
                }

                // 2. 批量锁定
                List<NopRetryRecord> lockedRecords = recordStore.tryLockRecordsForProcess(records, retryingTimeoutMs);
                LOG.debug("nop.retry.scanner.locked-records:count={}", lockedRecords.size());

                // lock 为空（并发竞争），继续下一轮
                if (lockedRecords.isEmpty()) {
                    continue;
                }

                // 3. 处理成功锁定的记录
                try {
                    processor.accept(lockedRecords);
                } catch (Exception e) {
                    LOG.error("nop.retry.execute-retry-fail", e);
                }
            } while (running);
        } catch (Exception e) {
            // Exception 级失败仍由站点自身兜底（既有语义）；Error 级逃逸到平台绝缘设施边界，
            // 由 onRunFailure 观测计数并重臂（C8-001 原缺陷 = Error/被拒即近静默永久停摆）
            lastScanFailureTimeMillis = System.currentTimeMillis();
            scanFailureCount.incrementAndGet();
            LOG.error("nop.retry.scanner.scan-failed", e);
        } finally {
            // C8-001 存活指标：每轮扫描结束（含异常路径）都刷新存活时间戳
            lastScanDurationMillis = CoreMetrics.currentTimeMillis() - begin;
            lastScanTimeMillis = CoreMetrics.currentTimeMillis();
            scanCount.incrementAndGet();
        }
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
