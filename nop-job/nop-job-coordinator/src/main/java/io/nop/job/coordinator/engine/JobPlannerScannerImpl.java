package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.trigger.JobTriggerCalculator;
import io.nop.job.coordinator.metrics.EmptyJobPlannerMetrics;
import io.nop.job.dao.helper.TriggerSpecHelper;
import io.nop.job.coordinator.metrics.IJobPlannerMetrics;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JobPlannerScannerImpl implements IJobPlannerScanner {
    static final Logger LOG = LoggerFactory.getLogger(JobPlannerScannerImpl.class);

    private IJobScheduleStore scheduleStore;
    private IJobPlannerMetrics plannerMetrics = new EmptyJobPlannerMetrics();
    private JobPartitionResolver partitionResolver;
    private int scanIntervalMs = 5000;
    private int batchSize = 100;
    private long planningTimeoutMs = 60000;
    private volatile boolean running;
    private Future<?> scanFuture;

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    public void setPlannerMetrics(IJobPlannerMetrics plannerMetrics) {
        this.plannerMetrics = plannerMetrics;
    }

    @Inject
    public void setPartitionResolver(JobPartitionResolver partitionResolver) {
        this.partitionResolver = partitionResolver;
    }

    @InjectValue("@cfg:nop.job.coordinator.planner.scan-interval-ms|5000")
    public void setScanIntervalMs(int scanIntervalMs) {
        if (scanIntervalMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.planner.scan-interval-ms must be >= 1000, got " + scanIntervalMs);
        }
        this.scanIntervalMs = scanIntervalMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.planner.batch-size|100")
    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "nop.job.planner.batch-size must be >= 1, got " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @InjectValue("@cfg:nop.job.coordinator.planner.lock-timeout-ms|60000")
    public void setPlanningTimeoutMs(long planningTimeoutMs) {
        if (planningTimeoutMs < 1000) {
            throw new IllegalArgumentException(
                    "nop.job.planner.lock-timeout-ms must be >= 1000, got " + planningTimeoutMs);
        }
        this.planningTimeoutMs = planningTimeoutMs;
    }

    @InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")
    public void setAssignedPartitions(String partitions) {
        if (partitionResolver == null) {
            partitionResolver = new JobPartitionResolver();
        }
        partitionResolver.setAssignedPartitions(partitions);
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
            IntRangeSet partitions = partitionResolver != null ? partitionResolver.resolvePartitions() : null;
            List<NopJobSchedule> schedules = scheduleStore.fetchDueSchedules(batchSize, partitions);
            if (schedules.isEmpty()) {
                return;
            }

            Map<String, Timestamp> dueFireTimes = new HashMap<>(schedules.size());
            for (NopJobSchedule schedule : schedules) {
                dueFireTimes.put(schedule.getJobScheduleId(), schedule.getNextFireTime());
            }

            List<NopJobSchedule> locked = scheduleStore.tryLockSchedulesForPlan(
                    schedules,
                    AppConfig.hostId(),
                    planningTimeoutMs
            );

            int dueCount = schedules.size();
            int lockedCount = locked.size();
            int conflictCount = dueCount - lockedCount;

            if (dueCount > 0) {
                plannerMetrics.onDueSchedules(dueCount);
            }

            if (conflictCount > 0) {
                plannerMetrics.onLockConflicts(conflictCount);
            }

            if (conflictCount > 0 && LOG.isDebugEnabled()) {
                List<String> conflictIds = schedules.stream()
                        .map(NopJobSchedule::getJobScheduleId)
                        .filter(id -> locked.stream().noneMatch(l -> l.getJobScheduleId().equals(id)))
                        .collect(Collectors.toList());
                LOG.debug("nop.job.planner.lock-conflict:dueCount={},lockedCount={},conflictIds={}",
                        dueCount, lockedCount, conflictIds);
            }

            for (NopJobSchedule schedule : locked) {
                Timestamp dueFireTime = dueFireTimes.get(schedule.getJobScheduleId());
                Timestamp nextFireTime = calculateNextFireTime(schedule);

                if (schedule.getScheduleStatus() != null
                        && schedule.getScheduleStatus() != _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED) {
                    LOG.debug("nop.job.planner.schedule-no-longer-enabled:scheduleId={},status={}",
                            schedule.getJobScheduleId(), schedule.getScheduleStatus());
                    continue;
                }

                if (shouldDiscard(schedule)) {
                    scheduleStore.advanceScheduleAfterSkip(schedule, nextFireTime);
                    continue;
                }

                if (shouldRecovery(schedule)) {
                    scheduleStore.recoveryFireAndAdvanceSchedule(schedule, nextFireTime);
                    continue;
                }

                NopJobFire fire = buildFire(schedule, dueFireTime);
                if (shouldOverlay(schedule)) {
                    scheduleStore.overlayFireAndAdvanceSchedule(schedule, fire, nextFireTime,
                            _NopJobCoreConstants.FIRE_STATUS_WAITING);
                    continue;
                }

                if (shouldParallel(schedule)) {
                    scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime,
                            _NopJobCoreConstants.FIRE_STATUS_WAITING);
                    continue;
                }

                if (defaultInt(schedule.getActiveFireCount()) > 0
                        && schedule.getBlockStrategy() != null
                        && !isKnownBlockStrategy(schedule.getBlockStrategy())) {
                    LOG.warn("nop.job.planner.unknown-block-strategy:scheduleId={},blockStrategy={},defaulting to DISCARD",
                            schedule.getJobScheduleId(), schedule.getBlockStrategy());
                    scheduleStore.advanceScheduleAfterSkip(schedule, nextFireTime);
                    continue;
                }

                scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime,
                        _NopJobCoreConstants.FIRE_STATUS_WAITING);
            }
        } catch (Exception e) {
            LOG.error("nop.job.planner.scan-failed", e);
        }
    }

    private NopJobFire buildFire(NopJobSchedule schedule, Timestamp dueFireTime) {
        long now = scheduleStore.getCurrentTime();

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(dueFireTime);
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
        fire.setPlannerInstanceId(AppConfig.hostId());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setRetryPolicyId(schedule.getRetryPolicyId());
        fire.setCreatedBy("system");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("system");
        fire.setUpdateTime(new Timestamp(now));
        fire.getJobParamsSnapshotComponent().set_jsonValue(copyMap(schedule.getJobParamsComponent().get_jsonMap()));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setDispatchMode(schedule.getDispatchMode());
        return fire;
    }

    private Timestamp calculateNextFireTime(NopJobSchedule schedule) {
        if (schedule.getTriggerType() != null &&
                schedule.getTriggerType() == _NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY) {
            return null;
        }

        long next = JobTriggerCalculator.calculateNextFireTime(
                toTriggerSpec(schedule),
                toEvalContext(schedule),
                scheduleStore.getCurrentTime()
        );
        return next <= 0 ? null : new Timestamp(next);
    }

    private TriggerSpec toTriggerSpec(NopJobSchedule schedule) {
        return TriggerSpecHelper.toTriggerSpec(schedule);
    }

    private ITriggerEvalContext toEvalContext(NopJobSchedule schedule) {
        return TriggerSpecHelper.toEvalContext(schedule);
    }

    private Map<String, Object> copyMap(Map<String, Object> map) {
        return map == null ? Collections.emptyMap() : map;
    }

    private boolean shouldDiscard(NopJobSchedule schedule) {
        return defaultInt(schedule.getActiveFireCount()) > 0
                && schedule.getBlockStrategy() != null
                && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_DISCARD;
    }

    private boolean shouldOverlay(NopJobSchedule schedule) {
        return defaultInt(schedule.getActiveFireCount()) > 0
                && schedule.getBlockStrategy() != null
                && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_OVERLAY;
    }

    private boolean shouldRecovery(NopJobSchedule schedule) {
        return defaultInt(schedule.getActiveFireCount()) > 0
                && schedule.getBlockStrategy() != null
                && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_RECOVERY;
    }

    private boolean shouldParallel(NopJobSchedule schedule) {
        return schedule.getBlockStrategy() != null
                && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_PARALLEL;
    }

    private boolean isKnownBlockStrategy(Integer blockStrategy) {
        return blockStrategy == _NopJobCoreConstants.BLOCK_STRATEGY_DISCARD
                || blockStrategy == _NopJobCoreConstants.BLOCK_STRATEGY_OVERLAY
                || blockStrategy == _NopJobCoreConstants.BLOCK_STRATEGY_RECOVERY
                || blockStrategy == _NopJobCoreConstants.BLOCK_STRATEGY_PARALLEL;
    }

    private long toTime(Timestamp value) {
        return value == null ? 0L : value.getTime();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    protected IScheduledExecutor getExecutor() {
        return GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
    }
}
