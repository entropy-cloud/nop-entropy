package io.nop.job.service.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.job.api.NopJobApiConstants;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.job.service.NopJobErrors.ARG_PARAM_NAME;
import static io.nop.job.service.NopJobErrors.ERR_RPC_INVOKER_MISSING_PARAM;

public class RpcJobInvoker implements IJobInvoker {

    private IRpcServiceInvoker rpcServiceInvoker;

    @Inject
    public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
        this.rpcServiceInvoker = rpcServiceInvoker;
    }

    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> jobParams = jobCtx.getJobParams();
        if (jobParams == null) {
            jobParams = Collections.emptyMap();
        }

        String serviceName = requireString(jobParams, "serviceName");
        String serviceMethod = getString(jobParams, "serviceMethod", "invokeJob");

        ApiRequest<Object> request = new ApiRequest<>();

        // Inject framework-level execution context as headers (always present)
        injectFrameworkHeaders(request, jobCtx);

        // User-configured headers (override framework defaults if needed)
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) jobParams.get("headers");
        if (headers != null) {
            request.addHeaders(headers);
        }

        // Body: business payload only
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) jobParams.get("data");
        if (data != null) {
            request.setData(data);
        } else {
            request.setData(Collections.emptyMap());
        }

        return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null)
                .thenApply(this::toJobFireResult);
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> jobParams = jobCtx.getJobParams();
        if (jobParams == null) {
            jobParams = Collections.emptyMap();
        }

        String serviceName = requireString(jobParams, "serviceName");
        String cancelMethod = getString(jobParams, "cancelMethod", "cancelJob");

        ApiRequest<Object> request = new ApiRequest<>();

        injectFrameworkHeaders(request, jobCtx);

        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) jobParams.get("headers");
        if (headers != null) {
            request.addHeaders(headers);
        }

        request.setData(Map.of(
                "instanceId", jobCtx.getInstanceId() != null ? jobCtx.getInstanceId() : ""
        ));

        return rpcServiceInvoker.invokeAsync(serviceName, cancelMethod, request, null)
                .thenApply(ApiResponse::isOk);
    }

    private void injectFrameworkHeaders(ApiRequest<Object> request, IJobExecutionContext jobCtx) {
        // Job identity
        if (jobCtx.getJobName() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_NAME, jobCtx.getJobName());
        }
        if (jobCtx.getJobGroup() != null) {
            request.setHeader(NopJobApiConstants.HEADER_JOB_GROUP, jobCtx.getJobGroup());
        }

        // Execution context IDs — stored in attributes by DefaultJobExecutionContextBuilder
        Map<String, Object> attrs = jobCtx.getAttributes();
        if (attrs != null) {
            Object fireId = attrs.get("jobFireId");
            if (fireId != null) {
                request.setHeader(NopJobApiConstants.HEADER_JOB_FIRE_ID, fireId);
            }
            Object taskId = attrs.get("jobTaskId");
            if (taskId != null) {
                request.setHeader(NopJobApiConstants.HEADER_JOB_TASK_ID, taskId);
            }
            Object shardingIndex = attrs.get("shardingIndex");
            if (shardingIndex != null) {
                request.setHeader(NopJobApiConstants.HEADER_JOB_SHARDING_INDEX, shardingIndex);
            }
            Object shardingTotal = attrs.get("shardingTotal");
            if (shardingTotal != null) {
                request.setHeader(NopJobApiConstants.HEADER_JOB_SHARDING_TOTAL, shardingTotal);
            }
        }
    }

    private JobFireResult toJobFireResult(ApiResponse<?> response) {
        if (response.isOk()) {
            return JobFireResult.CONTINUE(-1);
        }
        ErrorBean error = new ErrorBean(response.getStatus() + "." + response.getCode());
        error.setDescription(response.getMsg());
        return JobFireResult.ERROR(error);
    }

    private String requireString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new NopException(ERR_RPC_INVOKER_MISSING_PARAM)
                    .param(ARG_PARAM_NAME, key);
        }
        return (String) value;
    }

    private String getString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String && !((String) value).isBlank()) {
            return (String) value;
        }
        return defaultValue;
    }
}
