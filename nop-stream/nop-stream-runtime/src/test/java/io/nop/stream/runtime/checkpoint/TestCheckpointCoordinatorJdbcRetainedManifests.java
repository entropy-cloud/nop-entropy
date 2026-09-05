/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.StateSegmentDescriptor;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistry;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.runtime.checkpoint.storage.JdbcCheckpointStorage;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Items 28+31 (W-8, Phase 3 WIRING verification): the
 * {@link JdbcCheckpointStorage#loadRetainedEpochManifests} override is
 * consumed by the REAL recovery path —
 * {@link CheckpointCoordinator#restoreSharedStateRegistry} — on the JDBC
 * backend, rebuilding {@link SharedStateRegistry} reference counts from the
 * FULL retained set (not the latest-only default). This mirrors
 * {@code TestCheckpointCoordinatorIncrementalIntegration#restartRecoveryRebuildsRegistryFromRetainedManifests}
 * (LocalFile flavor) for the JDBC storage:
 *
 * <ol>
 *   <li>run THREE incremental checkpoints over the JDBC checkpoint storage
 *       (manifests land in {@code stream_epoch_manifest}); a stable
 *       RocksDB state dedups the shared SST segments across epochs, so the
 *       retained set carries the same hashes with multi-epoch references;</li>
 *   <li>simulate a restart: fresh coordinator over the same storage + segment
 *       store, call {@code restoreSharedStateRegistry()};</li>
 *   <li>assert the registry is rebuilt from the retained manifests (ref-count
 *       per manifest occurrence) and the GC map covers all retained epochs —
 *       only reachable through the multi-epoch override, proving the wiring
 *       (plan guide #23: the component is genuinely called by its consumer).</li>
 * </ol>
 */
class TestCheckpointCoordinatorJdbcRetainedManifests {

    private static HikariDataSource dataSource;

    @TempDir
    Path tmp;

    private static final class CompletionLatch implements CheckpointListener {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile long completedId = -1;

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            completedId = checkpointId;
            latch.countDown();
        }

        @Override
        public void notifyCheckpointAborted(long checkpointId) {
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @Test
    void restoreSharedStateRegistryConsumesJdbcRetainedManifests() throws Exception {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        String jobId = "jdbc-retained";
        String pipelineId = "pipe";
        TaskLocation loc = new TaskLocation(jobId, pipelineId, "v1", 0);

        JdbcCheckpointStorage storage = new JdbcCheckpointStorage(jdbcTemplate);
        LocalFileSegmentStore segmentStore = new LocalFileSegmentStore(tmp.resolve("segments"));

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(5);
        config.setMinPause(0L);

        CheckpointCoordinator cc = new CheckpointCoordinator(
                jobId, pipelineId, new CheckpointIDCounter(), storage, config);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(segmentStore);
        cc.setTasksToAcknowledge(Collections.singleton(loc));

        // One stable RocksDB state: every incremental snapshot dedups onto the
        // same SST set, so retained manifests reference the SAME hashes with
        // growing reference counts.
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                tmp.resolve("db").toString(), String.class, 1, null);
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tmp.resolve("ckp").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(7L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(13L);

        long cp1 = runCheckpoint(cc, loc, backend.snapshotState());
        long cp2 = runCheckpoint(cc, loc, backend.snapshotState());
        long cp3 = runCheckpoint(cc, loc, backend.snapshotState());
        backend.close();

        Set<String> hashes = hashesOf(cc.getCheckpointSegments(cp3));
        assertFalse(hashes.isEmpty(), "incremental checkpoints must carry SST segments");
        cc.shutdown();

        // Retained-set precondition: the JDBC override (not the latest-only
        // default) must serve ALL three epochs.
        List<EpochManifest> retained = storage.loadRetainedEpochManifests(jobId, pipelineId, 5);
        assertEquals(3, retained.size(), "all three epochs retained in JDBC storage");
        assertEquals(cp3, retained.get(0).getEpochId(), "newest first");

        // Simulate restart: fresh coordinator over the SAME JDBC storage +
        // segment store; the real recovery path consumes the override.
        CheckpointCoordinator restarted = new CheckpointCoordinator(
                jobId, pipelineId, new CheckpointIDCounter(), storage, config);
        restarted.setIncrementalCheckpointEnabled(true);
        restarted.setSegmentStore(segmentStore);
        restarted.restoreSharedStateRegistry();

        SharedStateRegistry registry = restarted.getSharedStateRegistry();
        assertNotNull(registry, "registry rebuilt on the JDBC backend");
        for (String hash : hashes) {
            assertEquals(3, registry.getReferenceCount(hash),
                    "each retained manifest referencing the shared SST must contribute one reference "
                            + "(3 epochs → ref-count 3) — reachable ONLY through the multi-epoch retained set");
        }
        // GC map rebuilt for every retained epoch (drives subsumption cleanup).
        assertEquals(hashes, hashesOf(restarted.getCheckpointSegments(cp1)));
        assertEquals(hashes, hashesOf(restarted.getCheckpointSegments(cp2)));
        assertEquals(hashes, hashesOf(restarted.getCheckpointSegments(cp3)));
        restarted.shutdown();
    }

    /**
     * Item 33 Phase 3 (JDBC boundedness equivalent): after many completions
     * (6 rounds) with {@code maxRetained=3} the manifest plane in
     * {@code stream_epoch_manifest} converges to the newest 3 rows (retention
     * runs on the dedicated executor — bounded async wait, flaky-safe terminal
     * assertion), the checkpoint plane converges likewise, and the restart
     * recovery semantics do NOT regress: a fresh coordinator's
     * {@code restoreSharedStateRegistry()} rebuilds ref-count 3 for the shared
     * SST hashes from the pruned retained set (every retained manifest still
     * references the stable state's deduped hashes).
     */
    @Test
    void manifestRowsStayBoundedAndRestoreStillRebuildsRefCount() throws Exception {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        String jobId = "jdbc-retained-bounded";
        String pipelineId = "pipe";
        TaskLocation loc = new TaskLocation(jobId, pipelineId, "v1", 0);
        int maxRetained = 3;

        JdbcCheckpointStorage storage = new JdbcCheckpointStorage(jdbcTemplate);
        LocalFileSegmentStore segmentStore = new LocalFileSegmentStore(tmp.resolve("segments-b"));

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(maxRetained);
        config.setMinPause(0L);

        CheckpointCoordinator cc = new CheckpointCoordinator(
                jobId, pipelineId, new CheckpointIDCounter(), storage, config);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(segmentStore);
        cc.setTasksToAcknowledge(Collections.singleton(loc));

        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                tmp.resolve("db-b").toString(), String.class, 1, null);
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tmp.resolve("ckp-b").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(7L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(13L);

        long newest = -1;
        for (int i = 0; i < 6; i++) {
            newest = runCheckpoint(cc, loc, backend.snapshotState());
        }
        backend.close();

        Set<String> hashes = hashesOf(cc.getCheckpointSegments(newest));
        assertFalse(hashes.isEmpty(), "incremental checkpoints must carry SST segments");
        cc.shutdown();
        final long newestFinal = newest;
        final JdbcCheckpointStorage finalStorage = storage;

        // Converged terminal state (bounded async wait): both planes bounded to
        // maxRetained — manifest rows via the retained-set read (count > bound).
        assertTrue(awaitCondition(() -> finalStorage.loadRetainedEpochManifests(
                        jobId, pipelineId, 100).size() <= maxRetained, 15_000),
                "manifest rows must converge to <= maxRetained=" + maxRetained + " after 6 completions (was "
                        + finalStorage.loadRetainedEpochManifests(jobId, pipelineId, 100).size() + ")");
        assertTrue(awaitCondition(() -> finalStorage.getAllCheckpoints(jobId).size() <= maxRetained, 15_000),
                "checkpoint rows must converge to <= maxRetained=" + maxRetained);

        List<EpochManifest> retained = storage.loadRetainedEpochManifests(jobId, pipelineId, maxRetained);
        assertEquals(maxRetained, retained.size(), "retained set serves the full newest-N set");
        assertEquals(newestFinal, retained.get(0).getEpochId(), "newest manifest retained first");
        assertEquals(newestFinal - 1, retained.get(1).getEpochId());
        assertEquals(newestFinal - 2, retained.get(2).getEpochId());

        // Restart recovery over the pruned plane: ref-count semantics NOT regressed.
        CheckpointCoordinator restarted = new CheckpointCoordinator(
                jobId, pipelineId, new CheckpointIDCounter(), storage, config);
        restarted.setIncrementalCheckpointEnabled(true);
        restarted.setSegmentStore(segmentStore);
        restarted.restoreSharedStateRegistry();

        SharedStateRegistry registry = restarted.getSharedStateRegistry();
        assertNotNull(registry, "registry rebuilt over the pruned retained set");
        for (String hash : hashes) {
            assertEquals(maxRetained, registry.getReferenceCount(hash),
                    "each of the " + maxRetained + " retained manifests referencing the shared SST must "
                            + "contribute one reference after pruning (ref-count semantics not regressed)");
        }
        restarted.shutdown();
    }

    private static boolean awaitCondition(java.util.function.BooleanSupplier predicate, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return predicate.getAsBoolean();
    }

    private long runCheckpoint(CheckpointCoordinator cc, TaskLocation loc, StateSnapshot keyed) throws Exception {
        CompletionLatch latch = new CompletionLatch();
        cc.addListener(latch);
        long cpId = cc.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT).getCheckpointId();
        TaskStateSnapshot ts = new TaskStateSnapshot(loc);
        ts.putKeyedState("keyed-state", keyed);
        cc.acknowledgeTask(loc, cpId, ts);
        assertTrue(latch.await(20, TimeUnit.SECONDS), "checkpoint " + cpId + " did not complete in time");
        return cpId;
    }

    private Set<String> hashesOf(List<StateSegmentDescriptor> segs) {
        Set<String> s = new HashSet<>();
        for (StateSegmentDescriptor seg : segs) {
            s.add(seg.getPath());
        }
        return s;
    }
}
