package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.NopJobApiConstants;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.helper.JobTaskStateMachine;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254: RemoteDispatchScanner 两段式（轮询+认领）与 HttpRemoteTaskClient 契约测试。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestRemoteDispatchScanner extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
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

    // ---- fixtures ----

    private NopJobSchedule saveSchedule(String id) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(id + "-job");
        schedule.setDisplayName(id);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind("rpc");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(System.currentTimeMillis()));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        return schedule;
    }

    private NopJobFire saveFire(String id, NopJobSchedule schedule) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(id);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(System.currentTimeMillis()));
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING);
        fire.setExecutorKind("rpc");
        fire.setDispatchMode("remote");
        fire.setPartitionIndex((short) 1);
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(System.currentTimeMillis()));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);
        return fire;
    }

    private NopJobTask saveTask(String id, NopJobFire fire, int status, String workerId) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(id);
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(status);
        task.setPartitionIndex((short) 1);
        task.setWorkerInstanceId(workerId);
        task.setTargetHost("h1:8080");
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(System.currentTimeMillis()));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);
        return task;
    }

    private RemoteDispatchScanner newScanner(MockRemoteTaskClient client) {
        RemoteDispatchScanner scanner = new RemoteDispatchScanner();
        scanner.setTaskStore(taskStore);
        scanner.setFireStore(fireStore);
        scanner.setScheduleStore(scheduleStore);
        scanner.setRemoteTaskClient(client);
        scanner.setScanIntervalMs(5000);
        scanner.setBatchSize(100);
        scanner.setLockTimeoutMs(60000);
        return scanner;
    }

    // ---- mock ----

    static class MockRemoteTaskClient implements IRemoteTaskClient {
        final Map<String, TaskStatusBean> statuses = new HashMap<>();
        final List<String> startCalls = new ArrayList<>();
        final List<String> statusCalls = new ArrayList<>();
        boolean startFails;
        String failCode;

        @Override
        public String startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            startCalls.add(task.getJobTaskId());
            if (startFails) {
                throw new NopException(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED).param("taskId", task.getJobTaskId());
            }
            return task.getJobTaskId();
        }

        @Override
        public TaskStatusBean getJobStatus(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            statusCalls.add(task.getJobTaskId());
            TaskStatusBean status = statuses.get(task.getJobTaskId());
            if (status == null) {
                status = new TaskStatusBean();
                status.setTaskId(task.getJobTaskId());
                status.setTaskStatus(TaskStatusBean.STATUS_NOT_FOUND);
            }
            return status;
        }
    }

    // ---- tests ----

    @Test
    public void testClaimThenStartJobMarksRunning() {
        NopJobSchedule schedule = saveSchedule("rd-s1");
        NopJobFire fire = saveFire("rd-f1", schedule);
        saveTask("rd-t1", fire, _NopJobCoreConstants.TASK_STATUS_WAITING, "w1");

        MockRemoteTaskClient client = new MockRemoteTaskClient();
        RemoteDispatchScanner scanner = newScanner(client);
        scanner.scanOnce();

        assertEquals(1, client.startCalls.size());
        assertEquals("rd-t1", client.startCalls.get(0));
        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, taskStore.loadTask("rd-t1").getTaskStatus());
        // 归因被覆盖为认领的 coordinator（既有 tryLockTasksForExecute 语义）
        assertEquals(io.nop.api.core.config.AppConfig.hostId(), taskStore.loadTask("rd-t1").getWorkerInstanceId());
    }

    @Test
    public void testStartJobFailureMarksFailed() {
        NopJobSchedule schedule = saveSchedule("rd-s2");
        NopJobFire fire = saveFire("rd-f2", schedule);
        saveTask("rd-t2", fire, _NopJobCoreConstants.TASK_STATUS_WAITING, "w1");

        MockRemoteTaskClient client = new MockRemoteTaskClient();
        client.startFails = true;
        RemoteDispatchScanner scanner = newScanner(client);
        scanner.scanOnce();

        NopJobTask task = taskStore.loadTask("rd-t2");
        assertEquals(_NopJobCoreConstants.TASK_STATUS_FAILED, task.getTaskStatus());
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(), task.getErrorCode());
        assertNotNull(task.getErrorMessage());
    }

    @Test
    public void testPollWritesFinalStatus() {
        NopJobSchedule schedule = saveSchedule("rd-s3");
        NopJobFire fire = saveFire("rd-f3", schedule);
        NopJobTask running = saveTask("rd-t3", fire, _NopJobCoreConstants.TASK_STATUS_RUNNING, "w1");
        running.setStartTime(new Timestamp(System.currentTimeMillis() - 60_000));
        taskStore.updateTask(running);

        MockRemoteTaskClient client = new MockRemoteTaskClient();
        TaskStatusBean success = new TaskStatusBean();
        success.setTaskId("rd-t3");
        success.setTaskStatus(TaskStatusBean.STATUS_SUCCESS);
        success.setDetail("progress", 100);
        client.statuses.put("rd-t3", success);

        RemoteDispatchScanner scanner = newScanner(client);
        scanner.scanOnce();

        NopJobTask task = taskStore.loadTask("rd-t3");
        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUCCESS, task.getTaskStatus());
        assertNotNull(task.getEndTime());
        assertTrue(task.getDurationMs() >= 0);
    }

    @Test
    public void testPollNotFoundMarksFailed() {
        NopJobSchedule schedule = saveSchedule("rd-s4");
        NopJobFire fire = saveFire("rd-f4", schedule);
        NopJobTask running = saveTask("rd-t4", fire, _NopJobCoreConstants.TASK_STATUS_RUNNING, "w1");
        running.setStartTime(new Timestamp(System.currentTimeMillis() - 60_000));
        taskStore.updateTask(running);

        MockRemoteTaskClient client = new MockRemoteTaskClient(); // 无 status → NOT_FOUND

        RemoteDispatchScanner scanner = newScanner(client);
        scanner.scanOnce();

        NopJobTask task = taskStore.loadTask("rd-t4");
        assertEquals(_NopJobCoreConstants.TASK_STATUS_FAILED, task.getTaskStatus());
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST.getErrorCode(), task.getErrorCode());
    }

    @Test
    public void testPollRunningIgnoredAndLateResultDroppedOnConcurrentFinalize() {
        NopJobSchedule schedule = saveSchedule("rd-s5");
        NopJobFire fire = saveFire("rd-f5", schedule);
        NopJobTask running = saveTask("rd-t5", fire, _NopJobCoreConstants.TASK_STATUS_RUNNING, "w1");
        running.setStartTime(new Timestamp(System.currentTimeMillis() - 60_000));
        taskStore.updateTask(running);

        MockRemoteTaskClient client = new MockRemoteTaskClient();
        TaskStatusBean runningStatus = new TaskStatusBean();
        runningStatus.setTaskId("rd-t5");
        runningStatus.setTaskStatus(TaskStatusBean.STATUS_RUNNING);
        client.statuses.put("rd-t5", runningStatus);

        RemoteDispatchScanner scanner = newScanner(client);
        scanner.scanOnce();
        // RUNNING → 保持 RUNNING
        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, taskStore.loadTask("rd-t5").getTaskStatus());

        // 并发终结：任务被取消（CANCELED），worker 迟到返回 SUCCESS → 丢弃
        NopJobTask canceled = taskStore.loadTask("rd-t5");
        canceled.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_CANCELED);
        taskStore.updateTask(canceled);

        TaskStatusBean lateSuccess = new TaskStatusBean();
        lateSuccess.setTaskId("rd-t5");
        lateSuccess.setTaskStatus(TaskStatusBean.STATUS_SUCCESS);
        client.statuses.put("rd-t5", lateSuccess);
        scanner.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_CANCELED, taskStore.loadTask("rd-t5").getTaskStatus(),
                "late worker result must be dropped on concurrently-finalized task");
    }

    @Test
    public void testHttpClientContractHeadersAndData() {
        NopJobSchedule schedule = saveSchedule("rd-s6");
        schedule.setFireCount(7L);
        schedule.setTimeoutSeconds(120);

        NopJobFire fire = saveFire("rd-f6", schedule);
        NopJobTask task = saveTask("rd-t6", fire, _NopJobCoreConstants.TASK_STATUS_WAITING, "w1");
        task.setShardingIndex(2);
        task.setShardingTotal(4);

        RecordingRpcInvoker rpc = new RecordingRpcInvoker();
        HttpRemoteTaskClient client = new HttpRemoteTaskClient();
        client.setRpcServiceInvoker(rpc);

        Map<String, Object> jobParams = Map.of("serviceName", "worker-svc");
        fire.getJobParamsSnapshotComponent().set_jsonValue(jobParams);

        String taskId = client.startJob(schedule, fire, task);
        assertEquals("rd-t6", taskId);

        RecordingRpcInvoker.Call call = rpc.calls.get(0);
        assertEquals("worker-svc", call.serviceName);
        assertEquals("invokeJob", call.serviceMethod);
        // 框架 headers
        assertEquals("rd-t6", call.request.getHeader(NopJobApiConstants.HEADER_JOB_TASK_ID));
        assertEquals("rd-f6", call.request.getHeader(NopJobApiConstants.HEADER_JOB_FIRE_ID));
        assertEquals(7L, call.request.getHeader(NopJobApiConstants.HEADER_JOB_EXEC_COUNT));
        assertEquals(2, call.request.getHeader(NopJobApiConstants.HEADER_JOB_SHARDING_INDEX));
        assertEquals(4, call.request.getHeader(NopJobApiConstants.HEADER_JOB_SHARDING_TOTAL));
        // targetHost 精确路由 header
        assertEquals("h1:8080", call.request.getHeader(io.nop.api.core.ApiConstants.HEADER_SVC_TARGET_HOST));
        // 超时 header
        assertEquals(120_000L, call.request.getHeader(io.nop.api.core.ApiConstants.HEADER_TIMEOUT));
        // 载荷键：data.instanceId = DB taskId
        assertEquals("rd-t6", ((Map<?, ?>) call.request.getData()).get("instanceId"));

        // getJobStatus 契约
        TaskStatusBean status = client.getJobStatus(schedule, fire, task);
        assertNotNull(status);
        assertEquals(TaskStatusBean.STATUS_SUCCESS, status.getTaskStatus());
        assertEquals("getJobStatus", rpc.calls.get(1).serviceMethod);
        assertEquals("rd-t6", ((Map<?, ?>) rpc.calls.get(1).request.getData()).get("instanceId"));
    }

    static class RecordingRpcInvoker implements IRpcServiceInvoker {
        static class Call {
            String serviceName;
            String serviceMethod;
            ApiRequest<Object> request;
        }

        final List<Call> calls = new ArrayList<>();

        @Override
        public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                           ApiRequest<?> request, ICancelToken cancelToken) {
            Call call = new Call();
            call.serviceName = serviceName;
            call.serviceMethod = serviceMethod;
            call.request = (ApiRequest<Object>) request;
            calls.add(call);

            ApiResponse<Object> response = new ApiResponse<>();
            if (serviceMethod.equals("invokeJob")) {
                response.setData("rd-t6");
            } else {
                TaskStatusBean status = new TaskStatusBean();
                status.setTaskId("rd-t6");
                status.setTaskStatus(TaskStatusBean.STATUS_SUCCESS);
                response.setData(status);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(response);
        }
    }
}
