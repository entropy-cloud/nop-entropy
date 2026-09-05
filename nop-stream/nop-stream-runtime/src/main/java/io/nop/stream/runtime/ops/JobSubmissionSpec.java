/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Item 16 (P-REQ-5): REST job submission descriptor. The submission object is
 * a FACTORY REFERENCE (Phase 1 decision): jobId + pipelineFactoryClass (FQCN
 * of a {@code ClusterPipelineFactory}) + launch parameters. The coordinator
 * process builds the pipeline via the factory and drives the existing
 * {@code start() -> assignTasks() -> deployTask} execution path — no
 * parallel submission mechanism.
 */
@DataBean
public class JobSubmissionSpec {

    private String jobId;
    private String pipelineFactoryClass;
    private Map<String, String> params = new LinkedHashMap<>();

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getPipelineFactoryClass() {
        return pipelineFactoryClass;
    }

    public void setPipelineFactoryClass(String pipelineFactoryClass) {
        this.pipelineFactoryClass = pipelineFactoryClass;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
