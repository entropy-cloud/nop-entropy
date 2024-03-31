/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;


import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.exceptions.ErrorMessageManager;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@DataBean
public class StepResultBean implements Serializable {
    private static final long serialVersionUID = 6502001395245004110L;

    private String stepName;
    private String nextStepName;
    private Map<String, Object> outputs;
    private ErrorBean error;
    private transient Throwable exception;

    public static StepResultBean buildFrom(String stepName, String locale, CompletionStage<TaskStepReturn> future) {
        StepResultBean resultBean = new StepResultBean();
        resultBean.setStepName(stepName);
        try {
            TaskStepReturn result = FutureHelper.syncGet(future);
            resultBean.setNextStepName(result.getNextStepName());
            resultBean.setOutputs(result.get());
        } catch (Exception exception) {
            resultBean.setError(ErrorMessageManager.instance().buildErrorMessage(locale, exception));
        }
        return resultBean;
    }

    public static StepResultBean buildFromResult(String stepName, String locale, TaskStepReturn result) {
        StepResultBean resultBean = new StepResultBean();
        resultBean.setStepName(stepName);

        resultBean.setNextStepName(result.getNextStepName());
        resultBean.setOutputs(result.get());
        return resultBean;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getNextStepName() {
        return nextStepName;
    }

    public void setNextStepName(String nextStepName) {
        this.nextStepName = nextStepName;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }

    public Object getValue(String name) {
        if (outputs == null)
            return null;
        return outputs.get(name);
    }

    public ErrorBean getError() {
        return error;
    }

    public void setError(ErrorBean error) {
        this.error = error;
    }

    @JsonIgnore
    public boolean isSuccess() {
        return error == null;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public Throwable exception() {
        if (exception != null)
            return exception;
        if (error != null)
            exception = NopRebuildException.rebuild(error);
        return exception;
    }
}