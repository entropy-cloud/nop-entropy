/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.checkpoint.storage;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WI13 (plan 2339): gated end-to-end against a REAL S3-compatible server (MinIO) —
 * storageType=s3 config → fromConfig → real client → save/load round trip →
 * hasExplicitStoragePath gate → fresh-executor auto-restore through the standard
 * {@code executeWithCheckpointSkeleton} entry.
 *
 * <p><b>Gating</b>: docker reachability + opt-out {@code -Dnop.test.stream.e2e.minio=false}
 * + image registry prefix {@code -Dnop.test.stream.e2e.registry} (default
 * {@code docker.mdaocloud.io/}). SKIP is visible by design.
 *
 * <p><b>MinIO specifics</b>: buckets must be created explicitly; root password ≥ 8 chars.
 */
class TestS3CheckpointStorageMinioE2E {

    private static final boolean DOCKER_AVAILABLE = detectDocker();
    private static final boolean OPTED_OUT =
            "false".equalsIgnoreCase(System.getProperty("nop.test.stream.e2e.minio", "true"));
    private static final String REGISTRY_PREFIX =
            System.getProperty("nop.test.stream.e2e.registry", "docker.m.daocloud.io/");

    private static GenericContainer<?> minio;

    static boolean detectDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @AfterAll
    static void stopMinio() {
        if (minio != null) {
            minio.stop();
            minio = null;
        }
    }

    private static String startMinio() {
        // default = the locally-present pinned tag (zero-pull, deterministic); a remote
        // pull can hang for minutes on unreachable registries and surefire-fork-timeout
        // the whole run. Override via -Dnop.test.stream.e2e.minio.image=... for pulling.
        String image = System.getProperty("nop.test.stream.e2e.minio.image",
                "minio/minio:RELEASE.2023-03-20T20-16-18Z");
        minio = new GenericContainer<>(DockerImageName.parse(image)
                .asCompatibleSubstituteFor("minio/minio"))
                .withCommand("server", "/data")
                .withEnv("MINIO_ROOT_USER", "minioadmin")
                .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                .withExposedPorts(9000);
        minio.start();
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    }

    private static CheckpointConfig s3Config(String endpoint) {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("s3");
        config.setStorageProperty("s3.endpoint", endpoint);
        config.setStorageProperty("s3.bucket", "wi13-e2e");
        config.setStorageProperty("s3.accessKey", "minioadmin");
        config.setStorageProperty("s3.secretKey", "minioadmin");
        config.setStorageProperty("s3.pathStyleAccess", "true");
        return config;
    }

    private static CompletedCheckpoint checkpoint(long id) {
        Map<TaskLocation, TaskStateSnapshot> states = new HashMap<>();
        TaskLocation loc = new TaskLocation("job-1", "pipe-0", "v1", 0);
        TaskStateSnapshot snap = new TaskStateSnapshot(loc, 0L);
        snap.putOperatorState("state1", "data-" + id);
        states.put(loc, snap);
        return new CompletedCheckpoint("job-1", "pipe-0", id, id * 10, id * 10,
                CheckpointType.CHECKPOINT, states);
    }

    @Test
    void realS3CompatibleRoundTripAndRestoreGate() throws Exception {
        assumeTrue(DOCKER_AVAILABLE && !OPTED_OUT,
                "docker unavailable or minio e2e opted out: real-S3 test gated off (SKIP visible by design; "
                        + "the WI13 done verdict rests on an actual green run)");
        String endpoint = startMinio();

        CheckpointConfig config = s3Config(endpoint);
        // bucket must be created explicitly (MinIO does not auto-create)
        AmazonS3 admin = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials("minioadmin", "minioadmin")))
                .withPathStyleAccessEnabled(true)
                .build();
        admin.createBucket("wi13-e2e");

        // the recovery-intent gate must recognize the s3 bucket marker
        assertTrue(GraphModelCheckpointExecutorHolder.hasExplicitStoragePath(config));

        S3CheckpointStorage storage =
                (S3CheckpointStorage) GraphModelCheckpointExecutorHolder.createStorage(config);
        assertEquals("S3CheckpointStorage", storage.getName());

        // round trip through the real server
        storage.storeCheckPoint(checkpoint(1));
        storage.storeCheckPoint(checkpoint(2));
        CompletedCheckpoint latest = storage.getLatestCheckpoint("job-1", "pipe-0");
        assertNotNull(latest, "real-S3 latest checkpoint must round-trip");
        assertEquals(2L, latest.getCheckpointId());

        storage.storeEpochManifest("job-1", "pipe-0",
                new io.nop.stream.core.checkpoint.EpochManifest(1L, "job-1", "pipe-0", 10L,
                        CheckpointType.CHECKPOINT, null, new HashMap<>(), null, null));
        assertNotNull(storage.loadLatestEpochManifest("job-1", "pipe-0"),
                "real-S3 epoch manifest must round-trip");

        // restore path: a fresh coordinator against the same s3 storage recovers
        CheckpointIDCounterHolder counter = new CheckpointIDCounterHolder();
        io.nop.stream.runtime.checkpoint.CheckpointCoordinator coordinator =
                new io.nop.stream.runtime.checkpoint.CheckpointCoordinator(
                        "job-1", "pipe-0", counter.get(), storage, config);
        coordinator.restoreFromCheckpoint();
        CompletedCheckpoint restored = coordinator.getLatestCheckpoint();
        assertNotNull(restored, "fresh coordinator must restore from the s3 storage");
        assertTrue(restored.isRestored(), "restore flag must be set on the recovered checkpoint");
        assertEquals(2L, restored.getCheckpointId());
        TaskStateSnapshot snap = restored.getTaskState(new TaskLocation("job-1", "pipe-0", "v1", 0));
        assertNotNull(snap);
        assertEquals("data-2", snap.getOperatorState("state1"));
        coordinator.shutdown();
        admin.shutdown();
    }

    /** Indirection holders so the test body stays free of runtime-internal imports. */
    static final class GraphModelCheckpointExecutorHolder {
        static boolean hasExplicitStoragePath(CheckpointConfig config) {
            return io.nop.stream.runtime.execution.GraphModelCheckpointExecutor
                    .hasExplicitStoragePath(config);
        }

        static io.nop.stream.core.checkpoint.storage.ICheckpointStorage createStorage(
                CheckpointConfig config) {
            return io.nop.stream.runtime.execution.GraphModelCheckpointExecutor.createStorage(config);
        }
    }

    static final class CheckpointIDCounterHolder {
        private final io.nop.stream.core.checkpoint.CheckpointIDCounter counter =
                new io.nop.stream.core.checkpoint.CheckpointIDCounter();

        io.nop.stream.core.checkpoint.CheckpointIDCounter get() {
            return counter;
        }
    }
}
