package io.nop.job.worker.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobWorkerScanner extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_SUCCESS = 30;
    private static final int FIRE_STATUS_FAILED = 40;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_FAILED = 40;
    private static final String EXECUTOR_KIND_TEST = "test";
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_SOURCE_SCHEDULE = 1;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    private IBeanContainer originalBeanContainer;

    @AfterEach
    public void tearDown() {
        if (originalBeanContainer != null) {
            BeanContainer.registerInstance(originalBeanContainer);
            originalBeanContainer = null;
        }
    }

    @Test
    public void testWorkerExecutesTaskSuccess() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask prepared = prepareWaitingTask("schedule-worker-1", "job-worker-1");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.scanOnce();

        NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, savedTask.getTaskStatus());
        assertNotNull(savedTask.getStartTime());
        assertNotNull(savedTask.getEndTime());
        assertEquals(AppConfig.hostId(), savedTask.getWorkerInstanceId());
        assertNotNull(savedTask.getResultPayloadComponent().get_jsonMap());

        JobCompletionProcessorLike completion = new JobCompletionProcessorLike(scheduleStore, fireStore, taskStore);
        completion.complete(prepared.fire.getJobFireId());

        NopJobFire savedFire = fireStore.loadFire(prepared.fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, savedFire.getFireStatus());
    }

    @Test
    public void testWorkerExecutesTaskFailure() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.ERROR(
                        new ErrorBean("JOB_TEST_ERROR").description("worker failed")
                ));
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask prepared = prepareWaitingTask("schedule-worker-2", "job-worker-2");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.scanOnce();

        NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_FAILED, savedTask.getTaskStatus());
        assertEquals("JOB_TEST_ERROR", savedTask.getErrorCode());

        JobCompletionProcessorLike completion = new JobCompletionProcessorLike(scheduleStore, fireStore, taskStore);
        completion.complete(prepared.fire.getJobFireId());

        NopJobFire savedFire = fireStore.loadFire(prepared.fire.getJobFireId());
        assertEquals(FIRE_STATUS_FAILED, savedFire.getFireStatus());
        assertEquals("JOB_TEST_ERROR", savedFire.getErrorCode());
    }

    private PreparedTask prepareWaitingTask(String scheduleId, String jobName) {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(scheduleId);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind(EXECUTOR_KIND_TEST);
        schedule.setExecutorKind("test");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        schedule.setLastFireTime(new Timestamp(now - 1000));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_RUNNING);
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_WAITING);
        task.getTaskPayloadComponent().set_jsonValue(Map.of("jobFireId", fire.getJobFireId()));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        return new PreparedTask(schedule, fire, task);
    }

    private void rememberOriginalBeanContainer() {
        if (originalBeanContainer == null && BeanContainer.isInitialized()) {
            originalBeanContainer = BeanContainer.instance();
        }
    }

    private static final class PreparedTask {
        private final NopJobSchedule schedule;
        private final NopJobFire fire;
        private final NopJobTask task;

        private PreparedTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.schedule = schedule;
            this.fire = fire;
            this.task = task;
        }
    }

    private static final class JobCompletionProcessorLike {
        private final IJobScheduleStore scheduleStore;
        private final IJobFireStore fireStore;
        private final IJobTaskStore taskStore;

        private JobCompletionProcessorLike(IJobScheduleStore scheduleStore, IJobFireStore fireStore,
                                           IJobTaskStore taskStore) {
            this.scheduleStore = scheduleStore;
            this.fireStore = fireStore;
            this.taskStore = taskStore;
        }

        private void complete(String fireId) {
            NopJobFire fire = fireStore.loadFire(fireId);
            NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
            NopJobTask task = taskStore.findTasksByFireId(fireId).get(0);
            fire.setFireStatus(task.getTaskStatus() == TASK_STATUS_SUCCESS ? FIRE_STATUS_SUCCESS : FIRE_STATUS_FAILED);
            fire.setStartTime(task.getStartTime());
            fire.setEndTime(task.getEndTime());
            fire.setDurationMs(task.getDurationMs());
            fire.setErrorCode(task.getErrorCode());
            fire.setErrorMessage(task.getErrorMessage());
            fire.setUpdatedBy("test");
            fire.setUpdateTime(task.getEndTime());
            schedule.setActiveFireCount(0);
            schedule.setLastEndTime(task.getEndTime());
            schedule.setLastFireStatus(fire.getFireStatus());
            schedule.setUpdatedBy("test");
            schedule.setUpdateTime(task.getEndTime());
            fireStore.completeFireAndUpdateSchedule(fire, schedule);
        }
    }
}
