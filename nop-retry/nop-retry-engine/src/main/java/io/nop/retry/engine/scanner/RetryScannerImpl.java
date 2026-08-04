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
import io.nop.cluster.naming.INamingService;
import io.nop.cluster.naming.PartitionResolver;
import io.nop.commons.concurrent.executor.GlobalExecutors;
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
        scanFuture = getExecutor().scheduleWithFixedDelay(
                () -> doScan(processor),
                0,
                scanIntervalMs,
                TimeUnit.MILLISECONDS
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

    @SingleSession
    protected void doScan(Consumer<List<NopRetryRecord>> processor) {
        if (!running) {
            return;
        }

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
            LOG.error("nop.retry.scanner.scan-failed", e);
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
