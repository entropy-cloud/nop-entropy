/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Local file system storage implementation using JSON serialization.
 */
public class LocalFileCheckpointStorage implements ICheckpointStorage {

    private static final String CHECKPOINT_SUFFIX = ".checkpoint";
    private static final String TEMP_SUFFIX = ".tmp";

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
    public CompletedCheckpoint getLatestCheckpoint(long jobId, int pipelineId) throws Exception {
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
    public List<CompletedCheckpoint> getAllCheckpoints(long jobId) throws Exception {
        List<CompletedCheckpoint> result = new ArrayList<>();

        lock.readLock().lock();
        try {
            Path jobRootDir = Paths.get(baseDir, String.valueOf(jobId));
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
                                            } catch (Exception ignored) {
                                            }
                                        });
                            } catch (Exception ignored) {
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
    public List<CompletedCheckpoint> getLatestCheckpoints(long jobId, int count) throws Exception {
        List<CompletedCheckpoint> all = new ArrayList<>();

        lock.readLock().lock();
        try {
            Path jobDir = getJobDir(jobId, -1).getParent();
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
                            } catch (Exception ignored) {
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
    public void deleteCheckpoint(long jobId, int pipelineId, long checkpointId) throws Exception {
        Path checkpointPath = getCheckpointPath(jobId, pipelineId, checkpointId);

        lock.writeLock().lock();
        try {
            deleteIfExists(checkpointPath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAllCheckpoints(long jobId) throws Exception {
        Path jobRootDir = Paths.get(baseDir, String.valueOf(jobId));

        lock.writeLock().lock();
        try {
            if (Files.exists(jobRootDir)) {
                try (Stream<Path> files = Files.walk(jobRootDir)) {
                    files.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception ignored) {
                                }
                            });
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int getCheckpointCount(long jobId) throws Exception {
        int[] count = {0};
        Path jobRootDir = Paths.get(baseDir, String.valueOf(jobId));

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

    private Path getJobDir(long jobId, int pipelineId) {
        return Paths.get(baseDir, String.valueOf(jobId), String.valueOf(pipelineId));
    }

    private Path getCheckpointPath(long jobId, int pipelineId, long checkpointId) {
        return Paths.get(baseDir, String.valueOf(jobId), String.valueOf(pipelineId),
                checkpointId + CHECKPOINT_SUFFIX);
    }

    private long extractCheckpointId(String fileName) {
        String name = fileName.replace(CHECKPOINT_SUFFIX, "");
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
        return JsonTool.serialize(checkpoint, false).getBytes(StandardCharsets.UTF_8);
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
        
        Long jobId = ((Number) map.get("jobId")).longValue();
        Integer pipelineId = ((Number) map.get("pipelineId")).intValue();
        Long checkpointId = ((Number) map.get("checkpointId")).longValue();
        Long triggerTimestamp = ((Number) map.get("triggerTimestamp")).longValue();
        Long completedTimestamp = ((Number) map.get("completedTimestamp")).longValue();
        String checkpointTypeName = (String) map.get("checkpointType");
        CheckpointType checkpointType = checkpointTypeName != null ? CheckpointType.valueOf(checkpointTypeName) : CheckpointType.CHECKPOINT;
        Boolean restored = (Boolean) map.get("restored");
        
        Map<String, Object> taskStatesMap = (Map<String, Object>) map.get("taskStates");
        Map<Long, TaskStateSnapshot> taskStates = new HashMap<>();
        if (taskStatesMap != null) {
            for (Map.Entry<String, Object> entry : taskStatesMap.entrySet()) {
                Long taskId = Long.parseLong(entry.getKey());
                Map<String, Object> stateMap = (Map<String, Object>) entry.getValue();
                TaskStateSnapshot snapshot = deserializeTaskStateSnapshot(stateMap);
                taskStates.put(taskId, snapshot);
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
    
    private TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Long taskId = ((Number) map.get("taskId")).longValue();
        TaskStateSnapshot snapshot = new TaskStateSnapshot(taskId);
        
        Map<String, Object> operatorStates = (Map<String, Object>) map.get("operatorStates");
        if (operatorStates != null) {
            for (Map.Entry<String, Object> entry : operatorStates.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    snapshot.putOperatorState(key, Base64.getDecoder().decode((String) value));
                }
            }
        }

        Map<String, Object> keyedStates = (Map<String, Object>) map.get("keyedStates");
        if (keyedStates != null) {
            for (Map.Entry<String, Object> entry : keyedStates.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    snapshot.putKeyedState(key, Base64.getDecoder().decode((String) value));
                }
            }
        }
        
        return snapshot;
    }

    private void ensureDirectoryExists(String dir) {
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (Exception ignored) {
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
