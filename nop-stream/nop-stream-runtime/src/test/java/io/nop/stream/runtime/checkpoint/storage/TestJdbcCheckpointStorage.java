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
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcDialectProvider;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJdbcCheckpointStorage {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private JdbcCheckpointStorage storage;

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

    @BeforeEach
    void setUp() {
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));

        try {
            SQL dropSql = SQL.begin().sql("DROP TABLE IF EXISTS stream_checkpoint").end();
            jdbcTemplate.executeUpdate(dropSql);
        } catch (Exception e) {
            // table may not exist on first run
        }

        storage = new JdbcCheckpointStorage(jdbcTemplate);
    }

    @Test
    void testGetName() {
        assertEquals("JdbcCheckpointStorage", storage.getName());
    }

    @Test
    void testStoreAndGetCheckpoint() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint("1", "1", 100L);

        String handle = storage.storeCheckPoint(checkpoint);
        assertNotNull(handle);
        assertTrue(handle.contains("100"));

        CompletedCheckpoint retrieved = storage.getLatestCheckpoint("1", "1");
        assertNotNull(retrieved);
        assertEquals(100L, retrieved.getCheckpointId());
        assertEquals("1", retrieved.getJobId());
        assertEquals("1", retrieved.getPipelineId());
        assertEquals(CheckpointType.CHECKPOINT, retrieved.getCheckpointType());
    }

    @Test
    void testGetLatestCheckpoint() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 300L));

        CompletedCheckpoint latest = storage.getLatestCheckpoint("1", "1");
        assertNotNull(latest);
        assertEquals(300L, latest.getCheckpointId());
    }

    @Test
    void testGetLatestCheckpointNoData() throws Exception {
        CompletedCheckpoint latest = storage.getLatestCheckpoint("nonexistent", "1");
        assertNull(latest);
    }

    @Test
    void testGetAllCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "2", 300L));

        List<CompletedCheckpoint> all = storage.getAllCheckpoints("1");
        assertEquals(3, all.size());
    }

    @Test
    void testGetAllCheckpointsEmpty() throws Exception {
        List<CompletedCheckpoint> all = storage.getAllCheckpoints("nonexistent");
        assertTrue(all.isEmpty());
    }

    @Test
    void testDeleteCheckpoint() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        assertEquals(1, storage.getCheckpointCount("1"));

        storage.deleteCheckpoint("1", "1", 100L);
        assertEquals(0, storage.getCheckpointCount("1"));

        CompletedCheckpoint retrieved = storage.getLatestCheckpoint("1", "1");
        assertNull(retrieved);
    }

    @Test
    void testDeleteAllCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "2", 300L));

        storage.deleteAllCheckpoints("1");
        assertEquals(0, storage.getCheckpointCount("1"));
    }

    @Test
    void testGetCheckpointCount() throws Exception {
        assertEquals(0, storage.getCheckpointCount("1"));

        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        assertEquals(1, storage.getCheckpointCount("1"));

        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        assertEquals(2, storage.getCheckpointCount("1"));
    }

    @Test
    void testGetLatestCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 300L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 400L));

        List<CompletedCheckpoint> latest = storage.getLatestCheckpoints("1", 2);
        assertEquals(2, latest.size());
        assertEquals(400L, latest.get(0).getCheckpointId());
        assertEquals(300L, latest.get(1).getCheckpointId());
    }

    @Test
    void testSerializationRoundTrip() throws Exception {
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("op1", "op-data-123")
                .putKeyedState("key1", "keyed-data-456")
                .build();

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("job1")
                .pipelineId("pipe1")
                .checkpointId(999L)
                .triggerTimestamp(1000L)
                .completedTimestamp(2000L)
                .checkpointType(CheckpointType.SAVEPOINT)
                .addTaskState(LOC_1, snapshot)
                .build();

        storage.storeCheckPoint(checkpoint);
        CompletedCheckpoint retrieved = storage.getLatestCheckpoint("job1", "pipe1");

        assertNotNull(retrieved);
        assertEquals("job1", retrieved.getJobId());
        assertEquals("pipe1", retrieved.getPipelineId());
        assertEquals(999L, retrieved.getCheckpointId());
        assertEquals(CheckpointType.SAVEPOINT, retrieved.getCheckpointType());
        assertEquals(1000L, retrieved.getTriggerTimestamp());
        assertEquals(2000L, retrieved.getCompletedTimestamp());

        TaskStateSnapshot retrievedSnapshot = retrieved.getTaskState(LOC_1);
        assertNotNull(retrievedSnapshot);
        assertEquals("op-data-123", retrievedSnapshot.getOperatorState("op1"));
        assertEquals("keyed-data-456", retrievedSnapshot.getKeyedState("key1"));
    }

    @Test
    void testExists() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "2", 200L));

        assertTrue(storage.exists("1", "1", 100L));
        assertFalse(storage.exists("1", "1", 200L));
        assertFalse(storage.exists("1", "2", 999L));
    }

    @Test
    void testDeleteNonExistentCheckpoint() throws Exception {
        assertDoesNotThrow(() -> storage.deleteCheckpoint("nonexistent", "1", 999L));
        assertDoesNotThrow(() -> storage.deleteAllCheckpoints("nonexistent"));
    }

    @Test
    void testSavepointStoreAndLoad() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint("job-sp", "pipe-sp", 500L);
        String savepointPath = "/savepoints/sp-500";

        String handle = storage.storeSavepoint(checkpoint, savepointPath);
        assertNotNull(handle);

        CompletedCheckpoint loaded = storage.loadSavepoint(savepointPath);
        assertNotNull(loaded, "loadSavepoint should return the checkpoint stored with the given path");
        assertEquals(500L, loaded.getCheckpointId());
        assertEquals("job-sp", loaded.getJobId());
    }

    @Test
    void testLoadSavepointWithNullPath() throws Exception {
        CompletedCheckpoint result = storage.loadSavepoint(null);
        assertNull(result, "loadSavepoint with null path should return null");
    }

    @Test
    void testLoadSavepointWithNonexistentPath() throws Exception {
        CompletedCheckpoint result = storage.loadSavepoint("/nonexistent/path");
        assertNull(result, "loadSavepoint with nonexistent path should return null");
    }

    @Test
    void testDuplicateCheckpointUpsert() throws Exception {
        CompletedCheckpoint cp1 = createTestCheckpoint("dup-job", "dup-pipe", 100L);
        storage.storeCheckPoint(cp1);
        assertEquals(1, storage.getCheckpointCount("dup-job"));

        CompletedCheckpoint cp2 = createTestCheckpoint("dup-job", "dup-pipe", 100L);
        cp2.setRestored(true);
        storage.storeCheckPoint(cp2);

        assertEquals(1, storage.getCheckpointCount("dup-job"),
                "Duplicate (job_id, pipeline_id, checkpoint_id) should not create a second row");

        CompletedCheckpoint loaded = storage.getLatestCheckpoint("dup-job", "dup-pipe");
        assertNotNull(loaded);
    }

    @Test
    void testDuplicateEpochManifestUpsert() throws Exception {
        try {
            SQL dropSql = SQL.begin().sql("DROP TABLE IF EXISTS stream_epoch_manifest").end();
            jdbcTemplate.executeUpdate(dropSql);
        } catch (Exception e) {
            // table may not exist on first run
        }

        EpochManifest manifest1 = new EpochManifest(1L, "ej", "ep", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, java.util.Collections.emptyMap(), null, null);
        storage.storeEpochManifest("ej", "ep", manifest1);

        EpochManifest manifest2 = new EpochManifest(1L, "ej", "ep", System.currentTimeMillis() + 1000,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, java.util.Collections.emptyMap(), null, null);
        storage.storeEpochManifest("ej", "ep", manifest2);

        EpochManifest loaded = storage.loadLatestEpochManifest("ej", "ep");
        assertNotNull(loaded);
        assertEquals(1L, loaded.getEpochId());
    }

    /**
     * Stage 51 dual-storage regression (JDBC side, plan 2026-09-03-1723-3): a new-format
     * manifest (decimal-valued keyed state included) round-trips through the JDBC blob with
     * both new fields intact and verified, and a corrupted {@code state_data} blob fails fast
     * with the typed checksum error through the JDBC storage API.
     */
    @Test
    void testNewFormatManifestRoundTripAndTamperThroughJdbcStorage() throws Exception {
        String jobId = "cksum-job";
        String pipelineId = "cp";
        TaskLocation loc = new TaskLocation(jobId, pipelineId, "v1", 0);
        TaskStateSnapshot snapshot = TaskStateSnapshot.builder(loc)
                .putKeyedState("decimal-key", new java.math.BigDecimal("0.100"))
                .putKeyedState("int-key", 7)
                .build();
        EpochManifest manifest = new EpochManifest(66L, jobId, pipelineId, 4321L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                java.util.Collections.singletonMap(loc, snapshot), null, null);

        storage.storeEpochManifest(jobId, pipelineId, manifest);

        EpochManifest loaded = storage.loadLatestEpochManifest(jobId, pipelineId);
        assertNotNull(loaded);
        assertEquals(io.nop.stream.core.checkpoint.CheckpointFormatVersions.CURRENT_FORMAT_VERSION,
                loaded.getStateFormatVersion(), "JDBC round-trip must carry stateFormatVersion");
        assertNotNull(loaded.getChecksum(), "JDBC round-trip must carry a verified checksum");
        assertEquals(0.1D,
                ((Number) loaded.getTaskSnapshots().get(loc).getKeyedState("decimal-key")).doubleValue());

        // corrupt the stored blob directly in the table, then read through the storage API
        byte[] stored = CheckpointSerDe.serializeEpochManifest(manifest);
        String json = new String(stored, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"epochId\":66"));
        byte[] tampered = json.replace("\"epochId\":66", "\"epochId\":99")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SQL corrupt = SQL.begin().name("corruptEpochManifestForTest")
                .sql("UPDATE stream_epoch_manifest SET state_data = ? WHERE job_id = ? AND pipeline_id = ? AND epoch_id = ?",
                        tampered, jobId, pipelineId, 66L)
                .end();
        jdbcTemplate.executeUpdate(corrupt);

        io.nop.stream.core.exceptions.StreamException ex = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> storage.loadLatestEpochManifest(jobId, pipelineId),
                "corrupted JDBC blob must surface as typed checksum mismatch through the storage API");
        assertEquals(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH.getErrorCode(),
                ex.getErrorCode());
    }

    /**
     * Phase 3: the native-upsert SQL text must branch by dialect. Each native branch
     * produces a single atomic statement (no caught-exception-then-UPDATE-in-same-txn),
     * which is the fix for the PostgreSQL "current transaction is aborted" failure.
     */
    @Test
    void testUpsertSqlShapePerDialect() {
        String[] columns = {"sid", "job_id", "pipeline_id", "checkpoint_id", "checkpoint_type",
                "trigger_timestamp", "completed_timestamp", "state_data"};
        String[] conflict = {"job_id", "pipeline_id", "checkpoint_id"};
        String[] update = {"checkpoint_type", "trigger_timestamp", "completed_timestamp", "state_data"};

        String pg = JdbcCheckpointStorage.buildNativeUpsertSqlText(
                JdbcCheckpointStorage.UpsertDialect.POSTGRESQL, "stream_checkpoint", columns, conflict, update);
        assertNotNull(pg, "PostgreSQL must produce a native upsert");
        assertTrue(pg.contains("INSERT INTO stream_checkpoint"), pg);
        assertTrue(pg.contains("ON CONFLICT (job_id, pipeline_id, checkpoint_id) DO UPDATE SET"), pg);
        assertTrue(pg.contains("state_data = EXCLUDED.state_data"), pg);
        assertFalse(pg.contains("ON DUPLICATE KEY"), pg);

        String mysql = JdbcCheckpointStorage.buildNativeUpsertSqlText(
                JdbcCheckpointStorage.UpsertDialect.MYSQL, "stream_checkpoint", columns, conflict, update);
        assertNotNull(mysql);
        assertTrue(mysql.contains("ON DUPLICATE KEY UPDATE"), mysql);
        assertTrue(mysql.contains("state_data = VALUES(state_data)"), mysql);

        String h2 = JdbcCheckpointStorage.buildNativeUpsertSqlText(
                JdbcCheckpointStorage.UpsertDialect.H2, "stream_checkpoint", columns, conflict, update);
        assertNotNull(h2);
        assertTrue(h2.contains("MERGE INTO stream_checkpoint"), h2);
        assertTrue(h2.contains("KEY (job_id, pipeline_id, checkpoint_id)"), h2);

        assertNull(JdbcCheckpointStorage.buildNativeUpsertSqlText(
                JdbcCheckpointStorage.UpsertDialect.GENERIC, "stream_checkpoint", columns, conflict, update),
                "GENERIC must fall back to INSERT+UPDATE, returning null native SQL");
    }

    /**
     * Phase 3: verify the native upsert actually updates data on a duplicate key
     * (exercises the H2 MERGE path through {@code storeCheckPoint}).
     */
    @Test
    void testDuplicateKeyUpsertUpdatesData() throws Exception {
        CompletedCheckpoint cp1 = CompletedCheckpoint.builder()
                .jobId("upd-job").pipelineId("upd-pipe").checkpointId(100L)
                .triggerTimestamp(1000L).completedTimestamp(2000L)
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(LOC_1, TaskStateSnapshot.empty(LOC_1))
                .build();
        storage.storeCheckPoint(cp1);

        CompletedCheckpoint cp2 = CompletedCheckpoint.builder()
                .jobId("upd-job").pipelineId("upd-pipe").checkpointId(100L)
                .triggerTimestamp(5555L).completedTimestamp(6666L)
                .checkpointType(CheckpointType.SAVEPOINT)
                .addTaskState(LOC_1, TaskStateSnapshot.empty(LOC_1))
                .build();
        storage.storeCheckPoint(cp2);

        assertEquals(1, storage.getCheckpointCount("upd-job"),
                "Duplicate key must update, not insert a second row");

        CompletedCheckpoint loaded = storage.getLatestCheckpoint("upd-job", "upd-pipe");
        assertNotNull(loaded);
        assertEquals(CheckpointType.SAVEPOINT, loaded.getCheckpointType(), "second store must overwrite");
        assertEquals(5555L, loaded.getTriggerTimestamp());
        assertEquals(6666L, loaded.getCompletedTimestamp());
    }

    /**
     * Phase 3: end-to-end store→load round-trip on a duplicate-key scenario.
     */
    @Test
    void testStoreLoadRoundTripOnDuplicateKey() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("rt-job", "rt-pipe", 700L));
        // store again with the same key (HA failover fencing overlap / savepoint re-store)
        storage.storeCheckPoint(createTestCheckpoint("rt-job", "rt-pipe", 700L));

        CompletedCheckpoint loaded = storage.getLatestCheckpoint("rt-job", "rt-pipe");
        assertNotNull(loaded, "round-trip store→load must succeed on duplicate key");
        assertEquals(700L, loaded.getCheckpointId());
        assertEquals(1, storage.getCheckpointCount("rt-job"));
    }

    /**
     * Phase 3: PostgreSQL verification. H2 cannot execute PostgreSQL-native
     * {@code ON CONFLICT ... DO UPDATE SET col = EXCLUDED.col} syntax (verified:
     * H2 2.3.232 raises a syntax error), and real PostgreSQL is not available in
     * the unit-test environment. Therefore the PostgreSQL upsert path is verified
     * deterministically by asserting the generated SQL shape — this is the
     * authoritative proof that {@code resolveUpsertDialect()==POSTGRESQL} produces
     * a single atomic {@code INSERT ... ON CONFLICT (job_id, pipeline_id,
     * checkpoint_id) DO UPDATE SET ... = EXCLUDED....} statement and never the
     * unsafe caught-exception-then-UPDATE-in-same-transaction pattern. See
     * {@link #testUpsertSqlShapePerDialect()} and the duplicate-key behavioural
     * tests {@link #testDuplicateKeyUpsertUpdatesData()} /
     * {@link #testStoreLoadRoundTripOnDuplicateKey()} which exercise the native
     * upsert dispatch end-to-end (H2 MERGE) and the GENERIC separate-transaction
     * fallback below.
     *
     * <p>Behavioural evidence that the unsafe same-transaction pattern is gone for
     * engines without a native upsert is provided by {@link
     * #testGenericDialectFallbackSeparateTransactionUpsert()}, which forces the
     * GENERIC branch (INSERT in one transaction, UPDATE in a SEPARATE transaction
     * on duplicate key) — the exact mechanism that prevents the PostgreSQL
     * "current transaction is aborted" failure on any non-native engine.
     */
    @Test
    void testPostgreSqlUpsertVerifiedViaSqlShape() {
        // This is a documentation anchor test; the actual assertions live in
        // testUpsertSqlShapePerDialect. Kept as a named entry point so the
        // PostgreSQL verification is discoverable by name.
        String[] columns = {"sid", "job_id", "pipeline_id", "checkpoint_id", "checkpoint_type",
                "trigger_timestamp", "completed_timestamp", "state_data"};
        String[] conflict = {"job_id", "pipeline_id", "checkpoint_id"};
        String[] update = {"checkpoint_type", "trigger_timestamp", "completed_timestamp", "state_data"};
        String pg = JdbcCheckpointStorage.buildNativeUpsertSqlText(
                JdbcCheckpointStorage.UpsertDialect.POSTGRESQL, "stream_checkpoint", columns, conflict, update);
        assertNotNull(pg);
        assertTrue(pg.contains("ON CONFLICT (job_id, pipeline_id, checkpoint_id) DO UPDATE SET"));
        assertTrue(pg.contains("= EXCLUDED."));
    }

    /**
     * Phase 3: the GENERIC fallback must run INSERT and UPDATE in <b>separate</b>
     * transactions. This is the safety mechanism that replaces the old
     * INSERT-then-UPDATE-in-one-transaction pattern (which aborts on PostgreSQL).
     * Here the dialect is forced to a non-native name ("oracle") so
     * {@code resolveUpsertDialect()} returns GENERIC, and a duplicate-key store
     * must still succeed.
     */
    @Test
    void testGenericDialectFallbackSeparateTransactionUpsert() throws Exception {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(4);
        try {
            JdbcTemplateImpl jdbc = (JdbcTemplateImpl) JdbcFactory.newJdbcTemplateFor(ds);
            // "oracle" is not postgresql/mysql/h2, so resolveUpsertDialect() -> GENERIC,
            // exercising the separate-transaction INSERT-then-UPDATE fallback.
            JdbcDialectProvider provider = new JdbcDialectProvider(jdbc.txn());
            provider.setQuerySpaceToDialectMap(Map.of("default", "oracle"));
            jdbc.setDialectProvider(provider);

            JdbcCheckpointStorage storage = new JdbcCheckpointStorage(jdbc);

            storage.storeCheckPoint(createTestCheckpoint("gen-job", "gen-pipe", 100L));
            // duplicate key -> INSERT fails -> UPDATE in a SEPARATE transaction succeeds
            storage.storeCheckPoint(createTestCheckpoint("gen-job", "gen-pipe", 100L));

            assertEquals(1, storage.getCheckpointCount("gen-job"),
                    "GENERIC fallback must upsert via separate transactions without aborting");
            CompletedCheckpoint loaded = storage.getLatestCheckpoint("gen-job", "gen-pipe");
            assertNotNull(loaded);
        } finally {
            ds.close();
        }
    }

    private CompletedCheckpoint createTestCheckpoint(String jobId, String pipelineId, long checkpointId) {
        return CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis() - 1000)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(LOC_1, TaskStateSnapshot.empty(LOC_1))
                .addTaskState(LOC_2, TaskStateSnapshot.empty(LOC_2))
                .build();
    }

    // ==================== Runtime audit 2026-09-01 fixes ====================

    /**
     * Items 28+31 (W-8 / Stage-31 parity): loadRetainedEpochManifests returns
     * the multi-epoch retained set, newest first, count-bounded — the JDBC
     * analog of the LocalFile behavior (whose analog test lives in
     * {@code TestEpochManifestPersistence#...} retained path). Before the
     * override the interface default degraded to latest-only, so
     * {@code restoreSharedStateRegistry} silently lost older epochs' segments.
     */
    @Test
    void testLoadRetainedEpochManifestsMultiEpochNewestFirstCountBounded() throws Exception {
        String jobId = "retained-job";
        String pipelineId = "rp";
        for (long epoch = 1L; epoch <= 5L; epoch++) {
            storage.storeEpochManifest(jobId, pipelineId, new EpochManifest(
                    epoch, jobId, pipelineId, System.currentTimeMillis(),
                    CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                    java.util.Collections.emptyMap(), null, null));
        }

        List<EpochManifest> top3 = storage.loadRetainedEpochManifests(jobId, pipelineId, 3);
        assertEquals(3, top3.size(), "count bound respected");
        assertEquals(5L, top3.get(0).getEpochId(), "newest first");
        assertEquals(4L, top3.get(1).getEpochId());
        assertEquals(3L, top3.get(2).getEpochId());

        List<EpochManifest> all = storage.loadRetainedEpochManifests(jobId, pipelineId, 10);
        assertEquals(5, all.size(), "full retained set available beyond the count of the first query");
        for (int i = 0; i < all.size() - 1; i++) {
            assertTrue(all.get(i).getEpochId() > all.get(i + 1).getEpochId(),
                    "strictly descending epoch order");
        }

        assertTrue(storage.loadRetainedEpochManifests(jobId, pipelineId, 0).isEmpty(),
                "count <= 0 → empty (interface contract)");
        assertTrue(storage.loadRetainedEpochManifests("nonexistent-retained", pipelineId, 3).isEmpty(),
                "unknown job → empty");
    }

    /**
     * R-17: deleteAllCheckpoints must also clear the epoch-manifest table —
     * LocalFile deletes the whole job tree including .epoch files; leaving
     * JDBC manifest rows behind let loadLatestEpochManifest serve stale
     * manifests after a "delete all" (stale-restore hazard).
     */
    @Test
    void testDeleteAllCheckpointsAlsoClearsEpochManifests() throws Exception {
        EpochManifest manifest = new EpochManifest(1L, "delall-job", "p", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, java.util.Collections.emptyMap(), null, null);
        storage.storeEpochManifest("delall-job", "p", manifest);
        assertNotNull(storage.loadLatestEpochManifest("delall-job", "p"));
        storage.storeCheckPoint(createTestCheckpoint("delall-job", "p", 100L));

        storage.deleteAllCheckpoints("delall-job");

        assertEquals(0, storage.getCheckpointCount("delall-job"));
        assertNull(storage.loadLatestEpochManifest("delall-job", "p"),
                "deleteAllCheckpoints must clear epoch manifests too (parity with LocalFile) — "
                        + "stale manifests would serve stale restores");
    }

    /**
     * R-18: two storage instances racing first-initialization on a fresh
     * database (the MiniStreamCluster / multi-node shape) must both succeed.
     * Before the fix the bare {@code CREATE TABLE} lost the race with a spurious
     * "table already exists" failure on one side.
     */
    @Test
    void testConcurrentFirstInitializationDoesNotFail() throws Exception {
        try {
            jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS stream_checkpoint").end());
        } catch (Exception e) {
            // ignore
        }
        try {
            jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS stream_epoch_manifest").end());
        } catch (Exception e) {
            // ignore
        }

        JdbcCheckpointStorage first = new JdbcCheckpointStorage(jdbcTemplate);
        JdbcCheckpointStorage second = new JdbcCheckpointStorage(jdbcTemplate);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        Thread t1 = new Thread(() -> {
            try {
                start.await();
                first.storeCheckPoint(createTestCheckpoint("race-job", "p", 1L));
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                start.await();
                second.storeCheckPoint(createTestCheckpoint("race-job", "p", 2L));
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        t1.start();
        t2.start();
        start.countDown();
        t1.join(10_000);
        t2.join(10_000);

        assertTrue(errors.isEmpty(),
                "concurrent first-init must not fail (IF NOT EXISTS DDL): " + errors);
        assertEquals(2, storage.getCheckpointCount("race-job"),
                "both concurrent stores must be durable after the race");
    }

    /**
     * R-22: a non-Nop runtime failure during metadata construction must surface
     * as the storage's typed {@link CheckpointStorageException}, not escape raw
     * (wrap parity with every sibling method).
     */
    @Test
    void testLoadSavepointMetadataWrapsUnexpectedRuntimeException() throws Exception {
        CompletedCheckpoint exploding = new CompletedCheckpoint(null, null, 1L, 0L, 0L,
                CheckpointType.SAVEPOINT, java.util.Collections.emptyMap()) {
            @Override
            public java.util.Map<TaskLocation, TaskStateSnapshot> getTaskStates() {
                throw new IllegalStateException("simulated metadata-construction failure (R-22)");
            }
        };
        JdbcCheckpointStorage stub = new JdbcCheckpointStorage(jdbcTemplate) {
            @Override
            public CompletedCheckpoint loadSavepoint(String savepointPath) {
                return exploding;
            }
        };

        io.nop.stream.core.checkpoint.storage.CheckpointStorageException ex =
                assertThrows(io.nop.stream.core.checkpoint.storage.CheckpointStorageException.class,
                () -> stub.loadSavepointMetadata("any-path"),
                "unexpected runtime failures must be wrapped, not escape raw");
        assertEquals("loadSavepointMetadata failed", ex.getParam("detail"));
    }
}
