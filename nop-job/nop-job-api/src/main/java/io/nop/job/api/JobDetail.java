/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;

@DataBean
public class JobDetail {
    private JobSpec jobSpec;

    private TriggerState triggerState;

    public JobSpec getJobSpec() {
        return jobSpec;
    }

    public void setJobSpec(JobSpec jobSpec) {
        this.jobSpec = jobSpec;
    }

    public TriggerState getTriggerState() {
        return triggerState;
    }

    public void setTriggerState(TriggerState triggerState) {
        this.triggerState = triggerState;
    }

    @JsonIgnore
    public String getJobName() {
        return jobSpec == null ? null : jobSpec.getJobName();
    }

    @JsonIgnore
    public String getJobGroup() {
        return jobSpec == null ? null : jobSpec.getJobGroup();
    }

    @JsonIgnore
    public TriggerSpec getTriggerSpec() {
        return jobSpec == null ? null : jobSpec.getTriggerSpec();
    }
}
