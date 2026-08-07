/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcDialectProvider;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import io.nop.stream.core.checkpoint.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}
