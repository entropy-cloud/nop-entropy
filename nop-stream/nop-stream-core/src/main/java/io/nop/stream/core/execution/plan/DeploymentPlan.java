/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.plan;

import java.io.Serializable;
import java.util.*;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.MemoryBudget;

@DataBean
public class DeploymentPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String jobId;
    private final String pipelineId;
    private final PartitionedPlan partitionedPlan;
    private final String transportBackend;
    private final String stateBackendBinding;
    private final String checkpointStorage;
    private final Map<String, EdgeConfig> edgeConfigs;
    private final MemoryBudget memoryBudget;

    public DeploymentPlan(String jobId, String pipelineId,
                          PartitionedPlan partitionedPlan,
                          String transportBackend,
                          String stateBackendBinding,
                          String checkpointStorage,
                          Map<String, EdgeConfig> edgeConfigs,
                          MemoryBudget memoryBudget) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.partitionedPlan = partitionedPlan;
        this.transportBackend = transportBackend;
        this.stateBackendBinding = stateBackendBinding;
        this.checkpointStorage = checkpointStorage;
        this.edgeConfigs = edgeConfigs != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(edgeConfigs)) : Collections.emptyMap();
        this.memoryBudget = memoryBudget;
    }

    public DeploymentPlan() {
        this(null, null, null, "local", "memory", "local", null, null);
    }

    public String getJobId() { return jobId; }
    public String getPipelineId() { return pipelineId; }
    public PartitionedPlan getPartitionedPlan() { return partitionedPlan; }
    public String getTransportBackend() { return transportBackend; }
    public String getStateBackendBinding() { return stateBackendBinding; }
    public String getCheckpointStorage() { return checkpointStorage; }
    public Map<String, EdgeConfig> getEdgeConfigs() { return edgeConfigs; }
    public MemoryBudget getMemoryBudget() { return memoryBudget; }
}
