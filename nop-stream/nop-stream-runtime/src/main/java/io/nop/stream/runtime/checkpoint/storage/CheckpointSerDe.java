/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

@Internal
public class CheckpointSerDe {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointSerDe.class);

    public static byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
        Map<String, Object> serializable = new LinkedHashMap<>();
        serializable.put("jobId", checkpoint.getJobId());
        serializable.put("pipelineId", checkpoint.getPipelineId());
        serializable.put("checkpointId", checkpoint.getCheckpointId());
        serializable.put("triggerTimestamp", checkpoint.getTriggerTimestamp());
        serializable.put("completedTimestamp", checkpoint.getCompletedTimestamp());
        serializable.put("checkpointType", checkpoint.getCheckpointType().name());
        serializable.put("restored", checkpoint.isRestored());

        Map<String, Object> taskStatesMap = new LinkedHashMap<>();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : checkpoint.getTaskStates().entrySet()) {
            String key = taskLocationToString(entry.getKey());
            taskStatesMap.put(key, entry.getValue());
        }
        serializable.put("taskStates", taskStatesMap);

        return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
    }

    public static CompletedCheckpoint deserializeCheckpoint(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        Map<String, Object> map = JsonTool.parseMap(json);
        if (map == null) {
            return null;
        }

        String jobId = (String) map.get("jobId");
        String pipelineId = (String) map.get("pipelineId");
        Long checkpointId = map.get("checkpointId") instanceof Number ? ((Number) map.get("checkpointId")).longValue() : null;
        Long triggerTimestamp = map.get("triggerTimestamp") instanceof Number ? ((Number) map.get("triggerTimestamp")).longValue() : null;
        Long completedTimestamp = map.get("completedTimestamp") instanceof Number ? ((Number) map.get("completedTimestamp")).longValue() : null;
        if (jobId == null || pipelineId == null || checkpointId == null
                || triggerTimestamp == null || completedTimestamp == null) {
            LOG.warn("Checkpoint data missing required fields, skipping deserialization");
            return null;
        }
        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : CheckpointType.CHECKPOINT;
        Boolean restored = (Boolean) map.get("restored");

        Map<String, Object> taskStatesMap = (Map<String, Object>) map.get("taskStates");
        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        if (taskStatesMap != null) {
            for (Map.Entry<String, Object> entry : taskStatesMap.entrySet()) {
                TaskLocation taskLocation;
                try {
                    taskLocation = stringToTaskLocation(entry.getKey());
                } catch (Exception e) {
                    LOG.warn("Failed to parse TaskLocation from key '{}', using fallback", entry.getKey(), e);
                    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
                }
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap, taskLocation);
                taskStates.put(taskLocation, snapshot);
            }
        }

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(triggerTimestamp)
                .completedTimestamp(completedTimestamp)
                .checkpointType(checkpointType)
                .taskStates(taskStates)
                .build();

        if (restored != null) {
            checkpoint.setRestored(restored);
        }

        return checkpoint;
    }

    public static byte[] serializeEpochManifest(EpochManifest manifest) {
        Map<String, Object> serializable = new LinkedHashMap<>();
        serializable.put("epochId", manifest.getEpochId());
        serializable.put("jobId", manifest.getJobId());
        serializable.put("pipelineId", manifest.getPipelineId());
        serializable.put("timestamp", manifest.getTimestamp());
        if (manifest.getCheckpointType() != null) {
            serializable.put("checkpointType", manifest.getCheckpointType().name());
        }
        if (manifest.getState() != null) {
            serializable.put("state", manifest.getState().name());
        }

        Map<String, Object> taskSnapshotsMap = new LinkedHashMap<>();
        for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : manifest.getTaskSnapshots().entrySet()) {
            String key = taskLocationToString(entry.getKey());
            Map<String, Object> snapshotMap = new LinkedHashMap<>();
            if (entry.getValue().getOperatorStates() != null && !entry.getValue().getOperatorStates().isEmpty()) {
                snapshotMap.put("operatorStates", entry.getValue().getOperatorStates());
            }
            if (entry.getValue().getKeyedStates() != null && !entry.getValue().getKeyedStates().isEmpty()) {
                snapshotMap.put("keyedStates", entry.getValue().getKeyedStates());
            }
            taskSnapshotsMap.put(key, snapshotMap);
        }
        serializable.put("taskSnapshots", taskSnapshotsMap);

        if (manifest.getStreamModelFingerprint() != null) {
            StreamModelFingerprint fp = manifest.getStreamModelFingerprint();
            Map<String, Object> fpMap = new LinkedHashMap<>();
            fpMap.put("version", fp.getVersion());
            fpMap.put("dagTopologyHash", fp.getDagTopologyHash());
            fpMap.put("requirementsHash", fp.getRequirementsHash());
            fpMap.put("checkpointParticipantsHash", fp.getCheckpointParticipantsHash());
            fpMap.put("componentHashes", fp.getComponentHashes());
            serializable.put("streamModelFingerprint", fpMap);
        }

        if (manifest.getSegments() != null && !manifest.getSegments().isEmpty()) {
            java.util.List<Map<String, Object>> segmentsList = new java.util.ArrayList<>();
            for (StateSegmentDescriptor seg : manifest.getSegments()) {
                Map<String, Object> segMap = new LinkedHashMap<>();
                segMap.put("segmentType", seg.getSegmentType());
                segMap.put("path", seg.getPath());
                segMap.put("codec", seg.getCodec());
                segMap.put("checksum", seg.getChecksum());
                segMap.put("schemaVersion", seg.getSchemaVersion());
                segmentsList.add(segMap);
            }
            serializable.put("segments", segmentsList);
        }

        return JsonTool.serialize(serializable, false).getBytes(StandardCharsets.UTF_8);
    }

    public static EpochManifest deserializeEpochManifest(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        Map<String, Object> map = JsonTool.parseMap(json);
        if (map == null) {
            return null;
        }

        long epochId = map.get("epochId") instanceof Number ? ((Number) map.get("epochId")).longValue() : -1;
        String jobId = (String) map.get("jobId");
        String pipelineId = (String) map.get("pipelineId");
        long timestamp = map.get("timestamp") instanceof Number ? ((Number) map.get("timestamp")).longValue() : 0;

        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : null;

        String stateName = (String) map.get("state");
        EpochState epochState = stateName != null ? EpochState.valueOf(stateName) : null;

        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        Map<String, Object> taskSnapshotsMap = (Map<String, Object>) map.get("taskSnapshots");
        if (taskSnapshotsMap != null) {
            for (Map.Entry<String, Object> entry : taskSnapshotsMap.entrySet()) {
                TaskLocation taskLocation;
                try {
                    taskLocation = stringToTaskLocation(entry.getKey());
                } catch (Exception e) {
                    LOG.warn("Failed to parse TaskLocation from key '{}' in epoch manifest, using fallback", entry.getKey(), e);
                    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
                }
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap, taskLocation);
                taskSnapshots.put(taskLocation, snapshot);
            }
        }

        StreamModelFingerprint fingerprint = null;
        Map<String, Object> fpMap = (Map<String, Object>) map.get("streamModelFingerprint");
        if (fpMap != null) {
            StreamModelFingerprint.Builder fpBuilder = StreamModelFingerprint.builder();
            fpBuilder.version((String) fpMap.get("version"));
            fpBuilder.dagTopologyHash((String) fpMap.get("dagTopologyHash"));
            fpBuilder.requirementsHash((String) fpMap.get("requirementsHash"));
            fpBuilder.checkpointParticipantsHash((String) fpMap.get("checkpointParticipantsHash"));
            Map<String, String> compHashes = (Map<String, String>) fpMap.get("componentHashes");
            if (compHashes != null) {
                for (Map.Entry<String, String> e : compHashes.entrySet()) {
                    fpBuilder.addComponentHash(e.getKey(), e.getValue());
                }
            }
            fingerprint = fpBuilder.build();
        }

        java.util.List<StateSegmentDescriptor> segments = new java.util.ArrayList<>();
        java.util.List<Map<String, Object>> segmentsList = (java.util.List<Map<String, Object>>) map.get("segments");
        if (segmentsList != null) {
            for (Map<String, Object> segMap : segmentsList) {
                segments.add(new StateSegmentDescriptor(
                        (String) segMap.get("segmentType"),
                        (String) segMap.get("path"),
                        (String) segMap.get("codec"),
                        (String) segMap.get("checksum"),
                        segMap.get("schemaVersion") instanceof Number ? ((Number) segMap.get("schemaVersion")).intValue() : 1
                ));
            }
        }

        return new EpochManifest(epochId, jobId, pipelineId, timestamp, checkpointType, epochState,
                taskSnapshots, fingerprint, segments);
    }

    public static String taskLocationToString(TaskLocation loc) {
        return loc.getJobId() + "|" + loc.getPipelineId() + "|" + loc.getVertexId() + "|" + loc.getTaskIndex();
    }

    public static TaskLocation stringToTaskLocation(String str) {
        String[] parts = str.split("\\|");
        if (parts.length != 4) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "taskLocation")
                    .param(ARG_DETAIL, "Invalid TaskLocation string: " + str);
        }
        return new TaskLocation(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
    }

    public static Map<String, Object> serializeTaskStateSnapshot(TaskStateSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (snapshot.getOperatorStates() != null && !snapshot.getOperatorStates().isEmpty()) {
            map.put("operatorStates", snapshot.getOperatorStates());
        }
        if (snapshot.getKeyedStates() != null && !snapshot.getKeyedStates().isEmpty()) {
            map.put("keyedStates", snapshot.getKeyedStates());
        }
        return map;
    }

    public static TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map, TaskLocation taskLocation) {
        if (map == null) {
            return null;
        }
        TaskStateSnapshot snapshot = new TaskStateSnapshot(taskLocation);

        Map<String, Object> operatorStates = (Map<String, Object>) map.get("operatorStates");
        if (operatorStates != null) {
            for (Map.Entry<String, Object> entry : operatorStates.entrySet()) {
                snapshot.putOperatorState(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> keyedStates = (Map<String, Object>) map.get("keyedStates");
        if (keyedStates != null) {
            for (Map.Entry<String, Object> entry : keyedStates.entrySet()) {
                snapshot.putKeyedState(entry.getKey(), entry.getValue());
            }
        }

        return snapshot;
    }
}
