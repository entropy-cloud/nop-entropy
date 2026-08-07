/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;

/**
 * JDBC-backed checkpoint storage for durable checkpoint persistence.
 *
 * <p><b>Runtime dependency:</b> This class requires {@code nop-dao}
 * (specifically {@link io.nop.dao.jdbc.IJdbcTemplate}) on the classpath.
 * The dependency is declared as {@code provided} scope, so consumers
 * must include it explicitly when using JDBC-based checkpoint storage.</p>
 */
@Internal
public class JdbcCheckpointStorage implements ICheckpointStorage {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcCheckpointStorage.class);

    private static final String TABLE_NAME = "stream_checkpoint";
    private static final String EPOCH_TABLE_NAME = "stream_epoch_manifest";

    private static final String DEFAULT_QUERY_SPACE = "default";

    private static long sidSequence = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private volatile boolean tableInitialized;
    private volatile boolean epochTableInitialized;

    public JdbcCheckpointStorage(IJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_QUERY_SPACE);
    }

    public JdbcCheckpointStorage(IJdbcTemplate jdbcTemplate, String querySpace) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : DEFAULT_QUERY_SPACE;
    }

    @Override
    public String getName() {
        return "JdbcCheckpointStorage";
    }

    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
        try {
            ensureTable();
            byte[] stateData = serializeCheckpoint(checkpoint);
            long sid = nextSid();

            String[] columns = {"sid", "job_id", "pipeline_id", "checkpoint_id", "checkpoint_type",
                    "trigger_timestamp", "completed_timestamp", "state_data"};
            String[] conflictColumns = {"job_id", "pipeline_id", "checkpoint_id"};
            String[] updateColumns = {"checkpoint_type", "trigger_timestamp", "completed_timestamp", "state_data"};
            Object[] values = {sid, checkpoint.getJobId(), checkpoint.getPipelineId(), checkpoint.getCheckpointId(),
                    checkpoint.getCheckpointType().name(), checkpoint.getTriggerTimestamp(),
                    checkpoint.getCompletedTimestamp(), stateData};

            UpsertDialect dialect = resolveUpsertDialect();
            if (dialect == UpsertDialect.GENERIC) {
                SQL insert = SQL.begin().name("storeCheckpoint").querySpace(querySpace)
                        .sql("INSERT INTO " + TABLE_NAME +
                                " (sid, job_id, pipeline_id, checkpoint_id, checkpoint_type, trigger_timestamp, " +
                                "completed_timestamp, state_data) VALUES (?,?,?,?,?,?,?,?)", values).end();
                SQL update = SQL.begin().name("updateCheckpoint").querySpace(querySpace)
                        .sql("UPDATE " + TABLE_NAME +
                                " SET checkpoint_type = ?, trigger_timestamp = ?, completed_timestamp = ?, state_data = ?" +
                                " WHERE job_id = ? AND pipeline_id = ? AND checkpoint_id = ?",
                                checkpoint.getCheckpointType().name(), checkpoint.getTriggerTimestamp(),
                                checkpoint.getCompletedTimestamp(), stateData,
                                checkpoint.getJobId(), checkpoint.getPipelineId(), checkpoint.getCheckpointId())
                        .end();
                runInsertOrUpdateSeparateTxns(insert, update, "storeCheckpoint");
            } else {
                SQL upsert = buildNativeUpsert(dialect, "storeCheckpoint", TABLE_NAME,
                        columns, conflictColumns, updateColumns, values);
                runNativeUpsert(upsert);
            }

            return checkpoint.getJobId() + "_" + checkpoint.getCheckpointId();
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "storeCheckPoint failed");
        }
    }

    @Override
    public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return null;
            }

            SQL sql = SQL.begin().name("getLatestCheckpoint").querySpace(querySpace)
                    .sql("SELECT state_data FROM " + TABLE_NAME +
                            " WHERE job_id = ? AND pipeline_id = ?" +
                            " ORDER BY checkpoint_id DESC LIMIT 1", jobId, pipelineId)
                    .end();

            byte[][] result = {null};
            jdbcTemplate.executeQuery(sql, dataSet -> {
                for (IDataRow row : dataSet) {
                    result[0] = row.getBytes(0);
                    break;
                }
                return null;
            });
            return deserializeCheckpoint(result[0]);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "getLatestCheckpoint failed");
        }
    }

    @Override
    public List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return Collections.emptyList();
            }

            SQL sql = SQL.begin().name("getAllCheckpoints").querySpace(querySpace)
                    .sql("SELECT state_data FROM " + TABLE_NAME +
                            " WHERE job_id = ? ORDER BY checkpoint_id DESC", jobId)
                    .end();

            List<CompletedCheckpoint> result = new ArrayList<>();
            jdbcTemplate.executeQuery(sql, dataSet -> {
                for (IDataRow row : dataSet) {
                    byte[] data = row.getBytes(0);
                    CompletedCheckpoint cp = deserializeCheckpoint(data);
                    if (cp != null) {
                        result.add(cp);
                    }
                }
                return null;
            });
            return result;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "getAllCheckpoints failed");
        }
    }

    @Override
    public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return Collections.emptyList();
            }

            SQL sql = SQL.begin().name("getLatestCheckpoints").querySpace(querySpace)
                    .sql("SELECT state_data FROM " + TABLE_NAME +
                            " WHERE job_id = ? ORDER BY checkpoint_id DESC LIMIT ?", jobId, count)
                    .end();

            List<CompletedCheckpoint> result = new ArrayList<>();
            jdbcTemplate.executeQuery(sql, dataSet -> {
                for (IDataRow row : dataSet) {
                    byte[] data = row.getBytes(0);
                    CompletedCheckpoint cp = deserializeCheckpoint(data);
                    if (cp != null) {
                        result.add(cp);
                    }
                }
                return null;
            });
            return result;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "getLatestCheckpoints failed");
        }
    }

    @Override
    public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return;
            }

            SQL sql = SQL.begin().name("deleteCheckpoint").querySpace(querySpace)
                    .sql("DELETE FROM " + TABLE_NAME +
                            " WHERE job_id = ? AND pipeline_id = ? AND checkpoint_id = ?",
                            jobId, pipelineId, checkpointId)
                    .end();

            jdbcTemplate.executeUpdate(sql);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "deleteCheckpoint failed");
        }
    }

    @Override
    public void deleteAllCheckpoints(String jobId) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return;
            }

            SQL sql = SQL.begin().name("deleteAllCheckpoints").querySpace(querySpace)
                    .sql("DELETE FROM " + TABLE_NAME + " WHERE job_id = ?", jobId)
                    .end();

            jdbcTemplate.executeUpdate(sql);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "deleteAllCheckpoints failed");
        }
    }

    @Override
    public boolean exists(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return false;
            }

            SQL sql = SQL.begin().name("checkpointExists").querySpace(querySpace)
                    .sql("SELECT COUNT(*) FROM " + TABLE_NAME +
                            " WHERE job_id = ? AND pipeline_id = ? AND checkpoint_id = ?",
                            jobId, pipelineId, checkpointId)
                    .end();

            Integer count = jdbcTemplate.findInt(sql, 0);
            return count != null && count > 0;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "exists failed");
        }
    }

    @Override
    public int getCheckpointCount(String jobId) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return 0;
            }

            SQL sql = SQL.begin().name("getCheckpointCount").querySpace(querySpace)
                    .sql("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE job_id = ?", jobId)
                    .end();

            Integer count = jdbcTemplate.findInt(sql, 0);
            return count != null ? count : 0;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "getCheckpointCount failed");
        }
    }

    @Override
    public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws CheckpointStorageException {
        try {
            ensureTable();
            byte[] stateData = serializeCheckpoint(checkpoint);
            long sid = nextSid();

            String[] columns = {"sid", "job_id", "pipeline_id", "checkpoint_id", "checkpoint_type",
                    "trigger_timestamp", "completed_timestamp", "state_data", "savepoint_path"};
            String[] conflictColumns = {"job_id", "pipeline_id", "checkpoint_id"};
            String[] updateColumns = {"checkpoint_type", "trigger_timestamp", "completed_timestamp",
                    "state_data", "savepoint_path"};
            Object[] values = {sid, checkpoint.getJobId(), checkpoint.getPipelineId(), checkpoint.getCheckpointId(),
                    checkpoint.getCheckpointType().name(), checkpoint.getTriggerTimestamp(),
                    checkpoint.getCompletedTimestamp(), stateData, targetPath};

            UpsertDialect dialect = resolveUpsertDialect();
            if (dialect == UpsertDialect.GENERIC) {
                SQL insert = SQL.begin().name("storeSavepoint").querySpace(querySpace)
                        .sql("INSERT INTO " + TABLE_NAME +
                                " (sid, job_id, pipeline_id, checkpoint_id, checkpoint_type, trigger_timestamp, " +
                                "completed_timestamp, state_data, savepoint_path) VALUES (?,?,?,?,?,?,?,?,?)", values)
                        .end();
                SQL update = SQL.begin().name("updateSavepoint").querySpace(querySpace)
                        .sql("UPDATE " + TABLE_NAME +
                                " SET checkpoint_type = ?, trigger_timestamp = ?, completed_timestamp = ?, state_data = ?, savepoint_path = ?" +
                                " WHERE job_id = ? AND pipeline_id = ? AND checkpoint_id = ?",
                                checkpoint.getCheckpointType().name(), checkpoint.getTriggerTimestamp(),
                                checkpoint.getCompletedTimestamp(), stateData, targetPath,
                                checkpoint.getJobId(), checkpoint.getPipelineId(), checkpoint.getCheckpointId())
                        .end();
                runInsertOrUpdateSeparateTxns(insert, update, "storeSavepoint");
            } else {
                SQL upsert = buildNativeUpsert(dialect, "storeSavepoint", TABLE_NAME,
                        columns, conflictColumns, updateColumns, values);
                runNativeUpsert(upsert);
            }

            return targetPath;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "storeSavepoint failed");
        }
    }

    @Override
    public CompletedCheckpoint loadSavepoint(String savepointPath) throws CheckpointStorageException {
        try {
            if (!tableExists()) {
                return null;
            }
            if (savepointPath == null || savepointPath.isEmpty()) {
                return null;
            }

            SQL sql = SQL.begin().name("loadSavepoint").querySpace(querySpace)
                    .sql("SELECT state_data FROM " + TABLE_NAME +
                            " WHERE savepoint_path = ?" +
                            " ORDER BY checkpoint_id DESC LIMIT 1", savepointPath)
                    .end();

            byte[][] result = {null};
            jdbcTemplate.executeQuery(sql, dataSet -> {
                for (IDataRow row : dataSet) {
                    result[0] = row.getBytes(0);
                    break;
                }
                return null;
            });
            return deserializeCheckpoint(result[0]);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "loadSavepoint failed");
        }
    }

    @Override
    public SavepointMetadata loadSavepointMetadata(String savepointPath) throws CheckpointStorageException {
        try {
            CompletedCheckpoint checkpoint = loadSavepoint(savepointPath);
            if (checkpoint == null) {
                return null;
            }
            return SavepointMetadata.fromCompletedCheckpoint(checkpoint);
        } catch (NopException e) {
            throw e;
        }
    }

    private boolean tableExists() {
        try {
            return jdbcTemplate.existsTable(querySpace, TABLE_NAME);
        } catch (Exception e) {
            LOG.debug("Failed to check table existence", e);
            return false;
        }
    }

    private void ensureTable() {
        if (tableInitialized) {
            return;
        }
        synchronized (this) {
            if (tableInitialized) {
                return;
            }

            if (tableExists()) {
                tableInitialized = true;
                return;
            }

            String ddl = "CREATE TABLE " + TABLE_NAME + " (" +
                    "sid BIGINT NOT NULL, " +
                    "job_id VARCHAR(255) NOT NULL, " +
                    "pipeline_id VARCHAR(255) NOT NULL, " +
                    "checkpoint_id BIGINT NOT NULL, " +
                    "checkpoint_type VARCHAR(50) NOT NULL, " +
                    "trigger_timestamp BIGINT NOT NULL, " +
                    "completed_timestamp BIGINT NOT NULL, " +
                    "state_data BLOB, " +
                    "savepoint_path VARCHAR(1024), " +
                    "created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (sid), " +
                    "UNIQUE (job_id, pipeline_id, checkpoint_id)" +
                    ")";

            SQL createTableSql = SQL.begin().name("createCheckpointTable").querySpace(querySpace)
                    .sql(ddl)
                    .end();

            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                jdbcTemplate.executeUpdate(createTableSql);

                try {
                    SQL idx1 = SQL.begin().name("createIdxJobPipeline").querySpace(querySpace)
                            .sql("CREATE INDEX idx_job_pipeline ON " + TABLE_NAME + " (job_id, pipeline_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx1);
                } catch (Exception e) {
                    LOG.debug("Index idx_job_pipeline may already exist, ignoring", e);
                }

                try {
                    SQL idx2 = SQL.begin().name("createIdxCheckpointId").querySpace(querySpace)
                            .sql("CREATE INDEX idx_checkpoint_id ON " + TABLE_NAME + " (checkpoint_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx2);
                } catch (Exception e) {
                    LOG.debug("Index idx_checkpoint_id may already exist, ignoring", e);
                }

                try {
                    SQL uniqueConstraint = SQL.begin().name("addCheckpointUniqueConstraint").querySpace(querySpace)
                            .sql("ALTER TABLE " + TABLE_NAME + " ADD CONSTRAINT uk_checkpoint_job_pipeline_id UNIQUE (job_id, pipeline_id, checkpoint_id)")
                            .end();
                    jdbcTemplate.executeUpdate(uniqueConstraint);
                } catch (Exception e) {
                    LOG.debug("Unique constraint uk_checkpoint_job_pipeline_id may already exist, ignoring", e);
                }

                return null;
            });
            tableInitialized = true;
        }
    }

    private byte[] serializeCheckpoint(CompletedCheckpoint checkpoint) {
        return CheckpointSerDe.serializeCheckpoint(checkpoint);
    }

    private CompletedCheckpoint deserializeCheckpoint(byte[] data) {
        return CheckpointSerDe.deserializeCheckpoint(data);
    }

    private TaskStateSnapshot deserializeTaskStateSnapshot(Map<String, Object> map, TaskLocation taskLocation) {
        return CheckpointSerDe.deserializeTaskStateSnapshot(map, taskLocation);
    }

    @Override
    public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws CheckpointStorageException {
        try {
            ensureEpochTable();
            byte[] stateData = serializeEpochManifest(manifest);
            long sid = nextSid();
            String checkpointType = manifest.getCheckpointType() != null ? manifest.getCheckpointType().name() : "CHECKPOINT";
            String state = manifest.getState() != null ? manifest.getState().name() : "COMMITTED";

            String[] columns = {"sid", "job_id", "pipeline_id", "epoch_id", "checkpoint_type", "state",
                    "timestamp", "state_data"};
            String[] conflictColumns = {"job_id", "pipeline_id", "epoch_id"};
            String[] updateColumns = {"checkpoint_type", "state", "timestamp", "state_data"};
            Object[] values = {sid, manifest.getJobId(), manifest.getPipelineId(), manifest.getEpochId(),
                    checkpointType, state, manifest.getTimestamp(), stateData};

            UpsertDialect dialect = resolveUpsertDialect();
            if (dialect == UpsertDialect.GENERIC) {
                SQL insert = SQL.begin().name("storeEpochManifest").querySpace(querySpace)
                        .sql("INSERT INTO " + EPOCH_TABLE_NAME +
                                " (sid, job_id, pipeline_id, epoch_id, checkpoint_type, state, timestamp, state_data) " +
                                "VALUES (?,?,?,?,?,?,?,?)", values).end();
                SQL update = SQL.begin().name("updateEpochManifest").querySpace(querySpace)
                        .sql("UPDATE " + EPOCH_TABLE_NAME +
                                " SET checkpoint_type = ?, state = ?, timestamp = ?, state_data = ?" +
                                " WHERE job_id = ? AND pipeline_id = ? AND epoch_id = ?",
                                checkpointType, state, manifest.getTimestamp(), stateData,
                                manifest.getJobId(), manifest.getPipelineId(), manifest.getEpochId())
                        .end();
                runInsertOrUpdateSeparateTxns(insert, update, "storeEpochManifest");
            } else {
                SQL upsert = buildNativeUpsert(dialect, "storeEpochManifest", EPOCH_TABLE_NAME,
                        columns, conflictColumns, updateColumns, values);
                runNativeUpsert(upsert);
            }

            LOG.debug("Stored epoch manifest {} for job {}/{}", manifest.getEpochId(), jobId, pipelineId);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "storeEpochManifest failed");
        }
    }

    @Override
    public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws CheckpointStorageException {
        try {
            if (!epochTableExists()) {
                return null;
            }

            SQL sql = SQL.begin().name("loadLatestEpochManifest").querySpace(querySpace)
                    .sql("SELECT state_data FROM " + EPOCH_TABLE_NAME +
                            " WHERE job_id = ? AND pipeline_id = ?" +
                            " ORDER BY epoch_id DESC LIMIT 1", jobId, pipelineId)
                    .end();

            byte[][] result = {null};
            jdbcTemplate.executeQuery(sql, dataSet -> {
                for (IDataRow row : dataSet) {
                    result[0] = row.getBytes(0);
                    break;
                }
                return null;
            });
            return deserializeEpochManifest(result[0]);
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL, "loadLatestEpochManifest failed");
        }
    }

    private boolean epochTableExists() {
        try {
            return jdbcTemplate.existsTable(querySpace, EPOCH_TABLE_NAME);
        } catch (Exception e) {
            LOG.debug("Failed to check epoch table existence", e);
            return false;
        }
    }

    private void ensureEpochTable() {
        if (epochTableInitialized) {
            return;
        }
        synchronized (this) {
            if (epochTableInitialized) {
                return;
            }

            if (epochTableExists()) {
                epochTableInitialized = true;
                return;
            }

            String ddl = "CREATE TABLE " + EPOCH_TABLE_NAME + " (" +
                    "sid BIGINT NOT NULL, " +
                    "job_id VARCHAR(255) NOT NULL, " +
                    "pipeline_id VARCHAR(255) NOT NULL, " +
                    "epoch_id BIGINT NOT NULL, " +
                    "checkpoint_type VARCHAR(50) NOT NULL, " +
                    "state VARCHAR(50) NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "state_data BLOB, " +
                    "created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (sid), " +
                    "UNIQUE (job_id, pipeline_id, epoch_id)" +
                    ")";

            SQL createTableSql = SQL.begin().name("createEpochManifestTable").querySpace(querySpace)
                    .sql(ddl)
                    .end();

            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                jdbcTemplate.executeUpdate(createTableSql);

                try {
                    SQL idx1 = SQL.begin().name("createEpochJobPipelineIdx").querySpace(querySpace)
                            .sql("CREATE INDEX idx_epoch_job_pipeline ON " + EPOCH_TABLE_NAME + " (job_id, pipeline_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx1);
                } catch (Exception e) {
                    LOG.debug("Index idx_epoch_job_pipeline may already exist, ignoring", e);
                }

                try {
                    SQL idx2 = SQL.begin().name("createEpochIdIdx").querySpace(querySpace)
                            .sql("CREATE INDEX idx_epoch_id ON " + EPOCH_TABLE_NAME + " (epoch_id)")
                            .end();
                    jdbcTemplate.executeUpdate(idx2);
                } catch (Exception e) {
                    LOG.debug("Index idx_epoch_id may already exist, ignoring", e);
                }

                try {
                    SQL uniqueConstraint = SQL.begin().name("addEpochUniqueConstraint").querySpace(querySpace)
                            .sql("ALTER TABLE " + EPOCH_TABLE_NAME + " ADD CONSTRAINT uk_epoch_job_pipeline_id UNIQUE (job_id, pipeline_id, epoch_id)")
                            .end();
                    jdbcTemplate.executeUpdate(uniqueConstraint);
                } catch (Exception e) {
                    LOG.debug("Unique constraint uk_epoch_job_pipeline_id may already exist, ignoring", e);
                }

                return null;
            });
            epochTableInitialized = true;
        }
    }

    private byte[] serializeEpochManifest(EpochManifest manifest) {
        return CheckpointSerDe.serializeEpochManifest(manifest);
    }

    private EpochManifest deserializeEpochManifest(byte[] data) {
        return CheckpointSerDe.deserializeEpochManifest(data);
    }

    private static synchronized long nextSid() {
        return ++sidSequence;
    }

    /**
     * Database upsert style supported by the resolved dialect. The checkpoint
     * tables use a composite unique key (job_id, pipeline_id, checkpoint_id or
     * epoch_id), and the older INSERT-then-UPDATE-in-one-transaction pattern is
     * unsafe on PostgreSQL (any statement error aborts the whole transaction,
     * so the UPDATE after a caught duplicate-key INSERT fails with "current
     * transaction is aborted"). Each branch below uses a single atomic upsert
     * statement when the dialect provides one; only the generic fallback splits
     * into two separate transactions.
     */
    enum UpsertDialect {
        /** PostgreSQL: INSERT ... ON CONFLICT (cols) DO UPDATE SET col = EXCLUDED.col. */
        POSTGRESQL,
        /** MySQL / MariaDB: INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col). */
        MYSQL,
        /** H2: MERGE INTO ... KEY (cols) VALUES (...). */
        H2,
        /** Any other DB: INSERT in one txn; on duplicate key, UPDATE in a fresh txn. */
        GENERIC
    }
    private UpsertDialect resolveUpsertDialect() {
        try {
            String name = jdbcTemplate.getDialectForQuerySpace(querySpace).getName().toLowerCase();
            if (name.startsWith("postgresql")) {
                return UpsertDialect.POSTGRESQL;
            }
            if (name.startsWith("mysql") || name.startsWith("mariadb")) {
                return UpsertDialect.MYSQL;
            }
            if (name.startsWith("h2")) {
                return UpsertDialect.H2;
            }
        } catch (Exception e) {
            LOG.debug("Failed to resolve dialect for upsert strategy, using generic fallback", e);
        }
        return UpsertDialect.GENERIC;
    }

    /** Execute a single atomic native-upsert statement inside one transaction. */
    private void runNativeUpsert(SQL sql) {
        jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            jdbcTemplate.executeUpdate(sql);
            return null;
        });
    }

    /**
     * Build a native-upsert {@link SQL} object for the given dialect. The values
     * array must align positionally with {@code columns} (one value per column).
     * Returns {@code null} for {@link UpsertDialect#GENERIC} — the caller must
     * fall back to {@link #runInsertOrUpdateSeparateTxns(SQL, SQL, String)}.
     */
    private SQL buildNativeUpsert(
            UpsertDialect dialect, String sqlName, String tableName,
            String[] columns, String[] conflictColumns, String[] updateColumns,
            Object[] values) {
        String text = buildNativeUpsertSqlText(dialect, tableName, columns, conflictColumns, updateColumns);
        if (text == null) {
            return null;
        }
        return SQL.begin().name(sqlName).querySpace(querySpace).sql(text, values).end();
    }

    /**
     * Produce the dialect-specific native-upsert SQL text. Each branch yields a
     * single atomic statement, eliminating the unsafe "INSERT throws duplicate-key,
     * then UPDATE in the same (now-aborted) transaction" pattern that breaks on
     * PostgreSQL. Returns {@code null} for the generic fallback.
     */
    static String buildNativeUpsertSqlText(
            UpsertDialect dialect, String tableName,
            String[] columns, String[] conflictColumns, String[] updateColumns) {
        String columnList = String.join(", ", columns);
        String conflictList = String.join(", ", conflictColumns);
        String placeholders = String.join(", ", Collections.nCopies(columns.length, "?"));

        switch (dialect) {
            case POSTGRESQL: {
                String setClause = String.join(", ",
                        Arrays.stream(updateColumns).map(c -> c + " = EXCLUDED." + c).toArray(String[]::new));
                return "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ") "
                        + "ON CONFLICT (" + conflictList + ") DO UPDATE SET " + setClause;
            }
            case MYSQL: {
                String setClause = String.join(", ",
                        Arrays.stream(updateColumns).map(c -> c + " = VALUES(" + c + ")").toArray(String[]::new));
                return "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ") "
                        + "ON DUPLICATE KEY UPDATE " + setClause;
            }
            case H2: {
                return "MERGE INTO " + tableName + " (" + columnList + ") KEY (" + conflictList + ") VALUES ("
                        + placeholders + ")";
            }
            default:
                return null;
        }
    }

    /**
     * Generic fallback: try INSERT in its own transaction; if a duplicate key is
     * detected, run UPDATE in a SEPARATE transaction. Splitting the transactions
     * is essential for engines that abort the whole transaction on a failed
     * statement (e.g. PostgreSQL): the INSERT failure only rolls back the first
     * transaction, leaving the second (UPDATE) transaction in a clean state.
     */
    private void runInsertOrUpdateSeparateTxns(SQL insert, SQL update, String operation) {
        try {
            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                jdbcTemplate.executeUpdate(insert);
                return null;
            });
        } catch (Exception e) {
            if (!isDuplicateKeyException(e)) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            LOG.debug("{} INSERT failed (duplicate key), running UPDATE in a fresh transaction", operation, e);
            jdbcTemplate.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                jdbcTemplate.executeUpdate(update);
                return null;
            });
        }
    }

    private static boolean isDuplicateKeyException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String className = cause.getClass().getName().toLowerCase();
            String message = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
            if (className.contains("integrity") || className.contains("constraint")
                    || className.contains("duplicate") || className.contains("unique")
                    || className.contains("primarykey") || className.contains("primary_key")
                    || message.contains("duplicate") || message.contains("unique constraint")
                    || message.contains("primary key") || message.contains("23505")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
