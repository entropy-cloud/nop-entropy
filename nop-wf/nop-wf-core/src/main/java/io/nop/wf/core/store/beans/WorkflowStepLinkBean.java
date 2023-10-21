package io.nop.wf.core.store.beans;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class WorkflowStepLinkBean {
    private String wfId;
    private String stepId;
    private String nextStepId;
    private String execAction;

    public String getWfId() {
        return wfId;
    }

    public void setWfId(String wfId) {
        this.wfId = wfId;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getNextStepId() {
        return nextStepId;
    }

    public void setNextStepId(String nextStepId) {
        this.nextStepId = nextStepId;
    }

    public String getExecAction() {
        return execAction;
    }

    public void setExecAction(String execAction) {
        this.execAction = execAction;
    }
}
