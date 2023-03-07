/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;


import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopRebuildException;

import java.io.Serializable;

@DataBean
public class AsyncStepResult implements Serializable {
    private static final long serialVersionUID = 6502001395245004110L;

    private String stepId;
    private String runId;
    private Object returnValue;
    private ErrorBean error;
    private transient Throwable exception;

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public ErrorBean getError() {
        return error;
    }

    public void setError(ErrorBean error) {
        this.error = error;
    }

    public Throwable exception() {
        if (exception != null)
            return exception;
        if (error != null)
            exception = NopRebuildException.rebuild(error);
        return exception;
    }
}