package io.nop.job.api.config;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class LocalSchedulerConfig {
    private boolean enabled;
    private List<LocalJobConfig> jobs;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<LocalJobConfig> getJobs() {
        return jobs;
    }

    public void setJobs(List<LocalJobConfig> jobs) {
        this.jobs = jobs;
    }
}
