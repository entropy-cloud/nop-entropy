package io.nop.task;

import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IEvalContext;

import java.util.Set;

public interface ITaskStepRuntime extends IEvalContext {
    ITaskRuntime getTaskRuntime();

    ICancelToken getCancelToken();

    void setCancelToken(ICancelToken cancelToken);

    default boolean isCancelled() {
        ICancelToken cancelToken = getCancelToken();
        return cancelToken != null && cancelToken.isCancelled();
    }

    default Object getBean(String beanName) {
        return getEvalScope().getBeanProvider().getBean(beanName);
    }

    default String getLocale() {
        return getTaskRuntime().getLocale();
    }

    default String getStepId() {
        return getState().getStepId();
    }

    default int getRunId() {
        return getState().getRunId();
    }

    default String getStepType() {
        return getState().getStepType();
    }

    /**
     * 类似于GraphQL的selection，外部可以传入输出选择集，即除了返回值之外，还有哪些可选的结果变量
     */
    Set<String> getOutputNames();

    void setOutputNames(Set<String> outputNames);

    default boolean isNeedOutput(String name) {
        Set<String> names = getOutputNames();
        return names == null || names.contains(name);
    }

    default Object getValue(String name) {
        return getEvalScope().getValue(name);
    }

    default Object getLocalValue(String name) {
        return getEvalScope().getLocalValue(name);
    }

    default Object getResult() {
        return getLocalValue(TaskConstants.VAR_RESULT);
    }

    default void setValue(String name, Object value) {
        getEvalScope().setLocalValue(name, value);
    }

    ITaskStepState getState();

    boolean isSupportPersist();

    default void setStateBean(Object stateBean) {
        getState().setStateBean(stateBean);
    }

    default <T> T getStateBean(Class<T> beanType) {
        return getState().getStateBean(beanType);
    }

    /**
     * 将当前状态保存到持久化存储中
     */
    void saveState();

    /**
     * 是否是中断后从历史状态恢复
     */
    boolean isRecoverMode();

    ITaskStepRuntime newStepRuntime(String stepName, String stepType, Set<String> persistVars);

}