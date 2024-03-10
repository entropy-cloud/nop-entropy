/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.nop.api.core.beans.ErrorBean;

import java.util.HashMap;
import java.util.Map;

/**
 * 记录Trigger的执行状态。一个Job只关联一条TriggerState记录。
 */
@JsonSubTypes(@JsonSubTypes.Type(TriggerState.class))
public interface ITriggerState {
    /**
     * 用于起标识作用的唯一ID
     */
    String getJobName();

    long getJobVersion();

    long getEpoch();

    /**
     * 已经执行过的次数
     */
    long getExecutionCount();

    /**
     * 如果大于0，则表示在此时间之前不会执行
     */
    long getMinScheduleTime();

    /**
     * 如果大于0，则表示在此时间之后应停止执行
     */
    long getMaxScheduleTime();

    /**
     * 如果大于0，则表示执行次数超过这个次数之后，则不再执行
     */
    long getMaxExecutionCount();

    /**
     * 下次执行时间
     */
    long getNextScheduleTime();

    /**
     * 执行完毕之后需要切换到的目标状态。如果为空，则切换到SCHEDULING，继续调度下一次执行
     */
    TriggerStatus getNextTriggerStatus();

    boolean isRecoverMode();

    long getRecoverTime();

    /**
     * 如果大于0，则表示首次实际执行的开始时间
     */
    long getFirstExecutionTime();

    /**
     * 上一次调度的预计执行时间。返回0, 如果此前没有执行过
     */
    long getLastScheduleTime();

    /**
     * 每一次执行都会分配一个唯一id。这里记录最近一次执行的id
     */
    String getLastExecutionId();

    /**
     * 上一次的实际执行时间。返回0, 如果此前没有执行过。从调度到实际实行可能有一点时间差
     */
    long getLastExecutionStartTime();

    /**
     * 上次执行的结束时间。返回0, 如果此前没有执行过或者当前执行尚未结束
     */
    long getLastExecutionEndTime();

    long getCompletionTime();

    int getMaxFailedCount();

    /**
     * 执行失败次数
     */
    long getTotalFailedCount();

    /**
     * 连续失败次数
     */
    long getConsecutiveFailedCount();

    TriggerStatus getTriggerStatus();

    @JsonIgnore
    default boolean isDone() {
        return getTriggerStatus().isDone();
    }

    @JsonIgnore
    default boolean isActive() {
        return getTriggerStatus().isActive();
    }

    ErrorBean getLastError();

    Map<String, Object> getAttributes();

    void setAttributes(Map<String, Object> attributes);

    default Object getAttribute(String name) {
        Map<String, Object> attrs = getAttributes();
        if (attrs == null)
            return null;
        return attrs.get(name);
    }

    default void setAttribute(String name, Object value) {
        Map<String, Object> attrs = getAttributes();
        if (attrs == null) {
            attrs = new HashMap<>();
            setAttributes(attrs);
        }
        attrs.put(name, value);
    }
}