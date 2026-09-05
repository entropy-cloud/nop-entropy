/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import io.nop.commons.util.StringHelper;

/**
 * state变量相当于是通过闭包方式捕获了所有状态相关的变量集合，从而使得step能够以continuation的方式运行.
 * <p>
 * 持久化状态包含返回结果（正常返回值以及抛出的异常），因此重复执行时发现结果已存在，则直接返回结果，跳过具体执行过程。
 */
public interface ITaskStepState extends ITaskStateCommon {
    String getTaskInstanceId();

    String getStepInstanceId();

    void setTaskInstanceId(String taskInstanceId);

    void setStepInstanceId(String stepInstanceId);

    /**
     * stepName为静态定义的步骤name. runId为动态执行路径所确定的id. 两者结合在一起唯一确定一个TaskStepState
     */
    String getStepPath();

    void setStepPath(String stepPath);

    default String getStepName() {
        return StringHelper.lastPart(getStepPath(), '/');
    }

    /**
     * 在TaskInstance范围内动态执行路径所对应的id。例如循环嵌套会生成:3:2这种runId，它标识第一层循环执行到下标为3，第二层循环执行到下标为2
     */
    int getRunId();

    void setRunId(int runId);

    int getBodyStepIndex();

    void setBodyStepIndex(int bodyIndex);

    String getParentStepPath();

    void setParentStepPath(String parentStepPath);

    int getParentRunId();

    void setParentRunId(int parentRunId);

    String getStepType();

    void setStepType(String stepType);

    Integer getStepStatus();

    void setStepStatus(Integer stepStatus);

    /**
     * 分布式执行时对应执行节点
     */
    String getWorkerId();

    void setWorkerId(String workerId);

    /**
     * 步骤所用到的变量。如果把TaskStep看作是普通函数。则这里的vars对应于参数、闭包变量以及函数内部临时变量等所有变量的集合
     */
    <T> T getStateBean(Class<T> beanType);

    void setStateBean(Object stateBean);

    /**
     * 步骤终态成功时捕获的输出映射（plan 349 Phase 6）：持久化后供 continuation-skip 路径重放
     * exportAs/toTaskScope 导出变量，修复恢复后非 RESULT 导出变量丢失。缺省 null（实现未支持）。
     */
    default java.util.Map<String, Object> getOutputs() {
        return null;
    }

    default void setOutputs(java.util.Map<String, Object> outputs) {
        // 默认不支持：持久化 store 实现按需覆写
    }

    /**
     * 步骤终态成功时的动态跳转目标（plan 349 Phase 6）：与 resultValue 一并持久化，
     * 供 continuation-skip 路径恢复动态跳转语义。
     */
    default String getSavedNextStepName() {
        return null;
    }

    default void setSavedNextStepName(String nextStepName) {
        // 默认不支持
    }

    /**
     * xdef persistVars 声明的持久化变量快照（plan 349 Phase 6）：saveState 时捕获 scope 变量值，
     * load 后由 TaskStepExecution 回写父 scope，实现"标记为 persist 的变量支持中断后恢复执行"。
     */
    default java.util.Map<String, Object> getPersistVarsSnapshot() {
        return null;
    }

    default void setPersistVarsSnapshot(java.util.Map<String, Object> vars) {
        // 默认不支持
    }

    /**
     * 从持久化存储中恢复后调用
     */
    void afterLoad(ITaskRuntime taskRt);

    void beforeSave(ITaskRuntime taskRt);

    void succeed(Object result, String nextStepId, ITaskRuntime taskRt);

    void fail(Throwable exception, ITaskRuntime taskRt);

    boolean isDone();

    boolean isSuccess();

    boolean needSave();

    TaskStepReturn result();

    Throwable exception();

    void exception(Throwable exp);
}