/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.state;

import io.nop.api.core.beans.ErrorBean;
import io.nop.task.ITaskStateCommon;

import java.time.LocalDateTime;
import java.util.Set;

public abstract class AbstractTaskStateCommon implements ITaskStateCommon {
    private Boolean internal;
    private Integer retryAttempt;
    private Integer partitionIndex;
    private Integer partitionTotal;
    private Set<String> tags;
    private String bizObjName;
    private String bizObjKey;
    private String extType;
    private String extState;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Object resultValue;
    private ErrorBean error;

    @Override
    public Boolean getInternal() {
        return internal;
    }

    @Override
    public void setInternal(Boolean internal) {
        this.internal = internal;
    }

    @Override
    public Integer getRetryAttempt() {
        return retryAttempt;
    }

    @Override
    public void setRetryAttempt(Integer retryAttempt) {
        this.retryAttempt = retryAttempt;
    }

    @Override
    public Integer getPartitionIndex() {
        return partitionIndex;
    }

    @Override
    public void setPartitionIndex(Integer partitionIndex) {
        this.partitionIndex = partitionIndex;
    }

    @Override
    public Set<String> getTagSet() {
        return tags;
    }

    @Override
    public void setTagSet(Set<String> tags) {
        this.tags = tags;
    }

    @Override
    public String getBizObjName() {
        return bizObjName;
    }

    @Override
    public void setBizObjName(String bizObjName) {
        this.bizObjName = bizObjName;
    }

    @Override
    public String getBizObjId() {
        return bizObjKey;
    }

    @Override
    public void setBizObjId(String bizObjKey) {
        this.bizObjKey = bizObjKey;
    }

    @Override
    public String getExtType() {
        return extType;
    }

    @Override
    public void setExtType(String extType) {
        this.extType = extType;
    }

    @Override
    public String getExtState() {
        return extState;
    }

    @Override
    public void setExtState(String extState) {
        this.extState = extState;
    }

    @Override
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    @Override
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public Object getResultValue() {
        return resultValue;
    }

    @Override
    public void setResultValue(Object resultValue) {
        this.resultValue = resultValue;
    }

    public ErrorBean getError() {
        return error;
    }

    public void setError(ErrorBean error) {
        this.error = error;
    }
}