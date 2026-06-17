package io.nop.job.api.config;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.job.api.spec.TriggerSpec;

import java.util.Map;

@DataBean
public class LocalJobConfig {
    private String jobName;
    private String displayName;
    private String description;
    private String jobGroup;
    private TriggerSpec trigger;
    private LocalInvokerConfig invoker;
    private Map<String, Object> params;
    private boolean onceTask;

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
    }

    public TriggerSpec getTrigger() {
        return trigger;
    }

    public void setTrigger(TriggerSpec trigger) {
        this.trigger = trigger;
    }

    public LocalInvokerConfig getInvoker() {
        return invoker;
    }

    public void setInvoker(LocalInvokerConfig invoker) {
        this.invoker = invoker;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public boolean isOnceTask() {
        return onceTask;
    }

    public void setOnceTask(boolean onceTask) {
        this.onceTask = onceTask;
    }
}
