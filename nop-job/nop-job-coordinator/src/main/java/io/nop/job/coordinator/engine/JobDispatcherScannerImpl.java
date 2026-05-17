package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JobDispatcherScannerImpl implements IJobDispatcherScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobDispatcherScannerImpl.class);
    static final String TASK_BUILDER_PREFIX = "nopJobTaskBuilder_";

    private IJobFireStore fireStore;
    private IJobTaskBuilder defaultTaskBuilder;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long lockTimeoutMs = 60000;
    private IntRangeSet assignedPartitions;
    private volatile boolean running;
    private Future<?> scanFuture;

    @Inject
    public void setFireStore(IJobFireStore fireStore) {
        this.fireStore = fireStore;
    }

    @Inject
    public void setDefaultTaskBuilder(IJobTaskBuilder defaultTaskBuilder) {
        this.defaultTaskBuilder = defaultTaskBuilder;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.batch-size|100")
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.dispatcher.lock-timeout-ms|60000")
    public void setLockTimeoutMs(long lockTimeoutMs) {
        this.lockTimeoutMs = lockTimeoutMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        if (partitions != null && !partitions.isEmpty()) {
            this.assignedPartitions = IntRangeSet.parse(partitions);
        }
    }

    @Override
    public synchronized void startScanning() {
        if (running) {
            return;
        }
        running = true;
        scanFuture = getExecutor().scheduleWithFixedDelay(this::doScan, 0, scanIntervalMs, TimeUnit.MILLISECONDS);
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
    protected void doScan() {
        if (!running) {
            return;
        }

        scanOnce();
    }

    void scanOnce() {
        try {
            var fires = fireStore.fetchWaitingFires(batchSize, assignedPartitions);
            if (fires.isEmpty()) {
                return;
            }

            var locked = fireStore.tryLockFiresForDispatch(fires, AppConfig.hostId(), lockTimeoutMs);
            for (NopJobFire fire : locked) {
                IJobTaskBuilder builder = resolveTaskBuilder(fire);
                List<NopJobTask> tasks = builder.buildTasks(fire);
                fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
            }
        } catch (Exception e) {
            LOG.error("nop.job.dispatcher.scan-failed", e);
        }
    }

    private IJobTaskBuilder resolveTaskBuilder(NopJobFire fire) {
        String executorKind = fire.getExecutorKind();
        if (executorKind != null && !executorKind.isBlank()) {
            String beanName = TASK_BUILDER_PREFIX + executorKind;
            Object bean = BeanContainer.tryGetBean(beanName);
            if (bean instanceof IJobTaskBuilder) {
                return (IJobTaskBuilder) bean;
            }
        }
        return defaultTaskBuilder;
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
