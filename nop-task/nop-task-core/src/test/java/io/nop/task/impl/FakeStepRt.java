package io.nop.task.impl;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.xlang.api.XLang;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * plan 349 测试基础设施：基于动态代理的最小 {@link ITaskStepRuntime} / {@link ITaskRuntime} 桩，
 * 供聚焦单元测试（retry/timeout/wrapper 等）使用，避免搭建完整任务上下文。
 */
public final class FakeStepRt {
    private FakeStepRt() {
    }

    public static ITaskStepRuntime of(ITaskStepState state) {
        Map<String, Object> holders = new HashMap<>();
        return of(state, holders);
    }

    public static ITaskStepRuntime of(ITaskStepState state, Map<String, Object> holders) {
        return (ITaskStepRuntime) proxy(ITaskStepRuntime.class, (method, args) -> {
            switch (method.getName()) {
                case "getState":
                    return state;
                case "getStateBean":
                    return holders.get("stateBean");
                case "setStateBean":
                    holders.put("stateBean", args == null ? null : args[0]);
                    return null;
                case "saveState":
                case "setValue":
                case "setBodyStepIndex":
                case "setTagSet":
                case "setOutputNames":
                case "setCancelToken":
                case "setException":
                case "setRecoverMode":
                case "addStepCleanup":
                case "runStepCleanups":
                    return null;
                case "getEvalScope":
                    // 固定 scope 实例：buildErrorResult 写入 errorName 变量后断言需要读到同一 scope
                    return (IEvalScope) holders.computeIfAbsent("scope", k -> newScope());
                case "getTaskRuntime":
                    return fakeTaskRuntime();
                case "getStepPath":
                    return "/fake";
                case "getRunId":
                    return 0;
                case "isCancelled":
                    return holders.get("cancelToken") instanceof io.nop.api.core.util.ICancelToken
                            && ((io.nop.api.core.util.ICancelToken) holders.get("cancelToken")).isCancelled();
                case "getCancelToken":
                    return holders.get("cancelToken");
                case "newStepRuntime":
                    return holders.get("childStepRt");
                case "getTagSet":
                    return Set.of();
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    public static ITaskRuntime fakeTaskRuntime() {
        return (ITaskRuntime) proxy(ITaskRuntime.class, (method, args) -> {
            switch (method.getName()) {
                case "getTaskName":
                    return "fake-task";
                case "getTaskVersion":
                    return 0L;
                case "getTaskInstanceId":
                    return "fake-instance";
                case "getScheduledExecutor":
                    return GlobalExecutors.globalTimer();
                case "getThreadPoolExecutor":
                    return GlobalExecutors.globalWorker();
                case "getEvalScope":
                    return newScope();
                case "getMetrics":
                    return io.nop.task.metrics.EmptyTaskFlowMetrics.INSTANCE;
                case "isCancelled":
                    return false;
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    public static IEvalScope newScope() {
        return XLang.newEvalScope(new ConcurrentHashMap<>());
    }

    /** 最小 ITask 桩：newTaskRuntime 只消费 taskName/taskVersion。 */
    public static ITask fakeTask(String name) {
        return (ITask) proxy(ITask.class, (method, args) -> {
            switch (method.getName()) {
                case "getTaskName":
                    return name;
                case "getTaskVersion":
                    return 0L;
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0D;
        return null;
    }

    interface Handler {
        Object invoke(Method method, Object[] args) throws Throwable;
    }

    private static Object proxy(Class<?> iface, Handler handler) {
        return Proxy.newProxyInstance(FakeStepRt.class.getClassLoader(), new Class[]{iface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "toString":
                                return iface.getSimpleName() + "@fake";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return null;
                        }
                    }
                    return handler.invoke(method, args);
                });
    }
}
