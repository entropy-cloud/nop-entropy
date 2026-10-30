package io.nop.job.coordinator.engine;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.NopJobApiConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.job.core.JobCoreErrors.ARG_SERVICE_NAME;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED;
import static io.nop.job.core.JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED;

/**
 * {@link IRpcPollTaskClient} 的**默认**实现：经平台 {@link IRpcServiceInvoker} 抽象
 * （ClusterRpcServiceInvoker 注册中心路由 / HttpRpcServiceInvoker urlMap 静态路由）
 * 调用 worker 的普通 BizModel 方法（默认 invokeJob / getJobStatus / cancelJob）。
 * <p>
 * **不绑定任何传输层**：类内无 HTTP 专属代码（不接触 IHttpClient/URL/状态码），
 * 传输由 {@code IRpcServiceInvoker} 的部署装配决定——因此命名为 Default 而非 Http。
 * <p>
 * 三个方法均为**异步**：整个方法体（参数组装 + RPC 调用 + 响应映射）经返回的
 * {@link CompletionStage} 交付，**不再阻塞等待** RPC 完成（此前用
 * {@code FutureHelper.syncGet} 同步取响应，单个挂起的 getJobStatus 会独占轮询
 * 线程——异步化后集中式 {@link RpcPollTaskManager} 可在同一注册表并发出多个在途调用）。
 * 同步校验类失败（如 serviceName 缺失）也经 future 失败透传，调用方只有一条错误通道。
 * <p>
 * 每次调用把传入的 {@link ICancelToken} 透传底层 {@code IRpcServiceInvoker.invokeAsync}——
 * token 在调用中途被取消时框架中止在途 RPC（与 DB 模式 {@code RpcJobInvoker} 一致）；
 * 为 null 时不传。
 * <p>
 * 每次调用注入 {@code nop-svc-target-host = task.targetHost} header 精确路由；
 * 状态查询与取消的载荷键统一为 {@code data.instanceId}（= DB jobTaskId）。
 */
public class DefaultRpcPollTaskClient implements IRpcPollTaskClient {
    static final Logger LOG = LoggerFactory.getLogger(DefaultRpcPollTaskClient.class);

    static final String DEFAULT_START_METHOD = "invokeJob";
    static final String DEFAULT_STATUS_METHOD = "getJobStatus";
    static final String DEFAULT_CANCEL_METHOD = "cancelJob";

    private IRpcServiceInvoker rpcServiceInvoker;
    private String startMethod = DEFAULT_START_METHOD;
    private String statusMethod = DEFAULT_STATUS_METHOD;
    private String cancelMethod = DEFAULT_CANCEL_METHOD;
    /**
     * check2 [P1-2]: getJobStatus/cancelJob 的单次 RPC 超时（HEADER_TIMEOUT，毫秒）。
     * 此前仅 startJob 注入超时头，poll/cancel 完全依赖 rpc 框架全局默认超时——网络分区下
     * 单个挂起的 getJobStatus 会独占轮询线程。默认 10s（poll 为轻量查询，应远短于任务执行
     * 超时）；{@code <=0} 表示不注入（回退旧行为）。
     */
    private long pollTimeoutMs = 10000;

    @Inject
    public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
        this.rpcServiceInvoker = rpcServiceInvoker;
    }

    @InjectValue("@cfg:nop.job.remote.poll-timeout-ms|10000")
    public void setPollTimeoutMs(long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }
    public void setStartMethod(String startMethod) {
        this.startMethod = startMethod;
    }

    public void setStatusMethod(String statusMethod) {
        this.statusMethod = statusMethod;
    }

    public void setCancelMethod(String cancelMethod) {
        this.cancelMethod = cancelMethod;
    }

    @Override
    public CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                            ICancelToken cancelToken) {
        // startJob 与 poll/cancel 共享 RpcPollRequest 冻结快照的请求构造路径（plan 2513 C4-001 ①(d)）
        RpcPollRequest poll = RpcPollRequest.of(schedule, fire, task);
        Map<String, Object> jobParams = poll.getJobParams();
        // futureCall：同步校验失败（如 serviceName 缺失）与 RPC 失败走同一条 future 错误通道，
        // 调用方无需区分同步/异步异常
        return FutureHelper.futureCall(() -> {
            String serviceName = requireServiceName(jobParams);

            ApiRequest<Object> request = new ApiRequest<>();
            injectFrameworkHeaders(request, poll);
            injectTargetHost(request, poll.getTargetHost());
            injectUserHeaders(request, jobParams);
            injectTimeoutHeader(request, poll.getTimeoutSeconds());

            // 载荷键统一约定：data.instanceId = DB taskId；业务参数（jobParams.data）原样透传并合并 instanceId
            Map<String, Object> data = new LinkedHashMap<>();
            Object userData = jobParams.get("data");
            if (userData instanceof Map) {
                data.putAll((Map<String, Object>) userData);
            } else if (userData != null) {
                data.put("data", userData);
            }
            data.put("instanceId", task.getJobTaskId());
            request.setData(data);

            // cancelToken 透传底层 RPC：token 中途取消时框架中止在途调用（与 RpcJobInvoker 一致）
            return rpcServiceInvoker.invokeAsync(serviceName, startMethod, request, cancelToken)
                    .thenApply(response -> {
                        // check2 [P3-4]: 非 ok 响应（远程异常经 ApiResponse 传回）必须携带远程 code/msg 抛出，
                        // 此前仅判 data==null 抛笼统 REMOTE_INVOKE_FAILED，远程错误细节丢失无法排障
                        if (!response.isOk()) {
                            throw new NopException(ERR_JOB_REMOTE_INVOKE_FAILED)
                                    .param("taskId", task.getJobTaskId())
                                    .param("responseCode", response.getCode())
                                    .param("responseMsg", response.getMsg());
                        }
                        Object result = response.getData();
                        if (result == null) {
                            throw new NopException(ERR_JOB_REMOTE_INVOKE_FAILED)
                                    .param("taskId", task.getJobTaskId());
                        }
                        return result instanceof String ? (String) result : String.valueOf(result);
                    });
        });
    }

    @Override
    public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                        ICancelToken cancelToken) {
        // plan 2513 C4-001 ①(d)：实体形态与窄快照形态共享同一条请求构造路径，请求契约不变
        return getJobStatus(RpcPollRequest.of(schedule, fire, task), cancelToken);
    }

    @Override
    public CompletionStage<TaskStatusBean> getJobStatus(RpcPollRequest request, ICancelToken cancelToken) {
        Map<String, Object> jobParams = request.getJobParams();
        return FutureHelper.futureCall(() -> {
            String serviceName = requireServiceName(jobParams);

            ApiRequest<Object> req = new ApiRequest<>();
            injectFrameworkHeaders(req, request);
            injectTargetHost(req, request.getTargetHost());
            injectPollTimeoutHeader(req);
            req.setData(Map.of("instanceId", request.getTaskId()));

            return rpcServiceInvoker.invokeAsync(serviceName, statusMethod, req, cancelToken)
                    .thenApply(response -> {
                        if (response.isOk() && response.getData() != null) {
                            Object data = response.getData();
                            if (data instanceof TaskStatusBean) {
                                return (TaskStatusBean) data;
                            }
                            return (TaskStatusBean) JsonTool.jsonObjectToBean(data, TaskStatusBean.class);
                        }
                        throw new NopException(ERR_JOB_REMOTE_INVOKE_FAILED)
                                .param("taskId", request.getTaskId());
                    });
        });
    }

    @Override
    public CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                             ICancelToken cancelToken) {
        // plan 2513 C4-001 ①(d)：实体形态与窄快照形态共享同一条请求构造路径，请求契约不变
        return cancelJob(RpcPollRequest.of(schedule, fire, task), cancelToken);
    }

    @Override
    public CompletionStage<Boolean> cancelJob(RpcPollRequest request, ICancelToken cancelToken) {
        Map<String, Object> jobParams = request.getJobParams();
        return FutureHelper.futureCall(() -> {
            String serviceName = requireServiceName(jobParams);

            ApiRequest<Object> req = new ApiRequest<>();
            injectFrameworkHeaders(req, request);
            injectTargetHost(req, request.getTargetHost());
            injectPollTimeoutHeader(req);
            req.setData(Map.of("instanceId", request.getTaskId()));

            return rpcServiceInvoker.invokeAsync(serviceName, cancelMethod, req, cancelToken)
                    .thenApply(response -> response.isOk());
        });
    }

    private static String requireServiceName(Map<String, Object> jobParams) {
        Object value = jobParams.get("serviceName");
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new NopException(ERR_JOB_SERVICE_NAME_REQUIRED)
                    .param(ARG_SERVICE_NAME, value);
        }
        return (String) value;
    }

    private static void injectFrameworkHeaders(ApiRequest<Object> request, RpcPollRequest poll) {
        if (poll.getJobName() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_NAME, poll.getJobName());
        }
        if (poll.getGroupId() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_GROUP, poll.getGroupId());
        }
        if (poll.getFireId() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_FIRE_ID, poll.getFireId());
        }
        request.setHeader(NopJobApiConstants.HEADER_JOB_TASK_ID, poll.getTaskId());
        if (poll.getShardingIndex() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_SHARDING_INDEX, poll.getShardingIndex());
        }
        if (poll.getShardingTotal() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_SHARDING_TOTAL, poll.getShardingTotal());
        }
        if (poll.getExecCount() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_EXEC_COUNT, poll.getExecCount());
        }
        if (poll.getScheduledFireTime() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_SCHEDULED_FIRE_TIME, poll.getScheduledFireTime());
        }
    }

    private static void injectTargetHost(ApiRequest<Object> request, String targetHost) {
        if (targetHost != null && !targetHost.isBlank()) {
            request.setHeader(ApiConstants.HEADER_SVC_TARGET_HOST, targetHost);
        }
    }

    private static void injectUserHeaders(ApiRequest<Object> request, Map<String, Object> jobParams) {
        Object headers = jobParams.get("headers");
        if (headers instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) headers).entrySet()) {
                request.setHeader(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
    }

    private static void injectTimeoutHeader(ApiRequest<Object> request, Integer timeoutSeconds) {
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            request.setHeader(ApiConstants.HEADER_TIMEOUT, timeoutSeconds * 1000L);
        }
    }

    /** check2 [P1-2]: poll/cancel 的独立较短超时（与 startJob 的任务级超时解耦）。 */
    private void injectPollTimeoutHeader(ApiRequest<Object> request) {
        if (pollTimeoutMs > 0) {
            request.setHeader(ApiConstants.HEADER_TIMEOUT, pollTimeoutMs);
        }
    }
}