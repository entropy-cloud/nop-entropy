/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class CheckpointPlan implements Serializable {

    private static final long serialVersionUID = 2L;

    private final int version;
    private final String jobId;
    private final String pipelineId;
    private final List<TaskLocation> allTasks;
    private final List<TaskLocation> sourceTasks;
    private final Map<TaskLocation, List<OperatorStateMapping>> stateMappings;
    private final List<String> checkpointParticipants;
    private final ProcessingGuarantee processingGuarantee;

    @JsonCreator
    public CheckpointPlan(
            @JsonProperty("version") int version,
            @JsonProperty("jobId") String jobId,
            @JsonProperty("pipelineId") String pipelineId,
            @JsonProperty("allTasks") List<TaskLocation> allTasks,
            @JsonProperty("sourceTasks") List<TaskLocation> sourceTasks,
            @JsonProperty("stateMappings") Map<TaskLocation, List<OperatorStateMapping>> stateMappings,
            @JsonProperty("checkpointParticipants") List<String> checkpointParticipants,
            @JsonProperty("processingGuarantee") ProcessingGuarantee processingGuarantee) {
        this.version = version;
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.allTasks = allTasks != null ? Collections.unmodifiableList(new ArrayList<>(allTasks)) : Collections.emptyList();
        this.sourceTasks = sourceTasks != null ? Collections.unmodifiableList(new ArrayList<>(sourceTasks)) : Collections.emptyList();
        this.stateMappings = stateMappings != null ? Collections.unmodifiableMap(new LinkedHashMap<>(stateMappings)) : Collections.emptyMap();
        this.checkpointParticipants = checkpointParticipants != null
                ? Collections.unmodifiableList(new ArrayList<>(checkpointParticipants))
                : Collections.emptyList();
        this.processingGuarantee = processingGuarantee != null ? processingGuarantee : ProcessingGuarantee.AT_LEAST_ONCE;
    }

    public CheckpointPlan(String jobId, String pipelineId,
                          List<TaskLocation> allTasks, List<TaskLocation> sourceTasks,
                          Map<TaskLocation, List<OperatorStateMapping>> stateMappings) {
        this(1, jobId, pipelineId, allTasks, sourceTasks, stateMappings, Collections.emptyList(), ProcessingGuarantee.AT_LEAST_ONCE);
    }

    public CheckpointPlan(int version, String jobId, String pipelineId,
                          List<TaskLocation> allTasks, List<TaskLocation> sourceTasks,
                          Map<TaskLocation, List<OperatorStateMapping>> stateMappings) {
        this(version, jobId, pipelineId, allTasks, sourceTasks, stateMappings, Collections.emptyList(), ProcessingGuarantee.AT_LEAST_ONCE);
    }

    public int getVersion() { return version; }
    public String getJobId() { return jobId; }
    public String getPipelineId() { return pipelineId; }
    public List<TaskLocation> getAllTasks() { return allTasks; }
    public List<TaskLocation> getSourceTasks() { return sourceTasks; }
    public Map<TaskLocation, List<OperatorStateMapping>> getStateMappings() { return stateMappings; }
    public List<String> getCheckpointParticipants() { return checkpointParticipants; }
    public ProcessingGuarantee getProcessingGuarantee() { return processingGuarantee; }

    public List<OperatorStateMapping> getStateMappings(TaskLocation taskLocation) {
        List<OperatorStateMapping> mappings = stateMappings.get(taskLocation);
        return mappings != null ? mappings : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "CheckpointPlan{" +
                "version=" + version +
                ", jobId='" + jobId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", allTasks=" + allTasks.size() +
                ", sourceTasks=" + sourceTasks.size() +
                ", stateMappings=" + stateMappings.size() +
                '}';
    }
}
