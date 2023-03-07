/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;

import io.nop.api.core.beans.ErrorBean;
import io.nop.commons.lang.ITagSetSupport;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * ITaskState和ITaskStepState的公共部分
 */
public interface ITaskStateCommon extends ITagSetSupport {
    /**
     * 一般情况下internal的TaskInstance/StepInstance由系统内部自动生成，对外不可见
     *
     * @return
     */
    Boolean getInternal();

    void setInternal(Boolean value);

    /**
     * 标记本次执行是第几次重试。缺省retryAttempt=0。失败后重试retryAttempt逐渐增加
     *
     * @return
     */
    Integer getRetryAttempt();

    void setRetryAttempt(Integer retryAttempt);

    /**
     * 内置数据分区支持
     *
     * @return
     */
    Integer getPartitionIndex();

    void setPartitionIndex(Integer partitionIndex);

    /**
     * 允许自由扩展的分类标签
     *
     * @return
     */
    Set<String> getTagSet();

    void setTagSet(Set<String> tags);

    /**
     * 用于关联业务对象
     *
     * @return
     */
    String getBizObjId();

    void setBizObjId(String bizObjId);

    String getBizObjName();

    void setBizObjName(String bizObjName);

    /**
     * 自定义的任务分类
     *
     * @return
     */
    String getExtType();

    void setExtType(String value);

    /**
     * 自定义的状态标识
     *
     * @return
     */
    String getExtState();

    void setExtState(String value);

    LocalDateTime getCreateTime();

    void setCreateTime(LocalDateTime createTime);

    LocalDateTime getUpdateTime();

    void setUpdateTime(LocalDateTime updateTime);

    Object getResultValue();

    void setResultValue(Object resultValue);

    void result(TaskStepResult result);

    ErrorBean getError();

    void setError(ErrorBean error);
}