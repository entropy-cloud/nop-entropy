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

import io.nop.stream.core.model.StreamModelFingerprint;

@DataBean
public class PartitionedPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String jobId;
    private final String pipelineId;
    private final Map<String, VertexPlan> vertexPlans;
    private final List<EdgePlan> edgePlans;
    private final Set<String> checkpointAckSet;
    private final StreamModelFingerprint fingerprint;

    public PartitionedPlan(String jobId, String pipelineId,
                           Map<String, VertexPlan> vertexPlans,
                           List<EdgePlan> edgePlans,
                           Set<String> checkpointAckSet,
                           StreamModelFingerprint fingerprint) {
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.vertexPlans = vertexPlans != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(vertexPlans)) : Collections.emptyMap();
        this.edgePlans = edgePlans != null
                ? Collections.unmodifiableList(new ArrayList<>(edgePlans)) : Collections.emptyList();
        this.checkpointAckSet = checkpointAckSet != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(checkpointAckSet)) : Collections.emptySet();
        this.fingerprint = fingerprint;
    }

    public PartitionedPlan() {
        this(null, null, null, null, null, null);
    }

    public String getJobId() { return jobId; }
    public String getPipelineId() { return pipelineId; }
    public Map<String, VertexPlan> getVertexPlans() { return vertexPlans; }
    public List<EdgePlan> getEdgePlans() { return edgePlans; }
    public Set<String> getCheckpointAckSet() { return checkpointAckSet; }
    public StreamModelFingerprint getFingerprint() { return fingerprint; }

    @DataBean
    public static class VertexPlan implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String vertexId;
        private final int parallelism;
        private final String operatorId;

        public VertexPlan(String vertexId, int parallelism, String operatorId) {
            this.vertexId = vertexId;
            this.parallelism = parallelism;
            this.operatorId = operatorId;
        }

        public VertexPlan() { this(null, 1, null); }

        public String getVertexId() { return vertexId; }
        public int getParallelism() { return parallelism; }
        public String getOperatorId() { return operatorId; }
    }

    @DataBean
    public static class EdgePlan implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String sourceVertexId;
        private final String targetVertexId;
        private final PartitionPolicy partitionPolicy;

        public EdgePlan(String sourceVertexId, String targetVertexId, PartitionPolicy partitionPolicy) {
            this.sourceVertexId = sourceVertexId;
            this.targetVertexId = targetVertexId;
            this.partitionPolicy = partitionPolicy != null ? partitionPolicy : PartitionPolicy.FORWARD;
        }

        public EdgePlan() { this(null, null, PartitionPolicy.FORWARD); }

        public String getSourceVertexId() { return sourceVertexId; }
        public String getTargetVertexId() { return targetVertexId; }
        public PartitionPolicy getPartitionPolicy() { return partitionPolicy; }
    }
}
