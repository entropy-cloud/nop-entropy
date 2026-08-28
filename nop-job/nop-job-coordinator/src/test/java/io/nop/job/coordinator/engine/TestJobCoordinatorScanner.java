package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.coordinator.metrics.IJobDispatcherMetrics;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.worker.engine.DefaultJobExecutionContextBuilder;
import io.nop.job.worker.engine.JobWorkerScannerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_NO_FITTING_WORKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobCoordinatorScanner extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int SCHEDULE_STATUS_COMPLETED = 30;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_SUCCESS = 30;
    private static final int FIRE_STATUS_TIMEOUT = 50;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_FAILED = 40;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_FAILED = 40;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_TIMEOUT = 50;
    private static final int TASK_STATUS_CANCELED = 60;
    private static final String EXECUTOR_KIND_TEST = "test";
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_TYPE_FIXED_DELAY = 3;
    private static final int BLOCK_STRATEGY_DISCARD = 1;
    private static final int BLOCK_STRATEGY_OVERLAY = 2;
    private static final int BLOCK_STRATEGY_RECOVERY = 4;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    @Inject
    IJobCompletionProcessor completionProcessorBean;

    private DefaultJobTaskBuilder defaultBuilder() {
        DefaultJobTaskBuilder b = new DefaultJobTaskBuilder();
        b.setDaoProvider(daoProvider);
        return b;
    }

    /**
     * plan 339：dispatchMode 是唯一路由键。map 缺少 "single" key 时，
     * 默认 fire（newSchedule 不设 dispatchMode）会抛 ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED，
     * 因此所有 dispatcher 装配必须含 "single"。
     */
    private Map<String, IJobTaskBuilder> singleBuilders() {
        return Map.of("single", (IJobTaskBuilder) defaultBuilder());
    }

    private Map<String, IJobTaskBuilder> withSingle(Map<String, IJobTaskBuilder> builders) {
        Map<String, IJobTaskBuilder> merged = new HashMap<>(builders);
        merged.put("single", defaultBuilder());
        return merged;
    }

    @Test
    public void testScheduleToFireToTask() {
        NopJobSchedule schedule = newSchedule("schedule-1", "job-1");
        Timestamp dueFireTime = new Timestamp(schedule.getNextFireTime().getTime());
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll();
        assertEquals(1, fires.size());

        NopJobFire fire = fires.get(0);
        assertEquals(schedule.getJobScheduleId(), fire.getJobScheduleId());
        assertEquals(dueFireTime, fire.getScheduledFireTime());
        assertEquals(schedule.getJobName(), fire.getJobName());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(1L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(dueFireTime, savedSchedule.getLastFireTime());

        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setTaskBuilders(singleBuilders());
        dispatcher.setBatchSize(10);
        dispatcher.setLockTimeoutMs(1000);
        dispatcher.setAssignedPartitions("1");
        dispatcher.setScheduleStore(scheduleStore);
        dispatcher.scanOnce();

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll();
        assertEquals(1, tasks.size());

        NopJobTask task = tasks.get(0);
        assertEquals(fire.getJobFireId(), task.getJobFireId());
        assertEquals(TASK_STATUS_WAITING, task.getTaskStatus());
        assertEquals(1, task.getTaskNo());

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, savedFire.getFireStatus());
    }

    /**
     * AR-95 / AR-84 根因：未配置 taskCostCpu/taskCostMemory/priority 的存量 schedule（null）
     * 经 dispatcher 派发后，落库的 task 行 cost/priority 必须为 0（非 null），而非把可空值写回。
     * 覆盖 single（默认 DefaultJobTaskBuilder）路径：builder 不设 cost，dispatcher 归一后落库。
     */
    @Test
    public void testDispatcherNormalizesNullCostScheduleToZeroSingle() {
        NopJobSchedule schedule = newSchedule("sched-ar95-single", "job-ar95-single");
        // cost/priority deliberately left null (legacy pre-Plan-212 schedule)
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        runPlanner();
        runDispatcher();

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll();
        assertEquals(1, tasks.size());
        NopJobTask task = tasks.get(0);
        assertNotNull(task.getCostCpu(), "costCpu must be non-null after dispatch (AR-95)");
        assertNotNull(task.getCostMemory(), "costMemory must be non-null after dispatch (AR-95)");
        assertNotNull(task.getPriority(), "priority must be non-null after dispatch (AR-95)");
        assertEquals(0, task.getCostCpu());
        assertEquals(0, task.getCostMemory());
        assertEquals(0, task.getPriority());
    }

    /**
     * AR-95 bestFit builder 路径：dispatcher 归一需覆盖 bestFit 派发路由。
     * 注册一个 stub bestFit builder（不设 cost），验证 dispatcher 在其产出后仍归一为 0。
     */
    @Test
    public void testDispatcherNormalizesNullCostToZeroBestFit() {
        IJobTaskBuilder bestFitStub = fire -> {
            NopJobTask task = new NopJobTask();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(1);
            task.setTaskStatus(TASK_STATUS_WAITING);
            task.setPartitionIndex(fire.getPartitionIndex());
            // deliberately do NOT set cost/priority — dispatcher normalization must fill 0
            return List.of(task);
        };

        NopJobSchedule schedule = newSchedule("sched-ar95-bestfit", "job-ar95-bestfit");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        runPlanner();

        // Set the planned fire's dispatchMode to bestFit so resolveTaskBuilder takes the bestFit route
        NopJobFire fire = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(f -> schedule.getJobScheduleId().equals(f.getJobScheduleId()))
                .findFirst().orElseThrow();
        fire.setDispatchMode("bestFit");
        daoProvider.daoFor(NopJobFire.class).updateEntityDirectly(fire);

        runDispatcher(Map.of("bestFit", bestFitStub));

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll();
        assertEquals(1, tasks.size());
        NopJobTask task = tasks.get(0);
        assertEquals(0, task.getCostCpu(), "bestFit-dispatched task costCpu must be normalized to 0");
        assertEquals(0, task.getCostMemory());
        assertEquals(0, task.getPriority());
    }

    @Test
    public void testBestFitAssignmentMetadataAndAssignmentCostEndToEnd() {
        AdaptiveJobTaskBuilder bestFitBuilder = new AdaptiveJobTaskBuilder();
        bestFitBuilder.setScheduleStore(scheduleStore);
        bestFitBuilder.setDaoProvider(daoProvider);
        bestFitBuilder.setLoadProvider(serviceName -> List.of());
        bestFitBuilder.setStrategy((taskCost, workers) -> {
            Assignment assignment = new Assignment();
            assignment.setWorkerInstanceId("worker-bestfit");
            assignment.setTargetHost("10.1.1.8:8080");
            assignment.setShardingIndex(1);
            assignment.setShardingTotal(4);
            assignment.setPartitionRange("20,10");
            assignment.setCost(new ResourceVector(120, 340));
            return new AssignmentPlan(List.of(assignment));
        });

        NopJobSchedule schedule = newSchedule("sched-bestfit-meta", "job-bestfit-meta");
        schedule.setTaskCostCpu(900);
        schedule.setTaskCostMemory(1800);
        schedule.setPriority(7);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        NopJobFire fire = newWaitingBestFitFire(schedule, "svc-meta");
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        JobDispatcherScannerImpl dispatcher = newDispatcher(10, Map.of("bestFit", bestFitBuilder));
        dispatcher.scanOnce();

        NopJobTask task = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> fire.getJobFireId().equals(item.getJobFireId()))
                .findFirst()
                .orElseThrow();
        assertEquals("worker-bestfit", task.getWorkerInstanceId());
        assertEquals("10.1.1.8:8080", task.getTargetHost());
        assertEquals(1, task.getShardingIndex());
        assertEquals(4, task.getShardingTotal());
        assertEquals("20,10", task.getPartitionRange());
        assertEquals(120, task.getCostCpu(), "assignment cost must survive dispatcher normalization");
        assertEquals(340, task.getCostMemory(), "assignment cost must survive dispatcher normalization");
        assertEquals(7, task.getPriority(), "builder-set schedule priority must be preserved");
    }

    @Test
    public void testDispatcherPreservesBuilderSetCostAndPriority() {
        IJobTaskBuilder bestFitStub = fire -> {
            NopJobTask task = new NopJobTask();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(1);
            task.setTaskStatus(TASK_STATUS_WAITING);
            task.setPartitionIndex(fire.getPartitionIndex());
            task.setCostCpu(111);
            task.setCostMemory(222);
            task.setPriority(9);
            return List.of(task);
        };

        NopJobSchedule schedule = newSchedule("sched-preserve-builder", "job-preserve-builder");
        schedule.setTaskCostCpu(700);
        schedule.setTaskCostMemory(800);
        schedule.setPriority(3);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        NopJobFire fire = newWaitingBestFitFire(schedule, "svc-preserve");
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        runDispatcher(Map.of("bestFit", bestFitStub));

        NopJobTask task = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> fire.getJobFireId().equals(item.getJobFireId()))
                .findFirst()
                .orElseThrow();
        assertEquals(111, task.getCostCpu());
        assertEquals(222, task.getCostMemory());
        assertEquals(9, task.getPriority());
    }

    private void runPlanner() {
        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();
    }

    private void runDispatcher() {
        runDispatcher(Map.of());
    }

    private void runDispatcher(Map<String, IJobTaskBuilder> builders) {
        JobDispatcherScannerImpl dispatcher = newDispatcher(10, builders);
        dispatcher.scanOnce();
    }

    // ========== AR-86: per-fire 错误隔离 + no-fitting-worker defer ==========

    /**
     * AR-86：dispatcher 批次中第 k 个 fire 抛异常（schedule 已删），第 k+1 个 fire 仍被正常派发。
     */
    @Test
    public void testPerFireIsolationBadFireDoesNotBlockRest() {
        NopJobSchedule schedule2 = newSchedule("sched-isol-good", "job-isol-good");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule2);

        // fire1 references a non-existent schedule -> loadSchedule throws (UnknownEntity)
        NopJobFire fire1 = newWaitingFire("sched-isol-bad", "job-isol-bad", null, "testInvoker");
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire1);
        // fire2 references the real schedule
        NopJobFire fire2 = newWaitingFire(schedule2.getJobScheduleId(), "job-isol-good", null, "testInvoker");
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire2);

        RecordingDispatcherMetrics metrics = new RecordingDispatcherMetrics();
        JobDispatcherScannerImpl dispatcher = newDispatcher(10);
        dispatcher.setDispatcherMetrics(metrics);
        dispatcher.scanOnce();

        // fire2 dispatched (RUNNING + task), fire1 not (stays DISPATCHING, loadSchedule threw)
        NopJobFire savedFire2 = fireStore.loadFire(fire2.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, savedFire2.getFireStatus(),
                "good fire must still dispatch despite the bad fire in the same batch");
        assertEquals(1, taskStore.findTasksByFireId(fire2.getJobFireId()).size(),
                "good fire's task must be inserted");
        assertEquals(1, metrics.fireDispatchFailed,
                "bad fire's failure must be recorded (metric emitted, not silently swallowed)");
    }

    /**
     * AR-86：no-fitting-worker（worker 满载的正常瞬态）的 fire 被回退为 WAITING（带 backoff），
     * 而非留卡 DISPATCHING；在 backoff 窗口内不被重复预锁（无紧循环）。
     */
    @Test
    public void testNoFittingWorkerRevertsToWaitingWithBackoff() {
        IJobTaskBuilder bestFitStub = fire -> {
            throw new NopException(ERR_JOB_NO_FITTING_WORKER)
                    .param("taskCost", "1000")
                    .param("serviceName", "svc");
        };

        NopJobSchedule schedule = newSchedule("sched-noWorker", "job-noWorker");
        schedule.setTaskCostCpu(1000);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        NopJobFire fire = newWaitingBestFitFire(schedule, "svc");
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        RecordingDispatcherMetrics metrics = new RecordingDispatcherMetrics();
        JobDispatcherScannerImpl dispatcher = newDispatcher(10, Map.of("bestFit", bestFitStub));
        dispatcher.setNoWorkerBackoffMs(30000);
        dispatcher.setDispatcherMetrics(metrics);
        dispatcher.scanOnce();

        NopJobFire saved = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_WAITING, saved.getFireStatus(),
                "no-fitting-worker fire reverted to WAITING (defer), not left DISPATCHING");
        assertNotNull(saved.getStartTime(),
                "backoff-until recorded on fire.startTime");
        assertTrue(saved.getStartTime().getTime() > System.currentTimeMillis(),
                "startTime is in the future (backoff window active)");
        assertEquals(1, metrics.fireDispatchFailed,
                "revert recorded as dispatch-failed metric");

        // second scan: fetchWaitingFires skips future-startTime fires -> not re-locked (no tight loop)
        dispatcher.scanOnce();
        NopJobFire saved2 = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_WAITING, saved2.getFireStatus(),
                "within backoff window the reverted fire must NOT be re-dispatched (no tight loop)");
    }

    private NopJobFire newWaitingFire(String scheduleId, String jobName, String dispatchMode, String executorKind) {
        long now = System.currentTimeMillis();
        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(scheduleId);
        fire.setNamespaceId("default");
        fire.setGroupId("default");
        fire.setJobName(jobName);
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.setDispatchMode(dispatchMode);
        fire.setExecutorKind(executorKind);
        fire.setPartitionIndex((short) 1);
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        return fire;
    }

    private static class RecordingDispatcherMetrics implements IJobDispatcherMetrics {
        int fireDispatchFailed;
        int waitingFires;
        int firesDispatched;

        @Override public void onWaitingFires(int count) { waitingFires += count; }
        @Override public void onDispatchConflicts(int count) { }
        @Override public void onFiresDispatched(int count) { firesDispatched += count; }
        @Override public void onFireDispatchFailed(int count) { fireDispatchFailed += count; }
    }

    /** AR-96: 计数服务发现调用次数的 IDiscoveryClient。 */
    private static final class CountingDiscoveryClient implements io.nop.cluster.discovery.IDiscoveryClient {
        int getInstancesCount;

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            getInstancesCount++;
            ServiceInstance inst = new ServiceInstance();
            inst.setInstanceId(AppConfig.hostId());
            inst.setAddr("localhost");
            inst.setPort(8080);
            inst.setHealthy(true);
            inst.setEnabled(true);
            return List.of(inst);
        }

        @Override
        public List<String> getServices() {
            return Collections.emptyList();
        }
    }

    /** AR-96: 包装真实 provider，计数 beginScan/endScan 调用（接线验证）。 */
    private static final class RecordingWorkerLoadProvider implements IWorkerLoadProvider {
        int beginScanCount;
        int endScanCount;
        private final IWorkerLoadProvider delegate;

        RecordingWorkerLoadProvider(IWorkerLoadProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public void beginScan() {
            beginScanCount++;
            delegate.beginScan();
        }

        @Override
        public void endScan() {
            endScanCount++;
            delegate.endScan();
        }

        @Override
        public List<WorkerLoad> getWorkerLoads(String serviceName) {
            return delegate.getWorkerLoads(serviceName);
        }
    }

    // ========== AR-94: 多 coordinator 超额派发（dispatcher 侧竞态存在性证明）==========

    /**
     * AR-94：两个 dispatcher 实例共享同一 DB，bestFit 模式下均通过 stale-read（MockLoadProvider
     * 恒报 reserved=ZERO）向同一 worker 派发任务，**累计派发成本超过 worker capacity**
     *（模拟跨事务 check-then-act 竞态的后果）。证明：竞态真实存在——两个 dispatcher 都把任务
     * 写入同一 task 表、归因给同一 worker、且总成本超出 capacity。容量不变量的权威守卫由
     * worker 侧 fit-check 承担（见 TestJobWorkerScanner 中的 guard 用例与本计划 design 决策）。
     */
    @Test
    public void testTwoDispatchersOverAssignBeyondCapacityViaStaleRead() {
        // Shared bestFit builder: load provider恒报 worker 空闲（reserved=ZERO），模拟 stale-read 竞态
        AdaptiveJobTaskBuilder bestFitBuilder = new AdaptiveJobTaskBuilder();
        bestFitBuilder.setScheduleStore(scheduleStore);
        bestFitBuilder.setDaoProvider(daoProvider);
        bestFitBuilder.setLoadProvider(serviceName -> {
            WorkerLoad load = new WorkerLoad();
            ServiceInstance inst = new ServiceInstance();
            inst.setInstanceId(AppConfig.hostId());
            load.setInstance(inst);
            load.setCapacity(new ResourceVector(1000, 2000));
            load.setReserved(ResourceVector.ZERO); // stale-read: 永远认为 worker 空闲
            return List.of(load);
        });

        // 5 个 schedule（cost {300,500}）+ 5 个 WAITING bestFit fire
        for (int i = 0; i < 5; i++) {
            NopJobSchedule schedule = newSchedule("sched-ar94-" + i, "job-ar94-" + i);
            schedule.setTaskCostCpu(300);
            schedule.setTaskCostMemory(500);
            daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
            daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(
                    newWaitingBestFitFire(schedule, "svc"));
        }

        // 两个 dispatcher 实例，batchSize=3 → dispatcher1 锁 3 个 fire，dispatcher2 锁剩余 2 个
        JobDispatcherScannerImpl dispatcher1 = newDispatcher(3, Map.of("bestFit", bestFitBuilder));
        JobDispatcherScannerImpl dispatcher2 = newDispatcher(3, Map.of("bestFit", bestFitBuilder));
        dispatcher1.scanOnce();
        dispatcher2.scanOnce();

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll();
        // 接线验证：两个 dispatcher 都把派发写入了同一 task 表
        assertEquals(5, tasks.size(),
                "both dispatchers must reach the same task table; dispatcher1 alone (batchSize=3) "
                        + "could produce at most 3, the extra 2 prove dispatcher2 also ran");
        for (NopJobTask task : tasks) {
            assertEquals(AppConfig.hostId(), task.getWorkerInstanceId(),
                    "all bestFit-dispatched tasks attributed to the single worker");
            assertEquals(300, task.getCostCpu());
            assertEquals(500, task.getCostMemory());
        }
        // 超额派发后果：5 × {300,500} = {1500,2500} > worker capacity {1000,2000}
        int totalCpu = tasks.stream().mapToInt(NopJobTask::getCostCpu).sum();
        int totalMem = tasks.stream().mapToInt(NopJobTask::getCostMemory).sum();
        assertTrue(totalCpu > 1000 && totalMem > 2000,
                "stale-read race causes over-assignment beyond capacity (total={" + totalCpu + "," + totalMem + "})");
    }

    /**
     * AR-96 接线验证：scanOnce 在批处理开始/结束调用 workerLoadProvider.beginScan/endScan，
     * 且共享同一 provider 实例的 AdaptiveJobTaskBuilder 在 scan 作用域内的多次 getWorkerLoads
     * 命中缓存（服务发现 + 聚合各只执行一次，不随 fire 数线性增长）。
     */
    @Test
    public void testScanOnceInvokesWorkerLoadProviderLifecycleAndCachesAcrossFires() {
        CountingDiscoveryClient discovery = new CountingDiscoveryClient();
        DefaultWorkerLoadProvider realProvider = new DefaultWorkerLoadProvider();
        realProvider.setDiscoveryClient(discovery);
        realProvider.setTaskStore(taskStore);
        RecordingWorkerLoadProvider recording = new RecordingWorkerLoadProvider(realProvider);

        // bestFit builder 共用同一 provider 实例（与 app-engine.beans.xml 的 ref="nopWorkerLoadProvider" 一致）
        AdaptiveJobTaskBuilder bestFitBuilder = new AdaptiveJobTaskBuilder();
        bestFitBuilder.setScheduleStore(scheduleStore);
        bestFitBuilder.setDaoProvider(daoProvider);
        bestFitBuilder.setLoadProvider(recording);

        // 3 个 WAITING bestFit fire（同一 serviceName）
        for (int i = 0; i < 3; i++) {
            NopJobSchedule schedule = newSchedule("sched-ar96-" + i, "job-ar96-" + i);
            schedule.setTaskCostCpu(100);
            daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
            daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(newWaitingBestFitFire(schedule, "svc"));
        }

        JobDispatcherScannerImpl dispatcher = newDispatcher(10, Map.of("bestFit", bestFitBuilder));
        dispatcher.setWorkerLoadProvider(recording); // 接线：dispatcher 持有同一 provider 做生命周期管理
        dispatcher.scanOnce();

        assertEquals(1, recording.beginScanCount, "AR-96 wiring: scanOnce calls beginScan exactly once");
        assertEquals(1, recording.endScanCount, "AR-96 wiring: scanOnce calls endScan exactly once");
        assertEquals(1, discovery.getInstancesCount,
                "AR-96 cache: discovery runs once for the whole scan, not once per bestFit fire (3 fires)");

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll();
        assertEquals(3, tasks.size(), "all 3 bestFit fires dispatched (each via cached load snapshot)");
    }

    /**
     * AR-98 端到端验证：partition 模式 fire 经 dispatcher（scanOnce → resolveTaskBuilder →
     * PartitionTaskBuilder）派发后，落库的 task 的 partitionRange 并集覆盖完整 SMALLINT 范围
     * [0, 32767]（含上界 32767），不丢边界。
     */
    @Test
    public void testPartitionDispatchEndToEndCoversFullSmallintBoundary() {
        // 带 mock discovery client 的 PartitionTaskBuilder（2 个 healthy worker → 2 分片）
        PartitionTaskBuilder partitionBuilder = new PartitionTaskBuilder();
        partitionBuilder.setScheduleStore(scheduleStore);
        partitionBuilder.setDaoProvider(daoProvider);
        partitionBuilder.setDiscoveryClient(new io.nop.cluster.discovery.IDiscoveryClient() {
            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                ServiceInstance w1 = new ServiceInstance();
                w1.setInstanceId("worker-1");
                w1.setAddr("h1");
                w1.setPort(8080);
                w1.setHealthy(true);
                w1.setEnabled(true);
                ServiceInstance w2 = new ServiceInstance();
                w2.setInstanceId("worker-2");
                w2.setAddr("h2");
                w2.setPort(8080);
                w2.setHealthy(true);
                w2.setEnabled(true);
                return List.of(w1, w2);
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }
        });

        // schedule + partition 模式 fire
        NopJobSchedule schedule = newSchedule("sched-ar98", "job-ar98");
        schedule.setPartitionCount(2);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        long now = System.currentTimeMillis();
        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.setDispatchMode("partition");
        fire.setJobParamsSnapshot(JsonTool.stringify(Map.of("serviceName", "svc-ar98")));
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        JobDispatcherScannerImpl dispatcher = newDispatcher(10, Map.of("partition", partitionBuilder));
        dispatcher.scanOnce();

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(t -> fire.getJobFireId().equals(t.getJobFireId()))
                .collect(Collectors.toList());
        assertEquals(2, tasks.size(), "partition dispatch end-to-end: 2 sharded tasks created");

        int totalLimit = 0;
        int maxLast = -1;
        for (NopJobTask task : tasks) {
            assertNotNull(task.getPartitionRange(), "partitionRange set by builder and persisted");
            io.nop.api.core.beans.IntRangeBean range =
                    io.nop.api.core.beans.IntRangeBean.parse(task.getPartitionRange());
            totalLimit += range.getLimit();
            maxLast = Math.max(maxLast, range.getLast());
        }
        assertEquals(Short.MAX_VALUE + 1, totalLimit,
                "AR-98 end-to-end: union of partition ranges covers full SMALLINT range [0, 32767]");
        assertEquals(Short.MAX_VALUE, maxLast,
                "AR-98 end-to-end: boundary partition 32767 is covered (not dropped)");
    }

    private JobDispatcherScannerImpl newDispatcher(int batchSize) {
        return newDispatcher(batchSize, Map.of());
    }

    private JobDispatcherScannerImpl newDispatcher(int batchSize, Map<String, IJobTaskBuilder> builders) {
        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setTaskBuilders(withSingle(builders));
        dispatcher.setBatchSize(batchSize);
        dispatcher.setLockTimeoutMs(1000);
        dispatcher.setAssignedPartitions("1");
        dispatcher.setScheduleStore(scheduleStore);
        return dispatcher;
    }

    private NopJobFire newWaitingBestFitFire(NopJobSchedule schedule, String serviceName) {
        long now = System.currentTimeMillis();
        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.setDispatchMode("bestFit");
        fire.setExecutorKind("bestFit");
        // 与生产路径一致（JobScheduleStoreImpl.newFire.setJobParamsSnapshot(schedule.getJobParams())）：
        // 直接写 String 列而非 component.set_jsonValue(Map)——后者经 markDirty 延迟 flushToEntity，
        // 在 saveEntityDirectly 时不一定刷入列，导致 DB round-trip 丢 jobParamsSnapshot → bestFit 误 fallback。
        fire.setJobParamsSnapshot(JsonTool.stringify(Map.of("serviceName", serviceName)));
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        return fire;
    }

    @Test
    public void testCompletionUpdatesFireAndSchedule() {
        PreparedChain chain = prepareChain(newSchedule("schedule-2", "job-2"));
        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        NopJobFire currentFire = fireStore.loadFire(chain.fire.getJobFireId());
        Timestamp startTime = currentFire.getStartTime();
        Timestamp endTime = new Timestamp(startTime.getTime() + 2000);

        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setDurationMs(2000L);
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = (JobCompletionProcessorImpl) completionProcessorBean;
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        completion.scanOnce();

        NopJobFire savedFire = fireStore.loadFire(chain.fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, savedFire.getFireStatus());
        assertEquals(startTime, savedFire.getStartTime());
        assertEquals(endTime, savedFire.getEndTime());
        assertEquals(2000L, savedFire.getDurationMs());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(endTime, savedSchedule.getLastEndTime());
        assertEquals(FIRE_STATUS_SUCCESS, savedSchedule.getLastFireStatus());
    }

    @Test
    public void testFixedDelayCompletionAdvancesNextFireTime() {
        NopJobSchedule schedule = newSchedule("schedule-3", "job-3");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_DELAY);
        PreparedChain chain = prepareChain(schedule);
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 3000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = (JobCompletionProcessorImpl) completionProcessorBean;
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(endTime.getTime() + schedule.getRepeatIntervalMs(), savedSchedule.getNextFireTime().getTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testCompletionMarksScheduleCompletedWhenTaskRequestsCompletion() {
        NopJobSchedule schedule = newSchedule("schedule-8", "job-8");
        schedule.setJobParams(JsonTool.stringify(Map.of("allowResultCompletion", true)));
        PreparedChain chain = prepareChain(schedule);
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 2000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setResultPayload(JsonTool.stringify(Map.of("completed", true)));
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_COMPLETED, savedSchedule.getScheduleStatus());
        assertEquals(FIRE_STATUS_SUCCESS, savedSchedule.getLastFireStatus());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(endTime, savedSchedule.getLastEndTime());
        assertEquals(null, savedSchedule.getNextFireTime());
    }

    @Test
    public void testCompletionUsesTaskNextScheduleTime() {
        PreparedChain chain = prepareChain(newSchedule("schedule-9", "job-9"));
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 2000);
        Timestamp nextScheduleTime = new Timestamp(endTime.getTime() + 5000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setResultPayload(JsonTool.stringify(Map.of("nextScheduleTime", nextScheduleTime.getTime())));
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ENABLED, savedSchedule.getScheduleStatus());
        assertEquals(nextScheduleTime, savedSchedule.getNextFireTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testFixedDelayCompletionUsesTaskNextScheduleTimeOverride() {
        NopJobSchedule schedule = newSchedule("schedule-10", "job-10");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_DELAY);
        PreparedChain chain = prepareChain(schedule);
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 3000);
        Timestamp overriddenNextFireTime = new Timestamp(endTime.getTime() + 9000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setResultPayload(JsonTool.stringify(Map.of("nextScheduleTime", overriddenNextFireTime.getTime())));
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(overriddenNextFireTime, savedSchedule.getNextFireTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testTimeoutCheckerMarksTaskAndCompletionFinalizesFire() {
        PreparedChain chain = prepareChain(newSchedule("schedule-4", "job-4"));
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 4000);

        NopJobSchedule runningSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        runningSchedule.setTimeoutSeconds(1);
        daoProvider.daoFor(NopJobSchedule.class).updateEntityDirectly(runningSchedule);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setStartTime(startTime);
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        taskStore.updateTask(task);

        JobTimeoutCheckerImpl timeoutChecker = new JobTimeoutCheckerImpl();
        timeoutChecker.setTaskStore(taskStore);
        timeoutChecker.setFireStore(fireStore);
        timeoutChecker.setScheduleStore(scheduleStore);
        timeoutChecker.setBatchSize(10);
        timeoutChecker.setAssignedPartitions("1");
        timeoutChecker.scanOnce();

        NopJobTask timedOutTask = taskStore.loadTask(chain.task.getJobTaskId());
        assertEquals(TASK_STATUS_TIMEOUT, timedOutTask.getTaskStatus());
        assertEquals("JOB_TIMEOUT", timedOutTask.getErrorCode());
        assertNotNull(timedOutTask.getEndTime());

        JobCompletionProcessorImpl completion = (JobCompletionProcessorImpl) completionProcessorBean;
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        completion.scanOnce();

        NopJobFire savedFire = fireStore.loadFire(chain.fire.getJobFireId());
        assertEquals(FIRE_STATUS_TIMEOUT, savedFire.getFireStatus());
        assertEquals("JOB_TIMEOUT", savedFire.getErrorCode());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(FIRE_STATUS_TIMEOUT, savedSchedule.getLastFireStatus());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testTimeoutCheckerInvokesCancelHandler() {
        PreparedChain chain = prepareChain(newSchedule("schedule-7", "job-7"));
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 4000);

        NopJobSchedule runningSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        runningSchedule.setTimeoutSeconds(1);
        daoProvider.daoFor(NopJobSchedule.class).updateEntityDirectly(runningSchedule);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setStartTime(startTime);
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        taskStore.updateTask(task);

        RecordingCancelHandler cancelHandler = new RecordingCancelHandler();

        JobTimeoutCheckerImpl timeoutChecker = new JobTimeoutCheckerImpl();
        timeoutChecker.setTaskStore(taskStore);
        timeoutChecker.setFireStore(fireStore);
        timeoutChecker.setScheduleStore(scheduleStore);
        timeoutChecker.setCancelHandler(cancelHandler);
        timeoutChecker.setBatchSize(10);
        timeoutChecker.setAssignedPartitions("1");
        timeoutChecker.scanOnce();

        assertEquals(chain.schedule.getJobScheduleId(), cancelHandler.scheduleId);
        assertEquals(chain.fire.getJobFireId(), cancelHandler.fireId);
        assertEquals(chain.task.getJobTaskId(), cancelHandler.taskId);
    }

    @Test
    public void testDiscardBlockStrategySkipsNewFire() {
        NopJobSchedule schedule = newSchedule("schedule-5", "job-5");
        schedule.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        Timestamp runningFireTime = new Timestamp(schedule.getNextFireTime().getTime() - 1000);
        schedule.setLastFireTime(runningFireTime);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire activeFire = newActiveFire(schedule, runningFireTime);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(activeFire);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .collect(Collectors.toList());
        assertEquals(1, fires.size());
        assertEquals(FIRE_STATUS_RUNNING, fires.get(0).getFireStatus());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(1L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(runningFireTime, savedSchedule.getLastFireTime());
    }

    @Test
    public void testOverlayBlockStrategyCancelsActiveFireAndCreatesReplacement() {
        NopJobSchedule schedule = newSchedule("schedule-6", "job-6");
        schedule.setBlockStrategy(BLOCK_STRATEGY_OVERLAY);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        Timestamp runningFireTime = new Timestamp(schedule.getNextFireTime().getTime() - 1000);
        schedule.setLastFireTime(runningFireTime);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire activeFire = newActiveFire(schedule, runningFireTime);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(activeFire);

        NopJobTask activeTask = newActiveTask(activeFire, TASK_STATUS_RUNNING,
                new Timestamp(System.currentTimeMillis() - 500));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(activeTask);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .collect(Collectors.toList());
        assertEquals(2, fires.size());

        NopJobFire canceledFire = fires.stream()
                .filter(item -> FIRE_STATUS_CANCELED == item.getFireStatus())
                .findFirst()
                .orElseThrow();
        NopJobFire replacementFire = fires.stream()
                .filter(item -> FIRE_STATUS_CANCELED != item.getFireStatus())
                .findFirst()
                .orElseThrow();
        assertNotNull(canceledFire.getEndTime());
        assertEquals("JOB_OVERLAID", canceledFire.getErrorCode());
        assertEquals(schedule.getNextFireTime(), replacementFire.getScheduledFireTime());

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> canceledFire.getJobFireId().equals(item.getJobFireId()))
                .collect(Collectors.toList());
        assertEquals(1, tasks.size());
        assertEquals(TASK_STATUS_CANCELED, tasks.get(0).getTaskStatus());
        assertEquals("JOB_OVERLAID", tasks.get(0).getErrorCode());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(2L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertEquals(FIRE_STATUS_WAITING, savedSchedule.getLastFireStatus());
        assertNotNull(savedSchedule.getLastEndTime());
        assertNotNull(savedSchedule.getNextFireTime());
    }

    /**
     * check2 [P0]：once 语义在 planner 路径失效。once 型 schedule（triggerType=ONCE、无 cron、
     * repeatInterval<=0）首次触发并派发（fire 离开 WAITING）后，后续 planner 周期不得再创建
     * 任何新 fire——OnceTrigger 的 once 判定必须在 planner 的"每次计算重建 trigger 链"模式下
     * 依然成立（持久化判定，而非实例内 first 标志）。schedule 应转入 dormant（nextFireTime=null）。
     */
    @Test
    public void testOnceScheduleFiresExactlyOnceAcrossPlannerCycles() {
        long now = System.currentTimeMillis();
        Timestamp onceTime = new Timestamp(now - 1000);

        NopJobSchedule schedule = newSchedule("sched-once", "job-once");
        schedule.setTriggerType(4); // TRIGGER_TYPE_ONCE
        schedule.setRepeatIntervalMs(null);
        schedule.setCronExpr(null);
        schedule.setMinScheduleTime(onceTime);
        schedule.setNextFireTime(onceTime);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        // cycle 1: 首次 due → 恰好创建一个 fire，scheduledFireTime = once time
        runPlanner();
        List<NopJobFire> fires = firesOf(schedule.getJobScheduleId());
        assertEquals(1, fires.size(), "first planner cycle must create exactly one fire");
        assertEquals(onceTime, fires.get(0).getScheduledFireTime());

        // 模拟 dispatcher 已推进该 fire（WAITING → RUNNING）：hasWaitingFire 去重从此失效，
        // 若 once 语义依赖 trigger 实例状态，此处后每个周期都会再插一个同 scheduledFireTime 的 fire
        NopJobFire fire1 = fireStore.loadFire(fires.get(0).getJobFireId());
        fire1.setFireStatus(FIRE_STATUS_RUNNING);
        fire1.setStartTime(new Timestamp(now));
        daoProvider.daoFor(NopJobFire.class).updateEntityDirectly(fire1);

        runPlanner();
        runPlanner();

        assertEquals(1, firesOf(schedule.getJobScheduleId()).size(),
                "once schedule must not create additional fires after its first (planner rebuilds the "
                        + "trigger chain every cycle, so instance-level once flags are always reset)");

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(1L, savedSchedule.getFireCount(), "fireCount must stay at 1");
        assertEquals(1, savedSchedule.getActiveFireCount(), "activeFireCount must stay at 1 (fire1 still RUNNING)");
        assertTrue(savedSchedule.getNextFireTime() == null,
                "exhausted once schedule goes dormant (nextFireTime=null), not re-due every cycle");
    }

    private List<NopJobFire> firesOf(String scheduleId) {
        return daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(f -> scheduleId.equals(f.getJobScheduleId()))
                .collect(Collectors.toList());
    }

    /**
     * check2 [P3-2]: 未知 blockStrategy 的处理必须与忙闲状态无关地统一为 DISCARD（跳过 + warn）。
     * 此前 activeFireCount==0 时落入普通插入执行（且无 warn），忙时才跳过——同一非法配置
     * 产生两种行为。本用例断言闲时（activeFireCount=0）也跳过、不产生 fire。
     */
    @Test
    public void testUnknownBlockStrategySkipsFireEvenWhenIdle() {
        NopJobSchedule schedule = newSchedule("sched-unknown-bs", "job-unknown-bs");
        schedule.setBlockStrategy(99); // unknown strategy value
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        runPlanner();

        assertEquals(0, firesOf(schedule.getJobScheduleId()).size(),
                "unknown blockStrategy must consistently skip fire creation (default to DISCARD), "
                        + "not execute when idle");

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(0L, savedSchedule.getFireCount(), "no fire created → fireCount stays 0");
        assertNotNull(savedSchedule.getNextFireTime(), "schedule advances to the next slot after skip");
    }

    private PreparedChain prepareChain(NopJobSchedule schedule) {
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        NopJobFire fire = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .findFirst()
                .orElseThrow();

        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setTaskBuilders(singleBuilders());
        dispatcher.setBatchSize(10);
        dispatcher.setLockTimeoutMs(1000);
        dispatcher.setAssignedPartitions("1");
        dispatcher.setScheduleStore(scheduleStore);
        dispatcher.scanOnce();

        NopJobTask task = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> fire.getJobFireId().equals(item.getJobFireId()))
                .findFirst()
                .orElseThrow();

        return new PreparedChain(schedule, fire, task);
    }

    private JobCompletionProcessorImpl newCompletionProcessor() {
        JobCompletionProcessorImpl completion = (JobCompletionProcessorImpl) completionProcessorBean;
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        return completion;
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind(EXECUTOR_KIND_TEST);
        schedule.setExecutorKind("testInvoker");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(now - 1000));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        return schedule;
    }

    private NopJobFire newActiveFire(NopJobSchedule schedule, Timestamp scheduledFireTime) {
        long now = System.currentTimeMillis();

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(scheduledFireTime);
        fire.setFireStatus(FIRE_STATUS_RUNNING);
        fire.setStartTime(scheduledFireTime);
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        return fire;
    }

    private NopJobTask newActiveTask(NopJobFire fire, int taskStatus, Timestamp startTime) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(taskStatus);
        task.setWorkerInstanceId("worker-1");
        task.setStartTime(startTime);
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        return task;
    }

    @Test
    public void testRecoveryBlockStrategyResetsFailedFire() {
        NopJobSchedule schedule = newSchedule("schedule-11", "job-11");
        schedule.setBlockStrategy(BLOCK_STRATEGY_RECOVERY);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        Timestamp failedFireTime = new Timestamp(schedule.getNextFireTime().getTime() - 1000);
        schedule.setLastFireTime(failedFireTime);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire failedFire = newActiveFire(schedule, failedFireTime);
        failedFire.setFireStatus(FIRE_STATUS_FAILED);
        failedFire.setEndTime(new Timestamp(failedFireTime.getTime() + 1000));
        failedFire.setDurationMs(1000L);
        failedFire.setErrorCode("TEST_ERROR");
        failedFire.setErrorMessage("test error");
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(failedFire);

        NopJobTask failedTask = newActiveTask(failedFire, TASK_STATUS_FAILED,
                new Timestamp(System.currentTimeMillis() - 500));
        failedTask.setEndTime(new Timestamp(System.currentTimeMillis()));
        failedTask.setDurationMs(500L);
        failedTask.setErrorCode("TEST_ERROR");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(failedTask);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        NopJobFire recoveredFire = (NopJobFire) daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> failedFire.getJobFireId().equals(item.getJobFireId()))
                .findFirst()
                .orElseThrow();
        assertEquals(FIRE_STATUS_WAITING, recoveredFire.getFireStatus());
        assertEquals(null, recoveredFire.getErrorCode());
        assertEquals(null, recoveredFire.getErrorMessage());

        NopJobTask recoveredTask = (NopJobTask) daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> failedTask.getJobTaskId().equals(item.getJobTaskId()))
                .findFirst()
                .orElseThrow();
        assertEquals(TASK_STATUS_WAITING, recoveredTask.getTaskStatus());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(2, savedSchedule.getActiveFireCount());
        assertNotNull(savedSchedule.getNextFireTime());
    }

    @Test
     public void testRecoveryBlockStrategyNoFailedFireCreatesFire() {
         // R2-12 fix: RECOVERY schedule with no failed fires should fire normally (not skip forever)
         NopJobSchedule schedule = newSchedule("schedule-12", "job-12");
         schedule.setBlockStrategy(BLOCK_STRATEGY_RECOVERY);
         daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

         JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
         planner.setScheduleStore(scheduleStore);
        planner.setDaoProvider(daoProvider);
         planner.setBatchSize(10);
         planner.setPlanningTimeoutMs(1000);
         planner.setAssignedPartitions("1");
         planner.scanOnce();

         List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                 .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                 .collect(Collectors.toList());
         assertEquals(1, fires.size());

         NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
         assertNotNull(savedSchedule.getNextFireTime());
         assertEquals(1L, savedSchedule.getFireCount());
    }

    private static final class PreparedChain {
        private final NopJobSchedule schedule;
        private final NopJobFire fire;
        private final NopJobTask task;

        private PreparedChain(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.schedule = schedule;
            this.fire = fire;
            this.task = task;
        }
    }

    private static final class RecordingCancelHandler implements IJobCancelHandler {
        private String scheduleId;
        private String fireId;
        private String taskId;

        @Override
        public void cancelRunningTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.scheduleId = schedule.getJobScheduleId();
            this.fireId = fire.getJobFireId();
            this.taskId = task.getJobTaskId();
        }
    }

    @Test
    public void testCoordinatorStopExceptionDoesNotCascade() {
        JobCoordinator coordinator = new JobCoordinator();

        RecordingStopScanner planner = new RecordingStopScanner();
        ThrowingStopScanner dispatcher = new ThrowingStopScanner("dispatcher-failed");
        RecordingStopScanner completion = new RecordingStopScanner();
        RecordingStopScanner timeout = new RecordingStopScanner();

        coordinator.setPlannerScanner(planner);
        coordinator.setDispatcherScanner(dispatcher);
        coordinator.setCompletionProcessor(completion);
        coordinator.setTimeoutChecker(timeout);

        coordinator.start();
        coordinator.stop();

        assertTrue(planner.stopped, "planner should be stopped");
        assertTrue(dispatcher.stopCalled, "dispatcher stop should be called even though it throws");
        assertTrue(completion.stopped, "completion should be stopped despite dispatcher failure");
        assertTrue(timeout.stopped, "timeout should be stopped despite dispatcher failure");
    }

    @Test
    public void testCoordinatorStopOrder() {
        JobCoordinator coordinator = new JobCoordinator();
        List<String> stopOrder = new java.util.ArrayList<>();

        coordinator.setPlannerScanner(new OrderRecordingScanner(stopOrder, "planner"));
        coordinator.setDispatcherScanner(new OrderRecordingScanner(stopOrder, "dispatcher"));
        coordinator.setCompletionProcessor(new OrderRecordingScanner(stopOrder, "completion"));
        coordinator.setTimeoutChecker(new OrderRecordingScanner(stopOrder, "timeout"));

        coordinator.start();
        coordinator.stop();

        assertEquals(List.of("planner", "dispatcher", "completion", "timeout"), stopOrder);
    }

    private static final class RecordingStopScanner implements IJobPlannerScanner, IJobDispatcherScanner,
            IJobCompletionProcessor, IJobTimeoutChecker {
        boolean stopped;

        @Override
        public void startScanning() {}

        @Override
        public void stopScanning() {
            stopped = true;
        }
    }

    private static final class ThrowingStopScanner implements IJobPlannerScanner, IJobDispatcherScanner,
            IJobCompletionProcessor, IJobTimeoutChecker {
        boolean stopCalled;

        private final String message;

        ThrowingStopScanner(String message) {
            this.message = message;
        }

        @Override
        public void startScanning() {}

        @Override
        public void stopScanning() {
            stopCalled = true;
            throw new NopException(JobCoreErrors.ERR_JOB_CALENDAR_MAX_ITERATION_EXCEEDED);
        }
    }

    /**
     * plan 2254 端到端：WAITING fire(executorKind=rpcPoll) → dispatcher 产生 WAITING task →
     * 内嵌 worker 执行链（JobWorkerScannerImpl + invokerResolver）认领 → RemoteJobInvoker 三段式
     * （startJob → 轮询 getJobStatus → SUCCESS）→ 终态写回 → fire/schedule 聚合。
     * mock worker 记录调用序列。
     */
    @Test
    public void testE2E_rpcPoll_fullChain() {
        NopJobSchedule schedule = newSchedule("sched-rpcpoll", "job-rpcpoll");
        schedule.setExecutorKind("rpcPoll");
        schedule.getJobParamsComponent().set_jsonValue(Map.of(
                "serviceName", "mockWorker",
                "data", Map.of("url", "http://worker/job")
        ));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-rpcpoll", schedule);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        MockPollWorker worker = new MockPollWorker();

        runDispatcher();

        List<NopJobTask> tasksBefore = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(t -> fire.getJobFireId().equals(t.getJobFireId()))
                .collect(Collectors.toList());
        assertEquals(1, tasksBefore.size());

        runWorkerScanner(worker);

        NopJobTask task = tasksBefore.get(0);
        NopJobTask fresh = waitForStatus(task.getJobTaskId(), TASK_STATUS_SUCCESS);
        assertEquals(TASK_STATUS_SUCCESS, fresh.getTaskStatus());

        ((JobCompletionProcessorImpl) completionProcessorBean).scanOnce();

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, savedFire.getFireStatus());

        assertEquals(1, worker.startCalls);
        assertEquals(task.getJobTaskId(), worker.lastInstanceId);
        assertEquals("http://worker/job", worker.lastData.get("url"));
        assertEquals("mockWorker", worker.lastServiceName);
    }

    /**
     * plan 2254 端到端：worker 持续 RUNNING + schedule.timeoutSeconds 超时 →
     * RemoteJobInvoker 墙钟判定 → cancelJob + ERROR(ERR_JOB_TIMEOUT) → task TIMEOUT。
     */
    @Test
    public void testE2E_rpcPoll_wallClockTimeout() {
        NopJobSchedule schedule = newSchedule("sched-rpcpoll-timeout", "job-rpcpoll-timeout");
        schedule.setExecutorKind("rpcPoll");
        schedule.setTimeoutSeconds(1);
        schedule.getJobParamsComponent().set_jsonValue(Map.of("serviceName", "mockWorker"));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-rpcpoll-timeout", schedule);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        MockPollWorker worker = new MockPollWorker();
        worker.alwaysRunning = true;

        runDispatcher();

        List<NopJobTask> tasksBefore = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(t -> fire.getJobFireId().equals(t.getJobFireId()))
                .collect(Collectors.toList());
        assertEquals(1, tasksBefore.size());

        runWorkerScanner(worker);

        NopJobTask fresh = waitForStatus(tasksBefore.get(0).getJobTaskId(), TASK_STATUS_TIMEOUT);
        assertEquals(TASK_STATUS_TIMEOUT, fresh.getTaskStatus());
        assertTrue(worker.cancelCalls > 0, "wall-clock timeout must trigger remote cancel");
    }

    private NopJobFire newFire(String fireId, NopJobSchedule schedule) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(fireId);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(new Timestamp(System.currentTimeMillis() - 1000));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setDispatchMode(schedule.getDispatchMode());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setJobParamsSnapshot(JsonTool.stringify(
                schedule.getJobParamsComponent().get_jsonMap()));
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(System.currentTimeMillis()));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return fire;
    }

    private NopJobTask waitForStatus(String jobTaskId, int expectedStatus) {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            NopJobTask fresh = taskStore.loadTask(jobTaskId);
            if (fresh != null && fresh.getTaskStatus() == expectedStatus) {
                return fresh;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for task status", e);
            }
        }
        NopJobTask last = taskStore.loadTask(jobTaskId);
        assertEquals(expectedStatus, last != null ? last.getTaskStatus() : -1,
                "task did not reach expected status in time");
        return last;
    }

    private void runWorkerScanner(MockPollWorker worker) {
        JobWorkerScannerImpl workerScanner = new JobWorkerScannerImpl();
        workerScanner.setTaskStore(taskStore);
        workerScanner.setFireStore(fireStore);
        workerScanner.setScheduleStore(scheduleStore);
        workerScanner.setInvokerResolver((s, f) -> buildRpcPollInvoker(worker));
        workerScanner.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        workerScanner.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        workerScanner.setBatchSize(10);
        workerScanner.setLockTimeoutMs(1000);
        workerScanner.setAssignedPartitions("1");
        workerScanner.scanOnce();
    }

    private RemoteJobInvoker buildRpcPollInvoker(MockPollWorker worker) {
        RpcPollTaskManager manager = new RpcPollTaskManager();
        manager.setRpcPollTaskClient(worker);
        manager.setPollIntervalMs(1000);

        RemoteJobInvoker invoker = new RemoteJobInvoker();
        invoker.setTaskStore(taskStore);
        invoker.setFireStore(fireStore);
        invoker.setScheduleStore(scheduleStore);
        invoker.setRpcPollTaskClient(worker);
        invoker.setPollTaskManager(manager);
        return invoker;
    }

    /**
     * 模拟 worker（三方法语义，均为异步）：startJob 登记并返回 jobTaskId；getJobStatus 先 RUNNING 再
     * SUCCESS（或持续 RUNNING）；cancelJob 计数。记录方法名/instanceId/data/serviceName。
     */
    static final class MockPollWorker implements IRpcPollTaskClient {
        int startCalls;
        int cancelCalls;
        boolean alwaysRunning;
        String lastMethod;
        String lastInstanceId;
        String lastServiceName;
        Map<?, ?> lastData;

        @Override
        public CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                ICancelToken cancelToken) {
            startCalls++;
            lastMethod = "invokeJob";
            lastInstanceId = task.getJobTaskId();
            lastServiceName = resolveServiceName(schedule, fire, task);
            Object data = task.getEffectiveParams(fire).get("data");
            if (data instanceof Map) {
                lastData = (Map<?, ?>) data;
            } else if (data instanceof String) {
                lastData = (Map<?, ?>) JsonTool.parse((String) data);
            }
            return CompletableFuture.completedFuture(task.getJobTaskId());
        }

        @Override
        public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire,
                                                            NopJobTask task, ICancelToken cancelToken) {
            lastMethod = "getJobStatus";
            lastInstanceId = task.getJobTaskId();
            TaskStatusBean bean = new TaskStatusBean();
            bean.setTaskStatus(alwaysRunning ? TaskStatusBean.STATUS_RUNNING : TaskStatusBean.STATUS_SUCCESS);
            return CompletableFuture.completedFuture(bean);
        }

        @Override
        public CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                  ICancelToken cancelToken) {
            cancelCalls++;
            lastMethod = "cancelJob";
            lastInstanceId = task.getJobTaskId();
            return CompletableFuture.completedFuture(true);
        }

        private static String resolveServiceName(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            Object value = task.getEffectiveParams(fire).get("serviceName");
            return value != null ? String.valueOf(value) : null;
        }
    }

    private static final class OrderRecordingScanner implements IJobPlannerScanner, IJobDispatcherScanner,
            IJobCompletionProcessor, IJobTimeoutChecker {
        private final List<String> order;
        private final String name;

        OrderRecordingScanner(List<String> order, String name) {
            this.order = order;
            this.name = name;
        }

        @Override
        public void startScanning() {}

        @Override
        public void stopScanning() {
            order.add(name);
        }
    }
}
