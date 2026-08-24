package io.nop.job.coordinator.engine;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.NopJobApiConstants;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254: HttpRpcPollTaskClient 三方法（startJob/getJobStatus/cancelJob）请求契约测试——
 * serviceName/方法名/data.instanceId 载荷键/nop-svc-target-host 精确路由 header/框架 headers。
 */
public class TestHttpRpcPollTaskClient {

    private NopJobTask task;
    private NopJobFire fire;
    private NopJobSchedule schedule;
    private RecordingInvoker invoker;
    private HttpRpcPollTaskClient client;

    @BeforeEach
    void setUp() {
        task = new NopJobTask();
        task.setJobTaskId("task-1");
        task.setJobFireId("fire-1");
        task.setTargetHost("worker-host-1");
        task.setShardingIndex(0);
        task.setShardingTotal(4);

        fire = new NopJobFire();
        fire.setJobFireId("fire-1");
        fire.setJobName("myJob");
        fire.setGroupId("myGroup");
        fire.setScheduledFireTime(new Timestamp(1700000000000L));

        schedule = new NopJobSchedule();
        schedule.setJobScheduleId("schedule-1");
        schedule.setFireCount(3L);
        schedule.setTimeoutSeconds(30);

        invoker = new RecordingInvoker();
        client = new HttpRpcPollTaskClient();
        client.setRpcServiceInvoker(invoker);
    }

    @Test
    void testStartJob_contract() {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("serviceName", "myWorker");
        jobParams.put("data", Map.of("url", "http://x"));
        task.setTaskPayload(JsonTool.stringify(jobParams));

        String result = client.startJob(schedule, fire, task);

        assertEquals("task-1", result);
        assertEquals("myWorker", invoker.lastServiceName);
        assertEquals("invokeJob", invoker.lastMethod);

        ApiRequest<Object> req = invoker.lastRequest;
        assertEquals("task-1", ((Map<?, ?>) req.getData()).get("instanceId"));
        assertEquals("http://x", ((Map<?, ?>) req.getData()).get("url"));
        assertEquals("worker-host-1", req.getHeader(ApiConstants.HEADER_SVC_TARGET_HOST));
        assertEquals("myJob", req.getHeader(NopJobApiConstants.HEADER_JOB_NAME));
        assertEquals("myGroup", req.getHeader(NopJobApiConstants.HEADER_JOB_GROUP));
        assertEquals("fire-1", req.getHeader(NopJobApiConstants.HEADER_JOB_FIRE_ID));
        assertEquals("task-1", req.getHeader(NopJobApiConstants.HEADER_JOB_TASK_ID));
        assertEquals(0, req.getHeader(NopJobApiConstants.HEADER_JOB_SHARDING_INDEX));
        assertEquals(4, req.getHeader(NopJobApiConstants.HEADER_JOB_SHARDING_TOTAL));
        assertEquals(3L, req.getHeader(NopJobApiConstants.HEADER_JOB_EXEC_COUNT));
        assertEquals(1700000000000L, req.getHeader(NopJobApiConstants.HEADER_JOB_SCHEDULED_FIRE_TIME));
        assertEquals(30000L, req.getHeader(ApiConstants.HEADER_TIMEOUT));
    }

    @Test
    void testStartJob_returnsNull_fails() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(null);

        assertThrows(NopException.class, () -> client.startJob(schedule, fire, task));
    }

    /**
     * check2 [P3-4]: 非 ok 响应时抛出的异常必须携带远程返回的 code/msg（排障信息），
     * 此前只带 taskId 的笼统 REMOTE_INVOKE_FAILED，远程错误细节丢失。
     */
    @Test
    void testStartJob_errorResponse_carriesRemoteCodeAndMsg() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildError(new ErrorBean("nop.err.worker.busy").description("worker is busy"));

        NopException e = assertThrows(NopException.class, () -> client.startJob(schedule, fire, task));
        assertEquals("nop.err.worker.busy", e.getParam("responseCode"),
                "remote response code must be propagated for troubleshooting");
        assertEquals("worker is busy", e.getParam("responseMsg"),
                "remote response msg must be propagated for troubleshooting");
    }

    @Test
    void testStartJob_missingServiceName_fails() {
        task.setTaskPayload(JsonTool.stringify(Map.of()));

        NopException e = assertThrows(NopException.class, () -> client.startJob(schedule, fire, task));
        assertEquals(JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void testGetJobStatus_contract() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        TaskStatusBean status = new TaskStatusBean();
        status.setTaskStatus(TaskStatusBean.STATUS_RUNNING);
        invoker.response = ApiResponse.buildSuccess(status);

        TaskStatusBean result = client.getJobStatus(schedule, fire, task);

        assertEquals(TaskStatusBean.STATUS_RUNNING, result.getTaskStatus());
        assertEquals("myWorker", invoker.lastServiceName);
        assertEquals("getJobStatus", invoker.lastMethod);
        assertEquals("task-1", ((Map<?, ?>) invoker.lastRequest.getData()).get("instanceId"));
        assertEquals("worker-host-1", invoker.lastRequest.getHeader(ApiConstants.HEADER_SVC_TARGET_HOST));
    }

    @Test
    void testGetJobStatus_mapsJsonObject() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        Map<String, Object> raw = new HashMap<>();
        raw.put("taskStatus", TaskStatusBean.STATUS_SUCCESS);
        invoker.response = ApiResponse.buildSuccess(raw);

        TaskStatusBean result = client.getJobStatus(schedule, fire, task);

        assertEquals(TaskStatusBean.STATUS_SUCCESS, result.getTaskStatus());
    }

    @Test
    void testCancelJob_contract() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(Boolean.TRUE);

        boolean cancelled = client.cancelJob(schedule, fire, task);

        assertTrue(cancelled);
        assertEquals("myWorker", invoker.lastServiceName);
        assertEquals("cancelJob", invoker.lastMethod);
        assertEquals("task-1", ((Map<?, ?>) invoker.lastRequest.getData()).get("instanceId"));
        assertEquals("worker-host-1", invoker.lastRequest.getHeader(ApiConstants.HEADER_SVC_TARGET_HOST));
    }

    @Test
    void testCancelJob_notOk_returnsFalse() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildError(new ErrorBean("ERR").description("boom"));

        boolean cancelled = client.cancelJob(schedule, fire, task);

        assertFalse(cancelled);
    }

    @Test
    void testNoTargetHost_noHeaderInjected() {
        task.setTargetHost(null);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));

        client.startJob(schedule, fire, task);

        assertNull(invoker.lastRequest.getHeader(ApiConstants.HEADER_SVC_TARGET_HOST));
    }

    /**
     * check2 [P1-2]: getJobStatus/cancelJob 必须注入独立的较短 poll 超时头（与 startJob 的
     * 任务级超时解耦）——否则单个挂起的 getJobStatus 依赖 rpc 框架全局默认超时，独占轮询线程。
     */
    @Test
    void testGetJobStatus_injectsPollTimeoutHeader() {
        client.setPollTimeoutMs(8000);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        TaskStatusBean bean = new TaskStatusBean();
        bean.setTaskStatus(TaskStatusBean.STATUS_RUNNING);
        invoker.response = ApiResponse.buildSuccess(bean);

        client.getJobStatus(schedule, fire, task);

        assertEquals(8000L, invoker.lastRequest.getHeader(ApiConstants.HEADER_TIMEOUT),
                "getJobStatus must inject nop.job.remote.poll-timeout-ms as HEADER_TIMEOUT");
    }

    @Test
    void testCancelJob_injectsPollTimeoutHeader() {
        client.setPollTimeoutMs(8000);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(Boolean.TRUE);

        client.cancelJob(schedule, fire, task);

        assertEquals(8000L, invoker.lastRequest.getHeader(ApiConstants.HEADER_TIMEOUT),
                "cancelJob must inject nop.job.remote.poll-timeout-ms as HEADER_TIMEOUT");
    }

    /** poll-timeout-ms <= 0 表示禁用注入（回退旧行为，依赖 rpc 全局默认超时）。 */
    @Test
    void testPollTimeoutDisabled_noHeaderInjected() {
        client.setPollTimeoutMs(0);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(Boolean.TRUE);

        client.cancelJob(schedule, fire, task);

        assertNull(invoker.lastRequest.getHeader(ApiConstants.HEADER_TIMEOUT),
                "pollTimeoutMs <= 0 must not inject HEADER_TIMEOUT");
    }

    static class RecordingInvoker implements IRpcServiceInvoker {
        volatile String lastServiceName;
        volatile String lastMethod;
        volatile ApiRequest<Object> lastRequest;
        ApiResponse<?> response = ApiResponse.buildSuccess("task-1");

        @Override
        public CompletableFuture<ApiResponse<?>> invokeAsync(String serviceName, String methodName,
                                                             ApiRequest<?> request, ICancelToken cancelToken) {
            lastServiceName = serviceName;
            lastMethod = methodName;
            lastRequest = (ApiRequest<Object>) request;
            return CompletableFuture.completedFuture(response);
        }
    }
}