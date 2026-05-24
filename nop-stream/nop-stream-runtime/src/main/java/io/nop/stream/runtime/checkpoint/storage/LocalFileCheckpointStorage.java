/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.api.core.annotations.core.Internal;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.model.StreamModelFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

@Internal
public class LocalFileCheckpointStorage implements ICheckpointStorage {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileCheckpointStorage.class);

    private static final String CHECKPOINT_SUFFIX = ".checkpoint";
    private static final String EPOCH_MANIFEST_SUFFIX = ".epoch";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String SAVEPOINT_DIR_PREFIX = "savepoint-";
    private static final String METADATA_SUFFIX = ".metadata";

    private final String baseDir;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LocalFileCheckpointStorage(String baseDir) {
        this.baseDir = baseDir;
        ensureDirectoryExists(baseDir);
    }

    @Override
    public String getName() {
        return "LocalFileCheckpointStorage";
    }

    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception {
        Path checkpointPath = getCheckpointPath(
                checkpoint.getJobId(),
                checkpoint.getPipelineId(),
                checkpoint.getCheckpointId());

        Path tempPath = Paths.get(checkpointPath.toString() + TEMP_SUFFIX);

        lock.writeLock().lock();
        try {
            ensureDirectoryExists(checkpointPath.getParent().toString());

            byte[] data = serializeCheckpoint(checkpoint);
            Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Files.move(tempPath, checkpointPath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            return checkpointPath.toString();
        } finally {
            lock.writeLock().unlock();
            deleteIfExists(tempPath);
        }
    }

    @Override
    public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws Exception {
        Path jobDir = getJobDir(jobId, pipelineId);

        lock.readLock().lock();
        try {
            if (!Files.exists(jobDir)) {
                return null;
            }

            try (Stream<Path> files = Files.list(jobDir)) {
                Optional<Path> latest = files
                        .filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX))
                        .max((a, b) -> Long.compare(
                                extractCheckpointId(a.getFileName().toString()),
                                extractCheckpointId(b.getFileName().toString())));

                if (latest.isPresent()) {
                    return deserializeCheckpoint(latest.get());
                }
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws Exception {
        List<CompletedCheckpoint> result = new ArrayList<>();

        lock.readLock().lock();
        try {
            Path jobRootDir = Paths.get(baseDir, jobId);
            if (!Files.exists(jobRootDir)) {
                return result;
            }

            try (Stream<Path> pipelineDirs = Files.list(jobRootDir)) {
                pipelineDirs.filter(Files::isDirectory)
                        .forEach(pipelineDir -> {
                            try (Stream<Path> files = Files.list(pipelineDir)) {
                                files.filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX))
                                        .forEach(p -> {
                                            try {
                                                CompletedCheckpoint cp = deserializeCheckpoint(p);
                                                if (cp != null) {
                                                    result.add(cp);
                                                }
                                            } catch (Exception e) {
                                                LOG.warn("Failed to deserialize checkpoint file: {}", p, e);
                                            }
                                        });
                            } catch (Exception e) {
                                LOG.warn("Failed to list checkpoint files in directory: {}", pipelineDir, e);
                            }
                        });
            }

            result.sort((a, b) -> Long.compare(b.getCheckpointId(), a.getCheckpointId()));
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws Exception {
        List<CompletedCheckpoint> all = new ArrayList<>();

        lock.readLock().lock();
        try {
            Path jobDir = getJobDir(jobId, "-1").getParent();
            if (!Files.exists(jobDir)) {
                return all;
            }

            try (Stream<Path> files = Files.walk(jobDir)) {
                files.filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX))
                        .forEach(p -> {
                             try {
                                CompletedCheckpoint cp = deserializeCheckpoint(p);
                                if (cp != null) {
                                    all.add(cp);
                                }
                            } catch (Exception e) {
                                LOG.warn("Failed to deserialize checkpoint file: {}", p, e);
                            }
                        });
            }

            all.sort((a, b) -> Long.compare(b.getCheckpointId(), a.getCheckpointId()));
            if (all.size() <= count) {
                return all;
            }
            return all.subList(0, count);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws Exception {
        Path checkpointPath = getCheckpointPath(jobId, pipelineId, checkpointId);

        lock.writeLock().lock();
        try {
            deleteIfExists(checkpointPath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAllCheckpoints(String jobId) throws Exception {
        Path jobRootDir = Paths.get(baseDir, jobId);

        lock.writeLock().lock();
        try {
            if (Files.exists(jobRootDir)) {
                try (Stream<Path> files = Files.walk(jobRootDir)) {
                    files.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception e) {
                                    LOG.warn("Failed to delete checkpoint file: {}", p, e);
                                }
                            });
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int getCheckpointCount(String jobId) throws Exception {
        int[] count = {0};
        Path jobRootDir = Paths.get(baseDir, jobId);

        lock.readLock().lock();
        try {
            if (!Files.exists(jobRootDir)) {
                return 0;
            }

            try (Stream<Path> files = Files.walk(jobRootDir)) {
                files.filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX))
                        .forEach(p -> count[0]++);
            }
            return count[0];
        } finally {
            lock.readLock().unlock();
        }
    }

    private Path getJobDir(String jobId, String pipelineId) {
        return Paths.get(baseDir, jobId, pipelineId);
    }

    private Path getCheckpointPath(String jobId, String pipelineId, long checkpointId) {
        return Paths.get(baseDir, jobId, pipelineId,
                checkpointId + CHECKPOINT_SUFFIX);
    }

    private long extractCheckpointId(String fileName) {
        return extractIdFromFileName(fileName, CHECKPOINT_SUFFIX);
    }

    private long extractIdFromFileName(String fileName, String suffix) {
        String name = fileName.replace(suffix, "");
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
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

    private static String taskLocationToString(TaskLocation loc) {
        return loc.getJobId() + "|" + loc.getPipelineId() + "|" + loc.getVertexId() + "|" + loc.getTaskIndex();
    }

    private static TaskLocation stringToTaskLocation(String str) {
        String[] parts = str.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid TaskLocation string: " + str);
        }
        return new TaskLocation(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
    }

    private CompletedCheckpoint deserializeCheckpoint(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        return deserializeCheckpoint(data);
    }

    private CompletedCheckpoint deserializeCheckpoint(byte[] data) {
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

    private TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map, TaskLocation taskLocation) {
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

    private void ensureDirectoryExists(String dir) {
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (Exception e) {
            LOG.warn("Failed to create directory: {}", dir, e);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            LOG.warn("Failed to delete file: {}", path, e);
        }
    }

    @Override
    public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws Exception {
        String savepointDirName = SAVEPOINT_DIR_PREFIX + checkpoint.getCheckpointId();
        Path savepointDir = Paths.get(targetPath, savepointDirName);

        lock.writeLock().lock();
        try {
            ensureDirectoryExists(savepointDir.toString());

            Path checkpointFile = savepointDir.resolve(checkpoint.getCheckpointId() + CHECKPOINT_SUFFIX);
            Path tempFile = Paths.get(checkpointFile.toString() + TEMP_SUFFIX);

            byte[] data = serializeCheckpoint(checkpoint);
            Files.write(tempFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempFile, checkpointFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            SavepointMetadata metadata = SavepointMetadata.fromCompletedCheckpoint(checkpoint);
            Path metadataFile = savepointDir.resolve("savepoint-" + checkpoint.getCheckpointId() + METADATA_SUFFIX);
            Path tempMetadata = Paths.get(metadataFile.toString() + TEMP_SUFFIX);

            byte[] metadataBytes = JsonTool.serialize(metadata, false).getBytes(StandardCharsets.UTF_8);
            Files.write(tempMetadata, metadataBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempMetadata, metadataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            LOG.info("Stored savepoint {} to {}", checkpoint.getCheckpointId(), savepointDir);
            return savepointDir.toString();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CompletedCheckpoint loadSavepoint(String savepointPath) throws Exception {
        Path dir = Paths.get(savepointPath);
        if (!Files.isDirectory(dir)) {
            LOG.warn("Savepoint path does not exist or is not a directory: {}", savepointPath);
            return null;
        }

        lock.readLock().lock();
        try {
            try (Stream<Path> files = Files.list(dir)) {
                Optional<Path> checkpointFile = files
                        .filter(p -> p.toString().endsWith(CHECKPOINT_SUFFIX))
                        .findFirst();

                if (checkpointFile.isPresent()) {
                    return deserializeCheckpoint(checkpointFile.get());
                }
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public SavepointMetadata loadSavepointMetadata(String savepointPath) throws Exception {
        Path dir = Paths.get(savepointPath);
        if (!Files.isDirectory(dir)) {
            return null;
        }

        lock.readLock().lock();
        try {
            try (Stream<Path> files = Files.list(dir)) {
                Optional<Path> metadataFile = files
                        .filter(p -> p.toString().endsWith(METADATA_SUFFIX))
                        .findFirst();

                if (metadataFile.isPresent()) {
                    byte[] data = Files.readAllBytes(metadataFile.get());
                    String json = new String(data, StandardCharsets.UTF_8);
                    return JsonTool.parseBeanFromText(json, SavepointMetadata.class);
                }
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws Exception {
        Path manifestPath = getEpochManifestPath(jobId, pipelineId, manifest.getEpochId());
        Path tempPath = Paths.get(manifestPath.toString() + TEMP_SUFFIX);

        lock.writeLock().lock();
        try {
            ensureDirectoryExists(manifestPath.getParent().toString());

            byte[] data = serializeEpochManifest(manifest);
            Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempPath, manifestPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            LOG.debug("Stored epoch manifest {} for job {}/{}", manifest.getEpochId(), jobId, pipelineId);
        } finally {
            lock.writeLock().unlock();
            deleteIfExists(tempPath);
        }
    }

    @Override
    public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws Exception {
        Path jobDir = getJobDir(jobId, pipelineId);

        lock.readLock().lock();
        try {
            if (!Files.exists(jobDir)) {
                return null;
            }

            try (Stream<Path> files = Files.list(jobDir)) {
                Optional<Path> latest = files
                        .filter(p -> p.toString().endsWith(EPOCH_MANIFEST_SUFFIX))
                        .max((a, b) -> Long.compare(
                                extractIdFromFileName(a.getFileName().toString(), EPOCH_MANIFEST_SUFFIX),
                                extractIdFromFileName(b.getFileName().toString(), EPOCH_MANIFEST_SUFFIX)));

                if (latest.isPresent()) {
                    return deserializeEpochManifest(latest.get());
                }
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private Path getEpochManifestPath(String jobId, String pipelineId, long epochId) {
        return Paths.get(baseDir, jobId, pipelineId, epochId + EPOCH_MANIFEST_SUFFIX);
    }

    private byte[] serializeEpochManifest(EpochManifest manifest) {
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
            taskSnapshotsMap.put(key, serializeTaskStateSnapshot(entry.getValue()));
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

    private Map<String, Object> serializeTaskStateSnapshot(TaskStateSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (snapshot.getOperatorStates() != null && !snapshot.getOperatorStates().isEmpty()) {
            map.put("operatorStates", snapshot.getOperatorStates());
        }
        if (snapshot.getKeyedStates() != null && !snapshot.getKeyedStates().isEmpty()) {
            map.put("keyedStates", snapshot.getKeyedStates());
        }
        return map;
    }

    private EpochManifest deserializeEpochManifest(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
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
}
