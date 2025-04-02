/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

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

    /**
     * 已经执行过的次数
     */
    long getExecutionCount();

    /**
     * 下次执行时间
     */
    long getNextScheduleTime();

    boolean isRecoverMode();

    long getRecoverTime();

    /**
     * 上一次调度的预计执行时间。返回0, 如果此前没有执行过
     */
    long getLastScheduleTime();

    /**
     * 每一次执行都会分配一个唯一id。这里记录最近一次执行的id
     */
    String getLastExecutionId();


    long getExecBeginTime();

    long getExecEndTime();

    long getLastExecEndTime();

    /**
     * 执行失败次数
     */
    long getExecFailCount();

    int getTriggerStatus();

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