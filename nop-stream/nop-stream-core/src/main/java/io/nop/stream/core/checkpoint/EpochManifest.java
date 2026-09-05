/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.stream.core.model.StreamModelFingerprint;

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

    /**
     * Stage 49 D2: per-source-vertex enumerator state snapshots. Keyed by source vertex id,
     * valued by the serialized enumerator state blob (version + bytes) for that source.
     * Empty map for jobs without split-based sources or before Stage 49.
     *
     * <p>This is the code-level landing of {@code checkpoint-design.md} §2.6 manifest field
     * {@code sourceEnumeratorSnapshots} and §5.3 6-state decomposition.
     */
    private final Map<String, SourceEnumeratorSnapshot> sourceEnumeratorSnapshots;

    /**
     * Stage 51 (roadmap item 25 / D-DRIFT-2): self-describing state format version.
     * Aliases {@link CheckpointFormatVersions#CURRENT_FORMAT_VERSION} on the write path
     * (stamped at the {@code CheckpointSerDe} serialization choke point — the single
     * version truth). {@code 0} means "not set" (in-memory manifest not yet serialized,
     * or a legacy manifest restored from pre-Stage-51 bytes).
     */
    private final int stateFormatVersion;

    /**
     * Stage 51: manifest integrity checksum (SHA-256 hex over the canonical
     * serialization with this key removed). Computed and injected only at the
     * serialization choke point; this field is populated on deserialize (read-back) and
     * is {@code null} for in-memory manifests. Re-serialization always recomputes the
     * checksum from content — a stale value is never copied.
     */
    private final String checksum;

    public EpochManifest(long epochId, String jobId, String pipelineId,
                         long timestamp, CheckpointType checkpointType,
                         EpochState state,
                         Map<TaskLocation, TaskStateSnapshot> taskSnapshots,
                         StreamModelFingerprint streamModelFingerprint,
                         List<StateSegmentDescriptor> segments) {
        this(epochId, jobId, pipelineId, timestamp, checkpointType, state,
                taskSnapshots, streamModelFingerprint, segments, null);
    }

    public EpochManifest(long epochId, String jobId, String pipelineId,
                         long timestamp, CheckpointType checkpointType,
                         EpochState state,
                         Map<TaskLocation, TaskStateSnapshot> taskSnapshots,
                         StreamModelFingerprint streamModelFingerprint,
                         List<StateSegmentDescriptor> segments,
                         Map<String, SourceEnumeratorSnapshot> sourceEnumeratorSnapshots) {
        this(epochId, jobId, pipelineId, timestamp, checkpointType, state,
                taskSnapshots, streamModelFingerprint, segments, sourceEnumeratorSnapshots,
                CheckpointFormatVersions.UNSET_FORMAT_VERSION, null);
    }

    public EpochManifest(long epochId, String jobId, String pipelineId,
                         long timestamp, CheckpointType checkpointType,
                         EpochState state,
                         Map<TaskLocation, TaskStateSnapshot> taskSnapshots,
                         StreamModelFingerprint streamModelFingerprint,
                         List<StateSegmentDescriptor> segments,
                         Map<String, SourceEnumeratorSnapshot> sourceEnumeratorSnapshots,
                         int stateFormatVersion, String checksum) {
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
        this.sourceEnumeratorSnapshots = sourceEnumeratorSnapshots != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(sourceEnumeratorSnapshots))
                : Collections.emptyMap();
        this.stateFormatVersion = stateFormatVersion;
        this.checksum = checksum;
    }

    public EpochManifest() {
        this(-1, null, null, 0, null, null, null, null, null, null);
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

    /** Stage 49 D2: per-source-vertex enumerator state snapshots. */
    public Map<String, SourceEnumeratorSnapshot> getSourceEnumeratorSnapshots() {
        return sourceEnumeratorSnapshots;
    }

    /**
     * Stage 51: self-describing state format version. {@code 0} = not set (legacy or
     * not yet serialized); otherwise aliases the {@code CheckpointSerDe} envelope
     * version truth (see {@link CheckpointFormatVersions}).
     */
    public int getStateFormatVersion() { return stateFormatVersion; }

    /**
     * Stage 51: manifest integrity checksum (SHA-256 hex, canonical serialization).
     * {@code null} for in-memory manifests; populated on deserialize when present in
     * the persisted bytes (absent on legacy manifests written before Stage 51).
     */
    public String getChecksum() { return checksum; }
}
