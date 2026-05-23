/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.model.StreamModelFingerprint;

import java.io.Serializable;
import java.util.*;

@DataBean
public class EpochManifest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long epochId;
    private final String jobId;
    private final String pipelineId;
    private final long timestamp;
    private final CheckpointType checkpointType;
    private final EpochState state;
    private final Map<TaskLocation, TaskStateSnapshot> taskSnapshots;
    private final StreamModelFingerprint streamModelFingerprint;
    private final List<StateSegmentDescriptor> segments;

    public EpochManifest(long epochId, String jobId, String pipelineId,
                         long timestamp, CheckpointType checkpointType,
                         EpochState state,
                         Map<TaskLocation, TaskStateSnapshot> taskSnapshots,
                         StreamModelFingerprint streamModelFingerprint,
                         List<StateSegmentDescriptor> segments) {
        this.epochId = epochId;
        this.jobId = jobId;
        this.pipelineId = pipelineId;
        this.timestamp = timestamp;
        this.checkpointType = checkpointType;
        this.state = state;
        this.taskSnapshots = taskSnapshots != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(taskSnapshots))
                : Collections.emptyMap();
        this.streamModelFingerprint = streamModelFingerprint;
        this.segments = segments != null
                ? Collections.unmodifiableList(new ArrayList<>(segments))
                : Collections.emptyList();
    }

    public EpochManifest() {
        this(-1, null, null, 0, null, null, null, null, null);
    }

    public long getEpochId() { return epochId; }
    public String getJobId() { return jobId; }
    public String getPipelineId() { return pipelineId; }
    public long getTimestamp() { return timestamp; }
    public CheckpointType getCheckpointType() { return checkpointType; }
    public EpochState getState() { return state; }
    public Map<TaskLocation, TaskStateSnapshot> getTaskSnapshots() { return taskSnapshots; }
    public StreamModelFingerprint getStreamModelFingerprint() { return streamModelFingerprint; }
    public List<StateSegmentDescriptor> getSegments() { return segments; }
}
