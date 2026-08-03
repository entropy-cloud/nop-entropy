/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;

/**
 * Transactional JDBC sink that provides exactly-once output via two-phase commit.
 *
 * <p>Each checkpoint epoch maps to one JDBC transaction:
 * <ol>
 *   <li>{@code invoke(value)} buffers the record in memory (no JDBC write).</li>
 *   <li>{@code saveState(epochId)} moves the in-memory batch into {@code pendingCommits[epochId]}
 *       <strong>before</strong> delegating to {@code super.saveState}, so the batch is persisted
 *       in <em>this</em> checkpoint — not lagging by one epoch. (See
 *       {@code StreamSinkOperator.processBarrier}: {@code saveState} runs before
 *       {@code prepareCommit}/{@code preCommit}.)</li>
 *   <li>{@code preCommit(epochId)} is a no-op (saveState already moved the batch).</li>
 *   <li>{@code commit(epochId)} opens a <strong>new</strong> JDBC transaction on an independent
 *       connection, atomically writes the data batch + a ledger row (epoch_id PK) in the same
 *       {@code connection.commit()}, then removes the entry from {@code pendingCommits}.</li>
 *   <li>{@code rollback()} discards the in-memory buffer; {@code abort(epochId)} discards
 *       {@code pendingCommits[epochId]} (no JDBC cleanup needed — data was never written).</li>
 * </ol>
 *
 * <p><strong>Idempotent commit guard</strong>: before writing data, {@code commit} checks the
 * ledger table for the epoch. If already recorded (e.g. after recovery of a durable-but-
 * uncommitted epoch), the data write is skipped — no duplicates.
 *
 * <p><strong>Subsuming constraint</strong>: the base class {@code finishCommit(M, true)} calls
 * {@code commit(eid)} for each {@code eid <= M}. Each call opens its own independent JDBC
 * transaction (separate {@code openConnection}), ensuring per-epoch atomicity and ledger
 * consistency.
 *
 * <p>See {@code connector-design.md} §5.3 for the full design rationale.
 *
 * @param <IN> the type of input records
 */
public class JdbcTwoPhaseCommitSink<IN> extends TwoPhaseCommitSinkFunction<IN> {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_LEDGER_TABLE = "stream_epoch_ledger";
    private static final String LEDGER_EPOCH_COL = "epoch_id";
    private static final String LEDGER_TIMESTAMP_COL = "committed_at";

    // ---- Configuration (final) ----
    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private final String tableName;
    private final String ledgerTableName;
    private final List<String> columnNames;
    private final Function<IN, Map<String, Object>> recordMapper;

    // ---- In-memory buffer for the current epoch (not yet in pendingCommits) ----
    private final List<Map<String, Object>> currentBuffer = new ArrayList<>();

    // ---- Cached SQL and dialect (lazily initialized in beginTransaction) ----
    private transient IDialect dialect;
    private transient String insertDataSql;
    private transient String insertLedgerSql;
    private transient String ledgerExistsSql;
    private transient boolean initialized = false;

    /**
     * Full constructor.
     *
     * @param jdbcTemplate   the JDBC template for obtaining connections and dialect
     * @param querySpace     the query space (database/schema identifier)
     * @param tableName      the target data table name
     * @param ledgerTableName the epoch ledger table name (idempotent commit guard)
     * @param columnNames    ordered list of data column names to write
     * @param recordMapper   function converting input records to column→value maps
     */
    public JdbcTwoPhaseCommitSink(IJdbcTemplate jdbcTemplate, String querySpace, String tableName,
                                  String ledgerTableName, List<String> columnNames,
                                  Function<IN, Map<String, Object>> recordMapper) {
        if (jdbcTemplate == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "jdbcTemplate");
        }
        if (tableName == null || tableName.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "tableName");
        }
        if (columnNames == null || columnNames.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "columnNames");
        }
        if (recordMapper == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "recordMapper");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : "";
        this.tableName = tableName;
        this.ledgerTableName = ledgerTableName != null ? ledgerTableName : DEFAULT_LEDGER_TABLE;
        this.columnNames = Collections.unmodifiableList(new ArrayList<>(columnNames));
        this.recordMapper = recordMapper;
    }

    // ---- Lifecycle ----

    @Override
    public void beginTransaction() {
        ensureInitialized();
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        this.dialect = jdbcTemplate.getDialectForQuerySpace(querySpace);
        this.insertDataSql = buildInsertDataSql(dialect);
        this.insertLedgerSql = buildInsertLedgerSql(dialect);
        this.ledgerExistsSql = buildLedgerExistsSql(dialect);
        this.initialized = true;
    }

    // ---- Data path ----

    @Override
    public void invoke(IN value) throws Exception {
        if (value == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "value");
        }
        Map<String, Object> row = recordMapper.apply(value);
        if (row == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "recordMapper returned null for value: " + value);
        }
        currentBuffer.add(new LinkedHashMap<>(row));
    }

    /**
     * Move the current in-memory buffer into {@code pendingCommits[epochId]} BEFORE
     * delegating to {@code super.saveState}, so the batch is captured in THIS checkpoint.
     *
     * <p>This override is critical because {@code StreamSinkOperator.processBarrier} calls
     * {@code saveState} before {@code preCommit}. Without this override, the batch would
     * lag by one epoch and be permanently lost on restore.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TaskStateSnapshot saveState(long epochId) throws Exception {
        ensureInitialized();
        synchronized (currentBuffer) {
            if (!currentBuffer.isEmpty()) {
                List<Map<String, Object>> batch = new ArrayList<>(currentBuffer);
                getPendingCommits().put(epochId, batch);
                currentBuffer.clear();
            }
        }
        return super.saveState(epochId);
    }

    @Override
    public void preCommit(long checkpointId) {
        // saveState already moved the batch into pendingCommits. No JDBC write here.
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commit(long checkpointId) throws Exception {
        ensureInitialized();

        Object raw = getPendingCommits().get(checkpointId);
        if (raw == null) {
            // No data batch for this epoch — nothing to commit. This happens when
            // the base class finishCommit falls through to commit(epochId) with an
            // empty pendingCommits map, or when an epoch had zero records.
            LOG.debug("commit(epochId={}) has no pending batch — skipping", checkpointId);
            return;
        }

        List<Map<String, Object>> batch;
        if (raw instanceof List) {
            batch = (List<Map<String, Object>>) raw;
        } else {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                    .param(ARG_EPOCH_ID, checkpointId)
                    .param(ARG_DETAIL,
                            "pendingCommits value is not a List: " + raw.getClass().getName());
        }

        Connection connection = jdbcTemplate.openConnection(querySpace);
        boolean committed = false;
        try {
            connection.setAutoCommit(false);

            // Idempotent guard: check ledger first
            if (ledgerExists(connection, checkpointId)) {
                LOG.info("Epoch {} already recorded in ledger — skipping data write (idempotent re-commit)",
                        checkpointId);
                connection.commit();
                committed = true;
                return;
            }

            // Write data rows
            if (!batch.isEmpty()) {
                writeDataRows(connection, batch);
            }

            // Write ledger entry (same transaction as data)
            writeLedgerEntry(connection, checkpointId);

            connection.commit();
            committed = true;
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate(
                    "JdbcTwoPhaseCommitSink.commit(epoch=" + checkpointId + ")", e);
        } catch (RuntimeException e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e)
                    .param(ARG_EPOCH_ID, checkpointId)
                    .param(ARG_DETAIL, "JDBC commit failed for epoch " + checkpointId
                            + " on table " + tableName);
        } finally {
            if (!committed) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackErr) {
                    LOG.error("Rollback failed for epoch {}", checkpointId, rollbackErr);
                }
            }
            try {
                connection.setAutoCommit(true);
            } catch (SQLException autoCommitErr) {
                LOG.warn("Failed to restore autoCommit=true for epoch {}", checkpointId, autoCommitErr);
            }
            try {
                connection.close();
            } catch (SQLException closeErr) {
                LOG.warn("Failed to close connection for epoch {}", checkpointId, closeErr);
            }
        }

        getPendingCommits().remove(checkpointId);
    }

    @Override
    public void rollback() {
        synchronized (currentBuffer) {
            currentBuffer.clear();
        }
    }

    @Override
    public void abort(long epochId) {
        // Data was never written to JDBC (commit writes data + ledger atomically).
        // Just discard the pending batch.
        getPendingCommits().remove(epochId);
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.TWO_PHASE_COMMIT;
    }

    /**
     * Creates a fluent builder for constructing a {@link JdbcTwoPhaseCommitSink}.
     */
    public static <IN> JdbcTwoPhaseCommitSinkBuilder<IN> builder() {
        return new JdbcTwoPhaseCommitSinkBuilder<>();
    }

    // ---- Ledger management ----

    /**
     * Returns a portable DDL string for creating the epoch ledger table.
     * The caller should execute this via {@code jdbcTemplate} before starting the stream.
     */
    public String getLedgerTableDDL() {
        IDialect d = jdbcTemplate.getDialectForQuerySpace(querySpace);
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(d.escapeSQLName(ledgerTableName));
        sb.append(" (");
        sb.append(d.escapeSQLName(LEDGER_EPOCH_COL)).append(" BIGINT NOT NULL, ");
        sb.append(d.escapeSQLName(LEDGER_TIMESTAMP_COL)).append(" TIMESTAMP, ");
        sb.append("PRIMARY KEY (").append(d.escapeSQLName(LEDGER_EPOCH_COL)).append(")");
        sb.append(")");
        return sb.toString();
    }

    /**
     * Initializes the ledger table by executing the DDL.
     * Call this before starting the stream to ensure the ledger table exists.
     */
    public void initializeLedgerTable() {
        String ddl = getLedgerTableDDL();
        Connection connection = jdbcTemplate.openConnection(querySpace);
        try {
            try (PreparedStatement ps = connection.prepareStatement(ddl)) {
                ps.execute();
            }
        } catch (SQLException e) {
            throw dialect != null
                    ? dialect.getSQLExceptionTranslator().translate("initializeLedgerTable", e)
                    : new StreamException(ERR_STREAM_STATE_ERROR, e)
                            .param(ARG_DETAIL, "Failed to initialize ledger table: " + ledgerTableName);
        } finally {
            try {
                connection.close();
            } catch (SQLException closeErr) {
                LOG.warn("Failed to close connection after ledger init", closeErr);
            }
        }
    }

    // ---- Internal JDBC helpers ----

    private boolean ledgerExists(Connection connection, long epochId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(ledgerExistsSql)) {
            ps.setLong(1, epochId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void writeLedgerEntry(Connection connection, long epochId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(insertLedgerSql)) {
            ps.setLong(1, epochId);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private void writeDataRows(Connection connection, List<?> batch) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(insertDataSql)) {
            for (Object item : batch) {
                Map<String, Object> row;
                if (item instanceof Map) {
                    row = (Map<String, Object>) item;
                } else {
                    throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                            .param(ARG_DETAIL, "Batch item is not a Map: " + item.getClass().getName());
                }
                int index = 1;
                for (String col : columnNames) {
                    Object value = row.get(col);
                    if (value == null) {
                        ps.setNull(index, Types.NULL);
                    } else {
                        ps.setObject(index, value);
                    }
                    index++;
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---- SQL builders ----

    private String buildInsertDataSql(IDialect d) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(d.escapeSQLName(tableName));
        sb.append(" (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(d.escapeSQLName(columnNames.get(i)));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildInsertLedgerSql(IDialect d) {
        return "INSERT INTO " + d.escapeSQLName(ledgerTableName)
                + " (" + d.escapeSQLName(LEDGER_EPOCH_COL) + ", " + d.escapeSQLName(LEDGER_TIMESTAMP_COL) + ")"
                + " VALUES (?, ?)";
    }

    private String buildLedgerExistsSql(IDialect d) {
        return "SELECT 1 FROM " + d.escapeSQLName(ledgerTableName)
                + " WHERE " + d.escapeSQLName(LEDGER_EPOCH_COL) + " = ?";
    }
}
