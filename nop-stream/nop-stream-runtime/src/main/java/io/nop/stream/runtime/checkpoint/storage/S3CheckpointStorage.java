/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.checkpoint.storage;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.exceptions.StreamException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * WI13 (plan 2339): S3-compatible object storage backend for checkpoints and epoch
 * manifests — mirrors the {@link LocalFileCheckpointStorage} object layout and
 * semantics one-to-one, with each file becoming one object:
 *
 * <pre>
 * {basePrefix}/{jobId}/{pipelineId}/{checkpointId}.checkpoint   — full checkpoint JSON
 * {basePrefix}/{jobId}/{pipelineId}/{epochId}.epoch             — epoch manifest
 * {targetPath}/savepoint-{id}/...checkpoint / ...metadata       — savepoint pair
 * </pre>
 *
 * <p><strong>Semantics mirrored from the local backend</strong>: latest = max
 * checkpointId parsed from the key (no GETs, no completedTimestamp correlation);
 * {@code loadRetainedEpochManifests} and {@code pruneEpochManifests} are both
 * overridden (the defaults would silently reduce the SharedStateRegistry rebuild to
 * one manifest — the same regression the JDBC backend once fixed); id whitelist
 * validation prevents key-injection through jobId/pipelineId/checkpointId; a
 * savepoint path outside {@code basePrefix} is rejected.
 *
 * <p><strong>Writes</strong>: a single putObject per object is the atomic publish
 * point (S3 same-key overwrite semantics) — no tmp-then-rename staging is needed.
 * getObject results are always closed (the S3Object holds an HTTP stream).
 *
 * <p><strong>Config (storageType=s3, via {@link #fromConfig})</strong>:
 * {@code s3.endpoint} (required), {@code s3.bucket} (required — also the
 * auto-restore intent marker, mirroring local's explicit path),
 * {@code s3.accessKey}/{@code s3.secretKey}, {@code s3.region},
 * {@code s3.pathStyleAccess} (default true, MinIO-style),
 * {@code s3.basePrefix} (default nop-stream-checkpoints).
 */
public class S3CheckpointStorage implements ICheckpointStorage {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(S3CheckpointStorage.class);

    private static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    private final AmazonS3 s3;
    private final String bucket;
    private final String basePrefix;

    public S3CheckpointStorage(AmazonS3 s3, String bucket, String basePrefix) {
        if (s3 == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param("argName", (Object) "s3Client");
        }
        if (bucket == null || bucket.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param("argName", (Object) "bucket");
        }
        this.s3 = s3;
        this.bucket = bucket;
        this.basePrefix = basePrefix == null || basePrefix.isEmpty() ? "nop-stream-checkpoints" : basePrefix;
    }

    /**
     * Builds a storage from {@code storageType=s3} checkpoint config properties:
     * {@code s3.endpoint} / {@code s3.bucket} (both required), {@code s3.accessKey},
     * {@code s3.secretKey}, {@code s3.region}, {@code s3.pathStyleAccess},
     * {@code s3.basePrefix}. The secret is never echoed back in logs or exceptions.
     */
    public static S3CheckpointStorage fromConfig(CheckpointConfig config) {
        String endpoint = require(config, "s3.endpoint");
        String bucket = require(config, "s3.bucket");
        String accessKey = config.getStorageProperty("s3.accessKey");
        String secretKey = config.getStorageProperty("s3.secretKey");
        String region = config.getStorageProperty("s3.region");
        String pathStyle = config.getStorageProperty("s3.pathStyleAccess");
        String basePrefix = config.getStorageProperty("s3.basePrefix");

        com.amazonaws.services.s3.AmazonS3ClientBuilder builder =
                com.amazonaws.services.s3.AmazonS3ClientBuilder.standard();
        boolean pathStyleEnabled = pathStyle == null || Boolean.parseBoolean(pathStyle);
        builder.withPathStyleAccessEnabled(pathStyleEnabled);
        builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint,
                region == null || region.isEmpty() ? "us-east-1" : region));
        if (accessKey != null && !accessKey.isEmpty()) {
            if (secretKey == null || secretKey.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_DETAIL, "s3.accessKey configured without s3.secretKey");
            }
            builder.withCredentials(new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(accessKey, secretKey)));
        }
        AmazonS3 client = builder.build();
        return new S3CheckpointStorage(client, bucket, basePrefix);
    }

    private static String require(CheckpointConfig config, String name) {
        String v = config.getStorageProperty(name);
        if (v == null || v.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "storageType=s3 requires storageProperty '" + name + "'");
        }
        return v;
    }

    // ==================== key helpers ====================

    private static void validateId(String what, String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, what + " must match [a-zA-Z0-9_-]+, got: " + id);
        }
    }

    private String checkpointKey(String jobId, String pipelineId, long checkpointId) {
        validateId("jobId", jobId);
        validateId("pipelineId", pipelineId);
        return basePrefix + "/" + jobId + "/" + pipelineId + "/" + checkpointId + ".checkpoint";
    }

    private String manifestKey(String jobId, String pipelineId, long epochId) {
        validateId("jobId", jobId);
        validateId("pipelineId", pipelineId);
        return basePrefix + "/" + jobId + "/" + pipelineId + "/" + epochId + ".epoch";
    }

    private String jobPrefix(String jobId) {
        validateId("jobId", jobId);
        return basePrefix + "/" + jobId + "/";
    }

    private String pipelinePrefix(String jobId, String pipelineId) {
        return jobPrefix(jobId) + pipelineId + "/";
    }

    private void putBytes(String key, byte[] bytes) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        s3.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes), meta));
    }

    private byte[] getBytes(String key) {
        // S3Object holds an HTTP stream — always closed
        try (S3Object obj = s3.getObject(bucket, key)) {
            return readAll(obj.getObjectContent());
        } catch (java.io.IOException e) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "failed to read object " + key + ": " + e.getMessage());
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private List<String> listKeys(String prefix, String suffix) {
        List<String> keys = new ArrayList<>();
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucket).withPrefix(prefix);
        ListObjectsV2Result result;
        do {
            result = s3.listObjectsV2(request);
            result.getObjectSummaries().forEach(sum -> {
                String key = sum.getKey();
                if (key.endsWith(suffix)) {
                    keys.add(key);
                }
            });
            if (result.isTruncated()) {
                // first version keeps single-page reads; truncation must be visible
                LOG.warn("S3CheckpointStorage list under {} is truncated; increase retention "
                        + "hygiene or page the scan", prefix);
                request.setContinuationToken(result.getNextContinuationToken());
            }
        } while (result.isTruncated());
        return keys;
    }

    private static long checkpointIdOfKey(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        return Long.parseLong(name.substring(0, name.length() - ".checkpoint".length()));
    }

    private static long epochIdOfKey(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        return Long.parseLong(name.substring(0, name.length() - ".epoch".length()));
    }

    private CompletedCheckpoint loadCheckpointObject(String key) {
        try {
            return CheckpointSerDe.deserializeCheckpoint(getBytes(key));
        } catch (StreamException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "failed to load checkpoint object " + key + ": " + e.getMessage());
        }
    }

    // ==================== ICheckpointStorage ====================

    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) {
        String key = checkpointKey(checkpoint.getJobId(), checkpoint.getPipelineId(),
                checkpoint.getCheckpointId());
        putBytes(key, CheckpointSerDe.serializeCheckpoint(checkpoint));
        return key;
    }

    @Override
    public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) {
        List<String> keys = listKeys(pipelinePrefix(jobId, pipelineId), ".checkpoint");
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort(Comparator.comparingLong(S3CheckpointStorage::checkpointIdOfKey));
        return loadCheckpointObject(keys.get(keys.size() - 1));
    }

    @Override
    public List<CompletedCheckpoint> getAllCheckpoints(String jobId) {
        List<CompletedCheckpoint> out = new ArrayList<>();
        for (String key : listKeys(jobPrefix(jobId), ".checkpoint")) {
            out.add(loadCheckpointObject(key));
        }
        out.sort(Comparator.comparingLong(CompletedCheckpoint::getCheckpointId));
        return out;
    }

    @Override
    public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) {
        List<CompletedCheckpoint> all = getAllCheckpoints(jobId);
        all.sort(Comparator.comparingLong((CompletedCheckpoint c) -> c.getCheckpointId()).reversed());
        return all.subList(0, Math.min(count, all.size()));
    }

    @Override
    public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) {
        s3.deleteObject(bucket, checkpointKey(jobId, pipelineId, checkpointId));
    }

    @Override
    public void deleteAllCheckpoints(String jobId) {
        // both planes, mirroring the JDBC backend's delete-all
        for (String key : listKeys(jobPrefix(jobId), ".checkpoint")) {
            s3.deleteObject(bucket, key);
        }
        for (String key : listKeys(jobPrefix(jobId), ".epoch")) {
            s3.deleteObject(bucket, key);
        }
    }

    @Override
    public String getName() {
        return "S3CheckpointStorage";
    }

    @Override
    public boolean exists(String jobId, String pipelineId, long checkpointId) {
        return s3.doesObjectExist(bucket, checkpointKey(jobId, pipelineId, checkpointId));
    }

    @Override
    public int getCheckpointCount(String jobId) {
        return listKeys(jobPrefix(jobId), ".checkpoint").size();
    }

    // ==================== savepoints ====================

    @Override
    public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) {
        if (targetPath == null || !targetPath.startsWith(basePrefix + "/")) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "savepoint targetPath must be inside basePrefix '" + basePrefix
                            + "/', got: " + targetPath);
        }
        String cpKey = targetPath + "/savepoint-" + checkpoint.getCheckpointId() + ".checkpoint";
        String metaKey = targetPath + "/savepoint-" + checkpoint.getCheckpointId() + ".metadata";
        putBytes(cpKey, CheckpointSerDe.serializeCheckpoint(checkpoint));
        putBytes(metaKey, ("{\"jobId\":\"" + checkpoint.getJobId() + "\",\"pipelineId\":\""
                + checkpoint.getPipelineId() + "\",\"checkpointId\":"
                + checkpoint.getCheckpointId() + "}").getBytes(StandardCharsets.UTF_8));
        return targetPath + "/savepoint-" + checkpoint.getCheckpointId();
    }

    @Override
    public CompletedCheckpoint loadSavepoint(String savepointPath) {
        validateSavepointPath(savepointPath);
        List<String> keys = listKeys(savepointPath, ".checkpoint");
        if (keys.isEmpty()) {
            return null; // mirror local: absent savepoint = null
        }
        if (keys.size() != 1) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "savepoint prefix " + savepointPath + " must contain exactly one "
                            + ".checkpoint object, found " + keys.size());
        }
        return loadCheckpointObject(keys.get(0));
    }

    @Override
    public SavepointMetadata loadSavepointMetadata(String savepointPath) {
        validateSavepointPath(savepointPath);
        List<String> keys = listKeys(savepointPath, ".metadata");
        if (keys.isEmpty()) {
            return null; // mirror local: absent savepoint metadata = null
        }
        if (keys.size() != 1) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "savepoint prefix " + savepointPath + " must contain exactly one "
                            + ".metadata object, found " + keys.size());
        }
        // mirror local: metadata JSON parsed via JsonTool into SavepointMetadata
        String json = new String(getBytes(keys.get(0)), StandardCharsets.UTF_8);
        return io.nop.core.lang.json.JsonTool.parseBeanFromText(json, SavepointMetadata.class);
    }

    private void validateSavepointPath(String savepointPath) {
        if (savepointPath == null || !savepointPath.startsWith(basePrefix + "/")) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "savepoint path must be inside basePrefix '" + basePrefix
                            + "/', got: " + savepointPath);
        }
    }

    // ==================== epoch manifests ====================

    @Override
    public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) {
        putBytes(manifestKey(jobId, pipelineId, manifest.getEpochId()),
                CheckpointSerDe.serializeEpochManifest(manifest));
    }

    @Override
    public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) {
        List<String> keys = listKeys(pipelinePrefix(jobId, pipelineId), ".epoch");
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort(Comparator.comparingLong(S3CheckpointStorage::epochIdOfKey));
        try {
            return CheckpointSerDe.deserializeEpochManifest(getBytes(keys.get(keys.size() - 1)));
        } catch (StreamException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "failed to load epoch manifest " + keys.get(keys.size() - 1)
                            + ": " + e.getMessage());
        }
    }

    /**
     * Overridden per the SPI contract: a per-epoch manifest backend MUST return the
     * retained set, otherwise the SharedStateRegistry reference-count rebuild silently
     * degrades to the latest single manifest.
     */
    @Override
    public List<EpochManifest> loadRetainedEpochManifests(String jobId, String pipelineId, int count) {
        List<String> keys = listKeys(pipelinePrefix(jobId, pipelineId), ".epoch");
        keys.sort(Comparator.comparingLong(S3CheckpointStorage::epochIdOfKey).reversed());
        List<EpochManifest> out = new ArrayList<>();
        for (String key : keys.subList(0, Math.min(count, keys.size()))) {
            try {
                out.add(CheckpointSerDe.deserializeEpochManifest(getBytes(key)));
            } catch (StreamException e) {
                throw e;
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "failed to load epoch manifest " + key + ": " + e.getMessage());
            }
        }
        return out;
    }

    @Override
    public List<Long> pruneEpochManifests(String jobId, String pipelineId, int maxRetained)
            throws CheckpointStorageException {
        List<String> keys = listKeys(pipelinePrefix(jobId, pipelineId), ".epoch");
        keys.sort(Comparator.comparingLong(S3CheckpointStorage::epochIdOfKey).reversed());
        List<Long> pruned = new ArrayList<>();
        for (int i = maxRetained; i < keys.size(); i++) {
            s3.deleteObject(bucket, keys.get(i));
            pruned.add(epochIdOfKey(keys.get(i)));
        }
        return pruned;
    }
}
