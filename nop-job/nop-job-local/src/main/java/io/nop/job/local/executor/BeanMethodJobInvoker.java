package io.nop.job.local.executor;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
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
        String methodName = requireString(params, PARAM_METHOD_NAME);

        Object bean = BeanContainer.tryGetBean(beanName);
        if (bean == null) {
            throw new NopException(ERR_JOB_BEAN_NOT_FOUND)
                    .param(ARG_BEAN_NAME, beanName);
        }

        Map<String, Object> methodParams = extractMethodParams(params);

        Method method = resolveMethod(bean.getClass(), methodName, methodParams);

        try {
            invokeSafely(method, bean, methodParams);
        } catch (Exception e) {
            LOG.error("nop.job.bean-method-invoke-failed:beanName={},methodName={}", beanName, methodName, e);
            return CompletableFuture.completedFuture(
                    JobFireResult.ERROR(new ErrorBean(
                            "nop.err.job.bean-method-invoke-failed")
                            .description(e.getMessage())));
        }

        return CompletableFuture.completedFuture(JobFireResult.CONTINUE(-1));
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    Method resolveMethod(Class<?> clazz, String methodName, Map<String, Object> methodParams) {
        Method noArgMethod = findMethod(clazz, methodName, 0);
        Method withParamMethod = findMethod(clazz, methodName, 1);

        if (noArgMethod != null && (methodParams == null || methodParams.isEmpty())) {
            return noArgMethod;
        }

        if (withParamMethod != null
                && withParamMethod.getParameterTypes()[0].isAssignableFrom(Map.class)) {
            return withParamMethod;
        }

        if (noArgMethod != null) {
            return noArgMethod;
        }

        throw new NopException(ERR_JOB_METHOD_NOT_FOUND)
                .param(ARG_BEAN_NAME, clazz.getName())
                .param(ARG_METHOD_NAME, methodName);
    }

    private void invokeSafely(Method method, Object bean, Map<String, Object> methodParams) throws Exception {
        try {
            if (method.getParameterCount() == 0) {
                method.invoke(bean);
            } else {
                method.invoke(bean, methodParams);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private Method findMethod(Class<?> clazz, String methodName, int paramCount) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        return null;
    }

    Map<String, Object> extractMethodParams(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>(params);
        result.remove(PARAM_BEAN_NAME);
        result.remove(PARAM_METHOD_NAME);
        return result;
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
