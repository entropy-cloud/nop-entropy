package io.nop.job.local.executor;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.job.api.JobApiErrors.ARG_BEAN_NAME;
import static io.nop.job.api.JobApiErrors.ARG_METHOD_NAME;
import static io.nop.job.api.JobApiErrors.ARG_PARAM_NAME;
import static io.nop.job.api.JobApiErrors.ERR_JOB_BEAN_NOT_FOUND;
import static io.nop.job.api.JobApiErrors.ERR_JOB_METHOD_NOT_FOUND;
import static io.nop.job.api.JobApiErrors.ERR_RPC_INVOKER_MISSING_PARAM;

public class BeanMethodJobInvoker implements IJobInvoker {
    static final Logger LOG = LoggerFactory.getLogger(BeanMethodJobInvoker.class);

    static final String PARAM_BEAN_NAME = "beanName";
    static final String PARAM_METHOD_NAME = "methodName";

    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> params = jobCtx.getJobParams();
        if (params == null) {
            params = Map.of();
        }

        String beanName = requireString(params, PARAM_BEAN_NAME);
        String methodName = getMethodName(params);

        Object bean = BeanContainer.tryGetBean(beanName);
        if (bean == null) {
            throw new NopException(ERR_JOB_BEAN_NOT_FOUND)
                    .param(ARG_BEAN_NAME, beanName);
        }

        Map<String, Object> methodParams = extractMethodParams(params);

        IFunctionModel fn = resolveMethod(bean, methodName, methodParams);

        Object result;
        try {
            result = invokeMethod(fn, bean, methodParams);
        } catch (Exception e) {
            LOG.error("nop.job.bean-method-invoke-failed:beanName={},methodName={}", beanName, methodName, e);
            return CompletableFuture.completedFuture(
                    JobFireResult.ERROR(new ErrorBean(
                            "nop.err.job.bean-method-invoke-failed")
                            .description(e.getMessage())));
        }

        return toJobFireResult(result, beanName, methodName);
    }

    CompletionStage<JobFireResult> toJobFireResult(Object result, String beanName, String methodName) {
        if (result instanceof CompletionStage) {
            return ((CompletionStage<?>) result)
                    .thenApply(this::toJobFireResultSync)
                    .exceptionally(e -> {
                        LOG.error("nop.job.bean-method-invoke-failed:beanName={},methodName={}", beanName, methodName, e);
                        return JobFireResult.ERROR(new ErrorBean(
                                "nop.err.job.bean-method-invoke-failed")
                                .description(e.getMessage()));
                    });
        }
        return CompletableFuture.completedFuture(toJobFireResultSync(result));
    }

    JobFireResult toJobFireResultSync(Object result) {
        if (result instanceof JobFireResult)
            return (JobFireResult) result;
        return JobFireResult.CONTINUE(-1);
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    IFunctionModel resolveMethod(Object bean, String methodName, Map<String, Object> methodParams) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(bean.getClass());
        IMethodModelCollection methods = classModel.getMethodsByName(methodName);
        if (methods == null || methods.getMethods().isEmpty()) {
            throw new NopException(ERR_JOB_METHOD_NOT_FOUND)
                    .param(ARG_BEAN_NAME, bean.getClass().getName())
                    .param(ARG_METHOD_NAME, methodName);
        }

        IFunctionModel noArgFn = null;
        IFunctionModel singleMapFn = null;
        IFunctionModel otherFn = null;
        for (IFunctionModel m : methods.getMethods()) {
            if (m.getArgCount() == 0) {
                noArgFn = m;
            } else if (m.getArgCount() == 1) {
                IFunctionArgument arg = m.getArgs().get(0);
                if (Map.class.isAssignableFrom(arg.getRawClass())) {
                    singleMapFn = m;
                } else {
                    otherFn = m;
                }
            } else {
                otherFn = m;
            }
        }

        if (noArgFn != null && methodParams.isEmpty())
            return noArgFn;

        if (singleMapFn != null)
            return singleMapFn;

        if (otherFn != null)
            return otherFn;

        if (noArgFn != null)
            return noArgFn;

        throw new NopException(ERR_JOB_METHOD_NOT_FOUND)
                .param(ARG_BEAN_NAME, bean.getClass().getName())
                .param(ARG_METHOD_NAME, methodName);
    }

    Object invokeMethod(IFunctionModel fn, Object bean, Map<String, Object> methodParams) {
        if (fn.getArgCount() == 0) {
            return fn.call0(bean, DisabledEvalScope.INSTANCE);
        } else if (fn.getArgCount() == 1
                && Map.class.isAssignableFrom(fn.getArgs().get(0).getRawClass())) {
            return fn.call1(bean, methodParams, DisabledEvalScope.INSTANCE);
        } else {
            Object[] args = fn.buildArgValues(methodParams);
            return fn.invoke(bean, args, DisabledEvalScope.INSTANCE);
        }
    }

    Map<String, Object> extractMethodParams(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>(params);
        result.remove(PARAM_BEAN_NAME);
        result.remove(PARAM_METHOD_NAME);
        return result;
    }

    private String getMethodName(Map<String, Object> params) {
        Object value = params.get(PARAM_METHOD_NAME);
        if (value instanceof String && !((String) value).isBlank())
            return (String) value;
        return "execute";
    }

    private String requireString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new NopException(ERR_RPC_INVOKER_MISSING_PARAM)
                    .param(ARG_PARAM_NAME, key);
        }
        return (String) value;
    }
}
