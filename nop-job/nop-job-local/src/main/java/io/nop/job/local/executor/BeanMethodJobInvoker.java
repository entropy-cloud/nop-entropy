package io.nop.job.local.executor;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.convert.ConvertHelper;
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

/**
 * 通过反射调用容器中 bean 的方法来执行 / 取消 job。
 * <p>
 * 正向 {@link #invokeAsync(IJobExecutionContext)} 与反向 {@link #cancelAsync(IJobExecutionContext)} 共享
 * bean 解析、方法解析、参数提取、反射调用等逻辑，仅差异在：
 * <ul>
 *     <li>默认方法名：正向 {@code execute}（可由 {@code methodName} 覆盖），
 *     反向 {@code cancel}（可由 {@code cancelMethodName} 覆盖）。</li>
 *     <li>异常策略：正向把方法调用异常包装为 {@link JobFireResult#ERROR(ErrorBean)}，
 *     bean/方法解析失败时仍抛出以便上层处理；反向为 best-effort，任何失败均记 warn 后返回 {@code true}，
 *     以与 {@code DefaultJobCancelHandler} 的兜底语义保持一致。</li>
 *     <li>结果映射：正向 {@code ->} {@link JobFireResult}，反向 {@code ->} {@code Boolean}（按 truthy 规则转换）。</li>
 * </ul>
 */
public class BeanMethodJobInvoker implements IJobInvoker {
    static final Logger LOG = LoggerFactory.getLogger(BeanMethodJobInvoker.class);

    static final String PARAM_BEAN_NAME = "beanName";
    static final String PARAM_METHOD_NAME = "methodName";
    static final String PARAM_CANCEL_METHOD_NAME = "cancelMethodName";
    static final String DEFAULT_EXECUTE_METHOD = "execute";
    static final String DEFAULT_CANCEL_METHOD = "cancel";

    private static final String ERR_BEAN_METHOD_INVOKE_FAILED = "nop.err.job.bean-method-invoke-failed";

    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> params = getParams(jobCtx);
        String beanName = requireString(params, PARAM_BEAN_NAME);
        String methodName = getMethodName(params, DEFAULT_EXECUTE_METHOD, PARAM_METHOD_NAME);
        Object bean = requireBean(beanName);
        Map<String, Object> methodParams = extractMethodParams(params);
        IFunctionModel fn = resolveMethod(bean, methodName, methodParams);

        return invokeReflective(fn, bean, methodParams)
                .thenApply(this::toJobFireResultSync)
                .exceptionally(e -> {
                    LOG.error("nop.job.bean-method-invoke-failed:beanName={},methodName={}",
                            beanName, methodName, e);
                    return JobFireResult.ERROR(new ErrorBean(ERR_BEAN_METHOD_INVOKE_FAILED)
                            .description(e.getMessage()));
                });
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        String beanName = null;
        IFunctionModel fn;
        Object bean;
        Map<String, Object> methodParams;
        try {
            Map<String, Object> params = getParams(jobCtx);
            beanName = getOptionalString(params, PARAM_BEAN_NAME);
            bean = beanName == null ? null : BeanContainer.tryGetBean(beanName);
            if (bean == null) {
                LOG.debug("nop.job.bean-method-cancel-skip-no-bean:beanName={}", beanName);
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
            String methodName = getMethodName(params, DEFAULT_CANCEL_METHOD, PARAM_CANCEL_METHOD_NAME);
            methodParams = extractMethodParams(params);
            fn = resolveMethod(bean, methodName, methodParams);
        } catch (Exception e) {
            LOG.warn("nop.job.bean-method-cancel-resolve-failed:beanName={}", beanName, e);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        String resolvedBeanName = beanName;
        return invokeReflective(fn, bean, methodParams)
                .handle((result, err) -> {
                    if (err != null) {
                        LOG.warn("nop.job.bean-method-cancel-failed:beanName={}", resolvedBeanName, err);
                        return Boolean.TRUE;
                    }
                    // void/null result means cancel completed without explicit failure
                    if (result == null) {
                        return Boolean.TRUE;
                    }
                    // Boolean / non-zero values = truthy. Explicit false/0 = cancel genuinely failed.
                    return ConvertHelper.toTruthy(result);
                });
    }

    CompletionStage<Object> invokeReflective(IFunctionModel fn, Object bean, Map<String, Object> methodParams) {
        try {
            return toCompletionStage(invokeMethod(fn, bean, methodParams));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @SuppressWarnings("unchecked")
    CompletionStage<Object> toCompletionStage(Object result) {
        if (result instanceof CompletionStage) {
            return (CompletionStage<Object>) result;
        }
        return CompletableFuture.completedFuture(result);
    }

    JobFireResult toJobFireResultSync(Object result) {
        if (result instanceof JobFireResult)
            return (JobFireResult) result;
        return JobFireResult.CONTINUE(-1);
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
        result.remove(PARAM_CANCEL_METHOD_NAME);
        return result;
    }

    private Object requireBean(String beanName) {
        Object bean = BeanContainer.tryGetBean(beanName);
        if (bean == null) {
            throw new NopException(ERR_JOB_BEAN_NOT_FOUND)
                    .param(ARG_BEAN_NAME, beanName);
        }
        return bean;
    }

    private Map<String, Object> getParams(IJobExecutionContext jobCtx) {
        Map<String, Object> params = jobCtx.getJobParams();
        if (params == null) {
            params = Map.of();
        }
        return params;
    }

    private String getMethodName(Map<String, Object> params, String defaultMethod, String paramKey) {
        Object value = params.get(paramKey);
        if (value instanceof String && !((String) value).isBlank())
            return (String) value;
        return defaultMethod;
    }

    private String requireString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new NopException(ERR_RPC_INVOKER_MISSING_PARAM)
                    .param(ARG_PARAM_NAME, key);
        }
        return (String) value;
    }

    private String getOptionalString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof String && !((String) value).isBlank()) {
            return (String) value;
        }
        return null;
    }
}
