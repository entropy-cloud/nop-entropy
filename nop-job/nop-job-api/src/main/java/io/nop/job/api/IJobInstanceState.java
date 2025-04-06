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
@JsonSubTypes(@JsonSubTypes.Type(JobInstanceState.class))
public interface IJobInstanceState {
    String getJobDefId();

    /**
     * 用于起标识作用的唯一ID
     */
    String getJobName();

    long getJobVersion();

    String getJobGroup();

    Map<String, Object> getJobParams();

    String getInstanceId();

    /**
     * 当前对应的执行次数，从1开始
     */
    long getExecCount();

    /**
     * 下次执行时间
     */
    long getScheduledExecTime();

    long getExecBeginTime();

    long getExecEndTime();

    boolean isOnceTask();

    boolean isManualFire();

    String getFiredBy();

    long getChangeVersion();

    /**
     * 执行失败次数
     */
    long getExecFailCount();

    int getInstanceStatus();

    ErrorBean getExecError();

    String getLastInstanceId();

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