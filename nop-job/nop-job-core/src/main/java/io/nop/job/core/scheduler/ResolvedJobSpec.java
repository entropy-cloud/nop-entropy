/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.scheduler;

import io.nop.job.api.IJobInvoker;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.core.ITrigger;

import java.util.Map;

/**
 * Job引擎会根据JobSpec来创建jobInvoker和trigger。当JobSpec发生更新时，需要重新创建这些对象
 */
class ResolvedJobSpec {
    private final JobSpec jobSpec;
    private final IJobInvoker jobInvoker;
    private final ITrigger trigger;

    public ResolvedJobSpec(JobSpec jobSpec, IJobInvoker jobInvoker, ITrigger trigger) {
        this.jobSpec = jobSpec;
        this.jobInvoker = jobInvoker;
        this.trigger = trigger;
    }

    public boolean isRemoveWhenDone() {
        return jobSpec.isRemoveWhenDone();
    }

    public String getJobName() {
        return jobSpec.getJobName();
    }

    public Map<String, Object> getJobParams() {
        return jobSpec.getJobParams();
    }

    public JobSpec getJobSpec() {
        return jobSpec;
    }

    public IJobInvoker getJobInvoker() {
        return jobInvoker;
    }

    public ITrigger getTrigger() {
        return trigger;
    }
}
