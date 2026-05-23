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
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.checkpoint.*;
import org.junit.jupiter.api.*;

import java.util.List;

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
        } catch (Exception ignored) {
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
