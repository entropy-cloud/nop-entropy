package io.nop.job.core;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 批量扫描器的公共基类：封装定时调度生命周期 + 有界批循环骨架。
 * <p>
 * 子类只需实现 {@link #scanBatch()}（标注 {@code @SingleSession}），返回 {@code true} 表示还有更多待处理项需要继续循环，
 * 返回 {@code false} 表示已 drain 完毕。每轮 {@code scanBatch} 在独立的 ORM session 中执行。
 * <p>
 * 循环上限由 {@link #maxScanLoops} 控制（默认 100），防止 fire/task 产生速度 ≥ 消费速度时无限循环。
 * {@code scheduleWithFixedDelay} 保证上一轮结束后才开始下一轮，未处理完的项在下一轮紧接着处理。
 */
public abstract class AbstractBatchScanner {
    static final Logger LOG = LoggerFactory.getLogger(AbstractBatchScanner.class);

    protected int scanIntervalMs = 5000;
    protected int batchSize = 100;
    protected int maxScanLoops = 1000;
    protected volatile boolean running;
    protected Future<?> scanFuture;

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

    protected void scanOnce() {
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

    protected void onScanFailed(Exception e) {
        LOG.error("nop.job.scan-failed:scanner={}", getClass().getSimpleName(), e);
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
