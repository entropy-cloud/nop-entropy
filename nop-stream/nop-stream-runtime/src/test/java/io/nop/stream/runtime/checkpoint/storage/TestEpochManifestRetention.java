/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 33 focused verification (storage level, §9.2 D1/D2): the
 * {@code pruneEpochManifests} retention face on BOTH production storages.
 *
 * <ul>
 *   <li><b>① LocalFile</b> — 8 manifests stored, prune(3) → the pipeline dir holds
 *       exactly the newest 3 {@code .epoch} files (file-name assertion), and the
 *       returned pruned-id list is the observed stale tail (5..1, newest-first).</li>
 *   <li><b>② JDBC</b> — equivalent row-count assertion on H2: after prune(3) only
 *       the newest 3 rows remain (served through {@code loadRetainedEpochManifests}).</li>
 *   <li><b>③ Retained-set consistency</b> — after pruning, the
 *       {@code loadRetainedEpochManifests} result EQUALS the prune keep set on both
 *       storages (the restore read set is pinned to the prune keep set — same
 *       newest-N ordering, checkpoint-design §9.2 D2).</li>
 * </ul>
 *
 * <p>Also pins: prune below the bound is a no-op returning an empty list, and the
 * epoch ids carried by the round-tripped manifests are preserved (D2 anchor).
 */
class TestEpochManifestRetention {

    private static final TaskLocation LOC_1 = new TaskLocation("job-1", "pipe-1", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("job-1", "pipe-1", "v2", 1);

    private static HikariDataSource dataSource;

    @TempDir
    Path tempDir;

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

    private EpochManifest createTestManifest(String jobId, String pipelineId, long epochId) {
        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(LOC_1, TaskStateSnapshot.empty(LOC_1));
        taskSnapshots.put(LOC_2, TaskStateSnapshot.empty(LOC_2));
        return new EpochManifest(epochId, jobId, pipelineId, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, taskSnapshots, null, null);
    }

    private void storeEightManifests(io.nop.stream.core.checkpoint.storage.ICheckpointStorage storage,
                                     String jobId, String pipelineId) throws Exception {
        for (long epoch = 1L; epoch <= 8L; epoch++) {
            storage.storeEpochManifest(jobId, pipelineId, createTestManifest(jobId, pipelineId, epoch));
        }
    }

    // ------------------------------------------------------------------
    // ① LocalFile: file-name-level pruning assertion
    // ------------------------------------------------------------------

    @Test
    void localFilePruneKeepsNewestNEpochFiles() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        String jobId = "job-1";
        String pipelineId = "pipe-1";
        storeEightManifests(storage, jobId, pipelineId);

        Path pipelineDir = tempDir.resolve(jobId).resolve(pipelineId);
        assertEquals(8, countEpochFiles(pipelineDir), "all 8 manifests stored before pruning");

        List<Long> pruned = storage.pruneEpochManifests(jobId, pipelineId, 3);

        // Pruned ids = observed stale tail, newest-first ordering (5,4,3,2,1).
        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), pruned,
                "prune must return the observed stale tail newest-first");

        // File-name assertion: exactly the newest 3 .epoch files remain.
        TreeSet<String> names = new TreeSet<>();
        try (var stream = Files.list(pipelineDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".epoch"))
                    .forEach(p -> names.add(p.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(List.of("6.epoch", "7.epoch", "8.epoch")), names,
                "pipeline dir must hold exactly the newest 3 .epoch files");
    }

    @Test
    void localFilePruneBelowBoundIsNoOp() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        storage.storeEpochManifest("job-1", "pipe-1", createTestManifest("job-1", "pipe-1", 1L));
        storage.storeEpochManifest("job-1", "pipe-1", createTestManifest("job-1", "pipe-1", 2L));

        List<Long> pruned = storage.pruneEpochManifests("job-1", "pipe-1", 5);

        assertTrue(pruned.isEmpty(), "prune below the bound is a no-op returning an empty list");
        assertEquals(2, countEpochFiles(tempDir.resolve("job-1").resolve("pipe-1")),
                "nothing deleted below the bound");
    }

    @Test
    void localFilePruneOnMissingDirectoryIsNoOp() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        assertTrue(storage.pruneEpochManifests("no-such-job", "pipe-1", 3).isEmpty(),
                "pruning a job with no manifest directory is a no-op");
    }

    // ------------------------------------------------------------------
    // ② JDBC: row-count-level pruning assertion (H2)
    // ------------------------------------------------------------------

    @Test
    void jdbcPruneBoundsManifestRowsToNewestN() throws Exception {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        JdbcCheckpointStorage storage = new JdbcCheckpointStorage(jdbcTemplate);
        String jobId = "prune-job";
        String pipelineId = "p";
        storeEightManifests(storage, jobId, pipelineId);
        assertEquals(8, storage.loadRetainedEpochManifests(jobId, pipelineId, 10).size(),
                "all 8 manifest rows stored before pruning");

        List<Long> pruned = storage.pruneEpochManifests(jobId, pipelineId, 3);

        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), pruned,
                "prune must return the observed stale tail newest-first");
        List<EpochManifest> remaining = storage.loadRetainedEpochManifests(jobId, pipelineId, 10);
        assertEquals(3, remaining.size(), "only the newest 3 manifest rows remain");
        assertEquals(8L, remaining.get(0).getEpochId(), "newest first");
        assertEquals(7L, remaining.get(1).getEpochId());
        assertEquals(6L, remaining.get(2).getEpochId());
    }

    @Test
    void jdbcPruneBelowBoundIsNoOp() throws Exception {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        JdbcCheckpointStorage storage = new JdbcCheckpointStorage(jdbcTemplate);
        storage.storeEpochManifest("prune-job-2", "p", createTestManifest("prune-job-2", "p", 1L));

        assertTrue(storage.pruneEpochManifests("prune-job-2", "p", 5).isEmpty(),
                "prune below the bound is a no-op returning an empty list");
        assertEquals(1, storage.loadRetainedEpochManifests("prune-job-2", "p", 10).size());
    }

    // ------------------------------------------------------------------
    // ③ Retained set == prune keep set (restore read set pinned, D2)
    // ------------------------------------------------------------------

    @Test
    void localFileRetainedSetEqualsPruneKeepSet() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        String jobId = "job-1";
        String pipelineId = "pipe-1";
        storeEightManifests(storage, jobId, pipelineId);

        storage.pruneEpochManifests(jobId, pipelineId, 3);

        // The restore read set (count = maxRetainedCheckpoints semantics) is exactly
        // the keep set — same newest-N ordering on both faces (§9.2 D2).
        List<EpochManifest> retained = storage.loadRetainedEpochManifests(jobId, pipelineId, 3);
        assertEquals(3, retained.size());
        assertEquals(List.of(8L, 7L, 6L), epochsOf(retained),
                "retained set must equal the prune keep set, newest first");
        // Reading beyond the remaining rows returns the same set (nothing extra left).
        assertEquals(List.of(8L, 7L, 6L), epochsOf(storage.loadRetainedEpochManifests(jobId, pipelineId, 10)),
                "no stale manifests left behind the keep set");
    }

    @Test
    void jdbcRetainedSetEqualsPruneKeepSet() throws Exception {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        JdbcCheckpointStorage storage = new JdbcCheckpointStorage(jdbcTemplate);
        String jobId = "prune-job-3";
        String pipelineId = "p";
        storeEightManifests(storage, jobId, pipelineId);

        storage.pruneEpochManifests(jobId, pipelineId, 3);

        assertEquals(List.of(8L, 7L, 6L), epochsOf(storage.loadRetainedEpochManifests(jobId, pipelineId, 3)),
                "retained set must equal the prune keep set, newest first");
        assertEquals(List.of(8L, 7L, 6L), epochsOf(storage.loadRetainedEpochManifests(jobId, pipelineId, 10)),
                "no stale manifest rows left behind the keep set");
    }

    private static List<Long> epochsOf(List<EpochManifest> manifests) {
        List<Long> ids = new ArrayList<>();
        for (EpochManifest m : manifests) {
            ids.add(m.getEpochId());
        }
        return ids;
    }

    private static int countEpochFiles(Path pipelineDir) throws Exception {
        if (!Files.isDirectory(pipelineDir)) {
            return 0;
        }
        try (var stream = Files.list(pipelineDir)) {
            return (int) stream.filter(p -> p.getFileName().toString().endsWith(".epoch")).count();
        }
    }
}
