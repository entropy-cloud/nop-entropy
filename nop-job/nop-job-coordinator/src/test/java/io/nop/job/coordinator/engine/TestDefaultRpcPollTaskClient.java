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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254: DefaultRpcPollTaskClient（默认 IRpcServiceInvoker 实现，不绑定 HTTP）三方法
 * （startJob/getJobStatus/cancelJob）请求契约测试——serviceName/方法名/data.instanceId 载荷键/
 * nop-svc-target-host 精确路由 header/框架 headers，以及 ICancelToken 透传底层
 * IRpcServiceInvoker（与 RpcJobInvoker 一致）。
 * 三方法均为异步（返回 {@link CompletionStage}）：结果/异常统一从 future 取，调用方无同步抛错路径。
 */
public class TestDefaultRpcPollTaskClient {

    private NopJobTask task;
    private NopJobFire fire;
    private NopJobSchedule schedule;
    private RecordingInvoker invoker;
    private DefaultRpcPollTaskClient client;
    private ICancelToken token;

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
        client = new DefaultRpcPollTaskClient();
        client.setRpcServiceInvoker(invoker);
        token = new NoOpCancelToken();
    }

    @Test
    void testStartJob_contract() {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("serviceName", "myWorker");
        jobParams.put("data", Map.of("url", "http://x"));
        task.setTaskPayload(JsonTool.stringify(jobParams));

        String result = await(client.startJob(schedule, fire, task, token));

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

        NopException e = awaitError(client.startJob(schedule, fire, task, token));

        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(), e.getErrorCode());
    }

    /**
     * check2 [P3-4]: 非 ok 响应时抛出的异常必须携带远程返回的 code/msg（排障信息），
     * 此前只带 taskId 的笼统 REMOTE_INVOKE_FAILED，远程错误细节丢失。
     */
    @Test
    void testStartJob_errorResponse_carriesRemoteCodeAndMsg() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildError(new ErrorBean("nop.err.worker.busy").description("worker is busy"));

        NopException e = awaitError(client.startJob(schedule, fire, task, token));

        assertEquals("nop.err.worker.busy", e.getParam("responseCode"),
                "remote response code must be propagated for troubleshooting");
        assertEquals("worker is busy", e.getParam("responseMsg"),
                "remote response msg must be propagated for troubleshooting");
    }

    @Test
    void testStartJob_missingServiceName_fails() {
        task.setTaskPayload(JsonTool.stringify(Map.of()));

        NopException e = awaitError(client.startJob(schedule, fire, task, token));

        assertEquals(JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void testGetJobStatus_contract() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        TaskStatusBean status = new TaskStatusBean();
        status.setTaskStatus(TaskStatusBean.STATUS_RUNNING);
        invoker.response = ApiResponse.buildSuccess(status);

        TaskStatusBean result = await(client.getJobStatus(schedule, fire, task, token));

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

        TaskStatusBean result = await(client.getJobStatus(schedule, fire, task, token));

        assertEquals(TaskStatusBean.STATUS_SUCCESS, result.getTaskStatus());
    }

    @Test
    void testCancelJob_contract() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(Boolean.TRUE);

        boolean cancelled = await(client.cancelJob(schedule, fire, task, token));

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

        boolean cancelled = await(client.cancelJob(schedule, fire, task, token));

        assertFalse(cancelled);
    }

    @Test
    void testNoTargetHost_noHeaderInjected() {
        task.setTargetHost(null);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));

        await(client.startJob(schedule, fire, task, token));

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

        await(client.getJobStatus(schedule, fire, task, token));

        assertEquals(8000L, invoker.lastRequest.getHeader(ApiConstants.HEADER_TIMEOUT),
                "getJobStatus must inject nop.job.remote.poll-timeout-ms as HEADER_TIMEOUT");
    }

    @Test
    void testCancelJob_injectsPollTimeoutHeader() {
        client.setPollTimeoutMs(8000);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(Boolean.TRUE);

        await(client.cancelJob(schedule, fire, task, token));

        assertEquals(8000L, invoker.lastRequest.getHeader(ApiConstants.HEADER_TIMEOUT),
                "cancelJob must inject nop.job.remote.poll-timeout-ms as HEADER_TIMEOUT");
    }

    /** poll-timeout-ms <= 0 表示禁用注入（回退旧行为，依赖 rpc 全局默认超时）。 */
    @Test
    void testPollTimeoutDisabled_noHeaderInjected() {
        client.setPollTimeoutMs(0);
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        invoker.response = ApiResponse.buildSuccess(Boolean.TRUE);

        await(client.cancelJob(schedule, fire, task, token));

        assertNull(invoker.lastRequest.getHeader(ApiConstants.HEADER_TIMEOUT),
                "pollTimeoutMs <= 0 must not inject HEADER_TIMEOUT");
    }

    /**
     * ICancelToken 契约：三方法接收的 cancelToken 必须原样透传底层 IRpcServiceInvoker
     * （与 DB 模式 RpcJobInvoker 一致）——token 在途取消时框架能中止对应 RPC。
     */
    @Test
    void testCancelToken_passedThroughToRpcInvoker() {
        task.setTaskPayload(JsonTool.stringify(Map.of("serviceName", "myWorker")));
        TaskStatusBean bean = new TaskStatusBean();
        bean.setTaskStatus(TaskStatusBean.STATUS_RUNNING);
        invoker.response = ApiResponse.buildSuccess(bean);

        await(client.startJob(schedule, fire, task, token));
        assertSame(token, invoker.lastCancelToken, "startJob must pass cancelToken to IRpcServiceInvoker");

        await(client.getJobStatus(schedule, fire, task, token));
        assertSame(token, invoker.lastCancelToken, "getJobStatus must pass cancelToken to IRpcServiceInvoker");

        await(client.cancelJob(schedule, fire, task, token));
        assertSame(token, invoker.lastCancelToken, "cancelJob must pass cancelToken to IRpcServiceInvoker");
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("timed out waiting for async result", e);
        }
    }

    private static NopException awaitError(CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof NopException, "expected NopException, got " + cause);
            return (NopException) cause;
        } catch (Exception e) {
            throw new IllegalStateException("timed out waiting for async failure", e);
        }
        throw new AssertionError("expected async failure, but stage completed successfully");
    }

    static class RecordingInvoker implements IRpcServiceInvoker {
        volatile String lastServiceName;
        volatile String lastMethod;
        volatile ApiRequest<Object> lastRequest;
        volatile ICancelToken lastCancelToken;
        ApiResponse<?> response = ApiResponse.buildSuccess("task-1");

        @Override
        public CompletableFuture<ApiResponse<?>> invokeAsync(String serviceName, String methodName,
                                                             ApiRequest<?> request, ICancelToken cancelToken) {
            lastServiceName = serviceName;
            lastMethod = methodName;
            lastRequest = (ApiRequest<Object>) request;
            lastCancelToken = cancelToken;
            return CompletableFuture.completedFuture(response);
        }
    }

    /** 取消令牌透传验证：三方法必须把传入的 ICancelToken 原样交给底层 IRpcServiceInvoker。 */
    static final class NoOpCancelToken implements ICancelToken {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public String getCancelReason() {
            return null;
        }

        @Override
        public void appendOnCancel(java.util.function.Consumer<String> task) {
        }

        @Override
        public void removeOnCancel(java.util.function.Consumer<String> task) {
        }
    }
}