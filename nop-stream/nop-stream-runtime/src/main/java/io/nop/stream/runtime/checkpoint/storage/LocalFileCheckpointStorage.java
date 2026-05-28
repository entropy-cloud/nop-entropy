/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

@Internal
public class LocalFileCheckpointStorage implements ICheckpointStorage {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileCheckpointStorage.class);

    private static final String CHECKPOINT_SUFFIX = ".checkpoint";
    private static final String EPOCH_MANIFEST_SUFFIX = ".epoch";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String SAVEPOINT_DIR_PREFIX = "savepoint-";
    private static final String METADATA_SUFFIX = ".metadata";
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    private final String baseDir;
    private final Path baseDirCanonical;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LocalFileCheckpointStorage(String baseDir) {
        this.baseDir = baseDir;
        this.baseDirCanonical = Paths.get(baseDir).toAbsolutePath().normalize();
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
        validateId(jobId, "jobId");
        List<CompletedCheckpoint> result = new ArrayList<>();

        lock.readLock().lock();
        try {
            Path jobRootDir = validatePath(Paths.get(baseDir, jobId));
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
        validateId(jobId, "jobId");
        List<CompletedCheckpoint> all = new ArrayList<>();

        lock.readLock().lock();
        try {
            Path jobDir = validatePath(Paths.get(baseDir, jobId));
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
        validateId(jobId, "jobId");
        Path jobRootDir = validatePath(Paths.get(baseDir, jobId));

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
        validateId(jobId, "jobId");
        int[] count = {0};
        Path jobRootDir = validatePath(Paths.get(baseDir, jobId));

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
        validateId(jobId, "jobId");
        validateId(pipelineId, "pipelineId");
        return validatePath(Paths.get(baseDir, jobId, pipelineId));
    }

    private Path getCheckpointPath(String jobId, String pipelineId, long checkpointId) {
        validateId(jobId, "jobId");
        validateId(pipelineId, "pipelineId");
        return validatePath(Paths.get(baseDir, jobId, pipelineId,
                checkpointId + CHECKPOINT_SUFFIX));
    }

    private void validateId(String id, String name) {
        if (id == null || !SAFE_ID_PATTERN.matcher(id).matches()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, name)
                    .param(ARG_DETAIL, "must match [a-zA-Z0-9_-]+, got: " + id);
        }
    }

    private Path validatePath(Path path) {
        Path canonical = path.toAbsolutePath().normalize();
        if (!canonical.startsWith(baseDirCanonical)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Path traversal detected: " + path + " is outside baseDir " + baseDirCanonical);
        }
        return canonical;
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
        return CheckpointSerDe.serializeCheckpoint(checkpoint);
    }

    private CompletedCheckpoint deserializeCheckpoint(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        return CheckpointSerDe.deserializeCheckpoint(data);
    }

    private CompletedCheckpoint deserializeCheckpoint(byte[] data) {
        return CheckpointSerDe.deserializeCheckpoint(data);
    }

    private TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map, TaskLocation taskLocation) {
        return CheckpointSerDe.deserializeTaskStateSnapshot(map, taskLocation);
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
        Path savepointDir = validatePath(Paths.get(targetPath, savepointDirName));

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
        Path dir = validatePath(Paths.get(savepointPath));
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
        Path dir = validatePath(Paths.get(savepointPath));
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
        validateId(jobId, "jobId");
        validateId(pipelineId, "pipelineId");
        return validatePath(Paths.get(baseDir, jobId, pipelineId, epochId + EPOCH_MANIFEST_SUFFIX));
    }

    private byte[] serializeEpochManifest(EpochManifest manifest) {
        return CheckpointSerDe.serializeEpochManifest(manifest);
    }

    private Map<String, Object> serializeTaskStateSnapshot(TaskStateSnapshot snapshot) {
        return CheckpointSerDe.serializeTaskStateSnapshot(snapshot);
    }

    private EpochManifest deserializeEpochManifest(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        return CheckpointSerDe.deserializeEpochManifest(data);
    }
}
