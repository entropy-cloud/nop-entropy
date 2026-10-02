/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import io.nop.api.core.time.CoreMetrics;
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
import io.nop.message.debezium.ChangeEvent;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONFIG_ERROR;
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
 *       connection, atomically writes the data batch + a ledger row (composite PK
 *       {@code (sink_namespace, epoch_id, subtask_id)}) in the same {@code connection.commit()},
 *       then removes the entry from {@code pendingCommits}.</li>
 *   <li>{@code rollback()} discards the in-memory buffer; {@code abort(epochId)} discards
 *       {@code pendingCommits[epochId]} (no JDBC cleanup needed — data was never written).</li>
 * </ol>
 *
 * <p><strong>Idempotent commit guard</strong>: before writing data, {@code commit} checks the
 * ledger table for the namespace + epoch + subtask. If already recorded (e.g. after recovery
 * of a durable-but-uncommitted epoch), the data write is skipped — no duplicates. The
 * namespace ({@code jobId|vertexId}, from the deployment task location) keeps different
 * sink branches / different jobs sharing one ledger table from hitting each other's guard.
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
public class JdbcTwoPhaseCommitSink<IN> extends TwoPhaseCommitSinkFunction<IN>
        implements ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_LEDGER_TABLE = "stream_epoch_ledger_v2";

    /** Default JDBC batch segmentation size. */
    public static final int DEFAULT_MAX_BATCH_SIZE = 1000;
    /**
     * Ledger namespace column (plan 368 Phase 4, audit R5-CON-01): the v2 ledger key is
     * {@code (sink_namespace, epoch_id, subtask_id)} so same-epoch commits from
     * <em>different sink branches</em> (two vertices of one job) or from different jobs
     * sharing one database never collide. The namespace is the deployment
     * {@code TaskLocation}'s {@code jobId|vertexId}, carried into the sink copy by the
     * {@code copyForSubtask(TaskLocation)} identity pipeline. Without deployment
     * identity (direct construction) the target data table name is used as the
     * namespace fallback, which still disambiguates same-job branches writing
     * different tables.
     */
    private static final String LEDGER_NAMESPACE_COL = "sink_namespace";
    private static final String LEDGER_EPOCH_COL = "epoch_id";
    private static final String LEDGER_SUBTASK_COL = "subtask_id";
    private static final String LEDGER_TIMESTAMP_COL = "committed_at";

    /**
     * DML semantics of the sink (cdc-design.md §3.5).
     */
    public enum DmlMode {
        /** Append-only inserts (legacy semantics, the default). */
        INSERT,
        /** Keyed upsert for c/r/u events; a d event is a typed error. */
        UPSERT,
        /** Keyed upsert for c/r/u events plus keyed delete for d events (CDC mirror). */
        UPSERT_DELETE;

        public static DmlMode parse(String value) {
            if (value == null || value.isEmpty()) {
                return INSERT;
            }
            switch (value.trim().toLowerCase()) {
                case "insert":
                    return INSERT;
                case "upsert":
                    return UPSERT;
                case "upsert_delete":
                case "upsert-delete":
                    return UPSERT_DELETE;
                default:
                    throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                            .param(ARG_DETAIL, "unknown dmlMode: " + value
                                    + " (supported: insert, upsert, upsert_delete)");
            }
        }
    }

    /**
     * One buffered DML operation. Kind {@code I} = plain insert, {@code U} = keyed
     * upsert, {@code D} = keyed delete. Buffered per epoch and persisted in
     * {@code pendingCommits}; {@code Serializable} because the pending batch rides the
     * checkpoint state.
     */
    public static final class DmlOp implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public static final char KIND_INSERT = 'I';
        public static final char KIND_UPSERT = 'U';
        public static final char KIND_DELETE = 'D';

        public final char kind;
        /**
         * KIND_INSERT / KIND_UPSERT: value columns → values.
         * KIND_DELETE: key columns → values.
         */
        public final Map<String, Object> row;

        public DmlOp(char kind, Map<String, Object> row) {
            this.kind = kind;
            this.row = row;
        }
    }

    /**
     * Upsert SQL style of the resolved dialect (mirrors the JdbcCheckpointStorage
     * UpsertDialect precedent). Anything outside the built-in three is rejected for
     * UPSERT modes: the built-in sync scope targets MySQL and PostgreSQL (cdc-design
     * §3.2); H2 is recognized for tests.
     */
    private enum UpsertDialect {
        MYSQL, POSTGRESQL, H2
    }

    // ---- Configuration (final) ----
    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private final String tableName;
    private final String ledgerTableName;
    /**
     * Value columns for KIND_INSERT ops (fixed, required in INSERT mode). In UPSERT
     * modes this may be empty — the column set is then derived from the first buffered
     * op's row and cached (schema drift within a run = typed error, no silent widening).
     */
    private final List<String> columnNames;
    private final Function<IN, Map<String, Object>> recordMapper;
    /** DML semantics (cdc-design.md §3.5); INSERT keeps the legacy append-only path. */
    private final DmlMode dmlMode;
    /** Key columns for keyed modes (UPSERT/UPSERT_DELETE); required there, empty for INSERT. */
    private final List<String> keyColumns;
    /**
     * Operation source for keyed modes: maps a record to its Debezium operation code
     * (c/u/d/r). Optional — when absent, records must be {@link ChangeEvent} instances
     * (the operation is read from the envelope).
     */
    private final Function<IN, String> opMapper;
    /**
     * Key map source for KIND_DELETE ops in keyed modes. Optional — ChangeEvent records
     * use {@link ChangeEvent#getKey()}; mapped records fall back to the value row
     * restricted to {@code keyColumns}.
     */
    private final Function<IN, Map<String, Object>> deleteKeyMapper;
    /**
     * Upper bound for one JDBC batch execution. The whole epoch
     * used to be buffered into a single {@code executeBatch()}, which can OOM the
     * driver or exceed server packet limits for large epochs. Batches are now
     * segmented; the transaction boundary (single commit for data + ledger) is
     * unchanged.
     */
    private final int maxBatchSize;
    /**
     * Subtask identity of this sink copy (0 for a non-parallel / template instance).
     * Parallel subtask copies disambiguate their ledger rows (idempotent commit guard)
     * with this index so same-epoch commits from different subtasks never collide —
     * see {@link #copyForSubtask(int)}.
     */
    private final int subtaskIndex;
    /**
     * Ledger namespace of this sink copy ({@code jobId|vertexId} when deployed through
     * the task-identity pipeline, {@code null} for direct construction — the effective
     * namespace then falls back to the target table name, see
     * {@link #effectiveLedgerNamespace()}).
     */
    private final String ledgerNamespace;

    // ---- In-memory buffer for the current epoch (not yet in pendingCommits) ----
    private final List<DmlOp> currentBuffer = new ArrayList<>();

    // ---- Cached SQL and dialect (lazily initialized in beginTransaction) ----
    private transient IDialect dialect;
    private transient String insertDataSql;
    private transient String insertLedgerSql;
    private transient String ledgerExistsSql;
    /**
     * Upsert/delete SQL and column set, established from the FIRST keyed batch and
     * cached; a later batch with a different column set is a typed error (no silent
     * schema drift). volatile: invoke (task thread) establishes the column set via the
     * buffered rows, commit (commit thread) consumes the SQL.
     */
    private transient volatile List<String> keyedColumns;
    private transient volatile String upsertSql;
    private transient volatile String deleteSql;
    private transient volatile UpsertDialect upsertDialect;
    /**
     * volatile so the task thread (saveState on the barrier
     * path) and the commit thread (finishCommit notification) safely publish /
     * observe the lazy initialization — a plain flag was an unsafe publication
     * under concurrent first use (NPE / duplicated init).
     */
    private transient volatile boolean initialized = false;

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
        this(jdbcTemplate, querySpace, tableName, ledgerTableName, columnNames, recordMapper, 0,
                DEFAULT_MAX_BATCH_SIZE);
    }

    /**
     * Copy constructor for a parallel subtask (see {@link #copyForSubtask(int)}).
     * Package-private: also used by the same-package builder to thread
     * {@code maxBatchSize}.
     */
    JdbcTwoPhaseCommitSink(IJdbcTemplate jdbcTemplate, String querySpace, String tableName,
                           String ledgerTableName, List<String> columnNames,
                           Function<IN, Map<String, Object>> recordMapper, int subtaskIndex,
                           int maxBatchSize) {
        this(jdbcTemplate, querySpace, tableName, ledgerTableName, columnNames, recordMapper,
                subtaskIndex, maxBatchSize, null);
    }

    /**
     * Full copy constructor including the ledger namespace (task-identity pipeline,
     * plan 368 Phase 4). Package-private: used by {@link #copyForSubtask} variants.
     * Delegates with INSERT dml semantics (legacy signature).
     */
    JdbcTwoPhaseCommitSink(IJdbcTemplate jdbcTemplate, String querySpace, String tableName,
                           String ledgerTableName, List<String> columnNames,
                           Function<IN, Map<String, Object>> recordMapper, int subtaskIndex,
                           int maxBatchSize, String ledgerNamespace) {
        this(jdbcTemplate, querySpace, tableName, ledgerTableName, columnNames, recordMapper,
                subtaskIndex, maxBatchSize, ledgerNamespace,
                DmlMode.INSERT, null, null, null);
    }

    /**
     * Canonical constructor including the DML contract (cdc-design.md §3.5).
     * Package-private: reached via the builder and the {@code copyForSubtask} variants.
     *
     * <p>Validation is mode-conditional: INSERT requires fixed {@code columnNames} and a
     * {@code recordMapper}; keyed modes (UPSERT/UPSERT_DELETE) require non-empty
     * {@code keyColumns}, accept a null {@code recordMapper} (native
     * {@link ChangeEvent} path) and derive operations from {@code opMapper} or the
     * ChangeEvent envelope.
     */
    JdbcTwoPhaseCommitSink(IJdbcTemplate jdbcTemplate, String querySpace, String tableName,
                           String ledgerTableName, List<String> columnNames,
                           Function<IN, Map<String, Object>> recordMapper, int subtaskIndex,
                           int maxBatchSize, String ledgerNamespace,
                           DmlMode dmlMode, List<String> keyColumns,
                           Function<IN, String> opMapper,
                           Function<IN, Map<String, Object>> deleteKeyMapper) {
        if (jdbcTemplate == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "jdbcTemplate");
        }
        if (tableName == null || tableName.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "tableName");
        }
        if (maxBatchSize <= 0) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "maxBatchSize");
        }
        DmlMode mode = dmlMode != null ? dmlMode : DmlMode.INSERT;
        if (mode == DmlMode.INSERT) {
            if (columnNames == null || columnNames.isEmpty()) {
                throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "columnNames");
            }
            if (recordMapper == null) {
                throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "recordMapper");
            }
            if (keyColumns != null && !keyColumns.isEmpty()) {
                throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                        .param(ARG_DETAIL, "keyColumns is only valid for dmlMode UPSERT/UPSERT_DELETE");
            }
            if (opMapper != null || deleteKeyMapper != null) {
                throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                        .param(ARG_DETAIL, "opMapper/deleteKeyMapper are only valid for dmlMode"
                                + " UPSERT/UPSERT_DELETE");
            }
        } else {
            if (keyColumns == null || keyColumns.isEmpty()) {
                throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                        .param(ARG_ARG_NAME, "keyColumns")
                        .param(ARG_DETAIL, "dmlMode " + mode + " requires non-empty keyColumns");
            }
            if (mode == DmlMode.UPSERT && deleteKeyMapper != null) {
                throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                        .param(ARG_DETAIL, "deleteKeyMapper is only valid for dmlMode UPSERT_DELETE"
                                + " (UPSERT does not carry delete events)");
            }
        }
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : "";
        this.tableName = tableName;
        this.ledgerTableName = ledgerTableName != null ? ledgerTableName : DEFAULT_LEDGER_TABLE;
        this.columnNames = columnNames != null
                ? Collections.unmodifiableList(new ArrayList<>(columnNames))
                : Collections.emptyList();
        this.recordMapper = recordMapper;
        this.subtaskIndex = subtaskIndex;
        this.ledgerNamespace = ledgerNamespace;
        this.dmlMode = mode;
        this.keyColumns = keyColumns != null
                ? Collections.unmodifiableList(new ArrayList<>(keyColumns))
                : Collections.emptyList();
        this.opMapper = opMapper;
        this.deleteKeyMapper = deleteKeyMapper;
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * Returns an independent copy of this sink for the parallel subtask
     * {@code subtaskIndex}. The copy shares the immutable configuration (JDBC template,
     * table/column names, record mapper) but has a fresh in-memory buffer and an empty
     * {@code pendingCommits} map, and keys its ledger rows (idempotent commit guard) by
     * {@code (epoch_id, subtask_id)} — parallel subtasks' same-epoch commits therefore
     * never collide and every subtask's batch is committed (exactly-once under
     * {@code parallelism > 1}).
     */
    @Override
    public JdbcTwoPhaseCommitSink<IN> copyForSubtask(int subtaskIndex) {
        return new JdbcTwoPhaseCommitSink<>(jdbcTemplate, querySpace, tableName,
                ledgerTableName, columnNames, recordMapper, subtaskIndex, maxBatchSize,
                this.ledgerNamespace, dmlMode, keyColumns, opMapper, deleteKeyMapper);
    }

    /**
     * Identity-aware copy (task-identity pipeline, plan 368 Phase 4): the copy keys its
     * ledger rows under the deployment namespace {@code jobId|vertexId} so same-epoch
     * commits from a DIFFERENT sink branch (another vertex of the same job) or from a
     * different job sharing the same database never hit this copy's ledger guard
     * (R5-CON-01: the old {@code (epoch_id, subtask_id)} key silently skipped the
     * second branch's writes).
     */
    @Override
    public JdbcTwoPhaseCommitSink<IN> copyForSubtask(TaskLocation location) {
        return new JdbcTwoPhaseCommitSink<>(jdbcTemplate, querySpace, tableName,
                ledgerTableName, columnNames, recordMapper, location.getTaskIndex(),
                maxBatchSize, location.getJobId() + "|" + location.getVertexId(),
                dmlMode, keyColumns, opMapper, deleteKeyMapper);
    }

    /**
     * Returns the subtask index of this sink copy. Primarily for tests.
     */
    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    /**
     * Returns the ledger namespace of this sink copy. Primarily for tests.
     */
    public String getLedgerNamespace() {
        return ledgerNamespace;
    }

    /**
     * The namespace actually used for ledger rows: the deployment-derived
     * {@code jobId|vertexId} when available, otherwise the target data table name
     * (direct construction without deployment identity — still disambiguates same-job
     * branches that write different tables).
     */
    private String effectiveLedgerNamespace() {
        return ledgerNamespace != null ? ledgerNamespace : tableName;
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
        // Double-checked under the instance monitor — saveState
        // (task thread) and commit (commit/notification thread) can race on first
        // use; both must observe a fully published initialization.
        synchronized (this) {
            if (initialized) {
                return;
            }
            this.dialect = jdbcTemplate.getDialectForQuerySpace(querySpace);
            this.insertDataSql = buildInsertDataSql(dialect);
            this.insertLedgerSql = buildInsertLedgerSql(dialect);
            this.ledgerExistsSql = buildLedgerExistsSql(dialect);
            this.initialized = true;
        }
    }

    // ---- Data path ----

    /**
     * Returns the DML semantics of this sink. Primarily for tests and the connector
     * capability probe.
     */
    public DmlMode getDmlMode() {
        return dmlMode;
    }

    @Override
    public void invoke(IN value) throws Exception {
        if (value == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "value");
        }
        DmlOp op = toDmlOp(value);
        // Same monitor as saveState/rollback: an unsynchronized add racing a concurrent
        // saveState snapshot can lose elements or corrupt the ArrayList.
        synchronized (currentBuffer) {
            currentBuffer.add(op);
        }
    }

    /**
     * Maps a record to its buffered {@link DmlOp} according to {@link #dmlMode}.
     */
    private DmlOp toDmlOp(IN value) {
        if (dmlMode == DmlMode.INSERT) {
            if (value instanceof ChangeEvent) {
                String operation = ((ChangeEvent) value).getOperation();
                if ("u".equals(operation) || "d".equals(operation)) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_DETAIL, "dmlMode=INSERT received an update/delete CDC event"
                                    + " (operation=" + operation + "): updates and deletes cannot be"
                                    + " expressed as appends; use UPSERT or UPSERT_DELETE");
                }
            }
            requireRecordMapper();
            return new DmlOp(DmlOp.KIND_INSERT, copyRow(requireRow(recordMapper.apply(value), value)));
        }

        // Keyed modes: resolve the operation code
        String operation;
        if (opMapper != null) {
            operation = opMapper.apply(value);
        } else if (value instanceof ChangeEvent) {
            operation = ((ChangeEvent) value).getOperation();
        } else {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "dmlMode " + dmlMode + " requires an opMapper for records"
                            + " that are not ChangeEvent instances: " + value.getClass().getName());
        }
        if (operation == null || operation.isEmpty()) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "resolved an empty CDC operation for value: " + value);
        }

        if ("d".equals(operation)) {
            if (dmlMode == DmlMode.UPSERT) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "dmlMode=UPSERT received a delete CDC event: deletes"
                                + " require dmlMode=UPSERT_DELETE");
            }
            return new DmlOp(DmlOp.KIND_DELETE, copyRow(resolveDeleteKey(value)));
        }

        // c / r / u → keyed upsert on the after-image
        Map<String, Object> row = resolveValueRow(value);
        verifyKeyColumnsPresent(row, value);
        return new DmlOp(DmlOp.KIND_UPSERT, copyRow(row));
    }

    private void requireRecordMapper() {
        if (recordMapper == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "dmlMode=INSERT requires a recordMapper");
        }
    }

    private Map<String, Object> requireRow(Map<String, Object> row, IN value) {
        if (row == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "recordMapper returned null for value: " + value);
        }
        return row;
    }

    private Map<String, Object> resolveValueRow(IN value) {
        if (recordMapper != null) {
            return requireRow(recordMapper.apply(value), value);
        }
        if (value instanceof ChangeEvent) {
            Map<String, Object> after = ((ChangeEvent) value).getAfter();
            if (after == null || after.isEmpty()) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "ChangeEvent carries no after-image for an upsert op");
            }
            return after;
        }
        throw new StreamException(ERR_STREAM_STATE_ERROR)
                .param(ARG_DETAIL, "keyed dmlMode without recordMapper requires ChangeEvent records");
    }

    private Map<String, Object> resolveDeleteKey(IN value) {
        if (deleteKeyMapper != null) {
            Map<String, Object> key = deleteKeyMapper.apply(value);
            if (key == null || key.isEmpty()) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "deleteKeyMapper returned an empty key map for value: " + value);
            }
            return key;
        }
        if (value instanceof ChangeEvent) {
            Map<String, Object> key = ((ChangeEvent) value).getKey();
            if (key != null && !key.isEmpty()) {
                return key;
            }
            // fall through to the after-image restriction below
        }
        Map<String, Object> row = resolveValueRow(value);
        Map<String, Object> key = new LinkedHashMap<>();
        for (String col : keyColumns) {
            if (!row.containsKey(col)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "delete key column '" + col + "' is absent from the record"
                                + " (no key image and no deleteKeyMapper)");
            }
            key.put(col, row.get(col));
        }
        return key;
    }

    private void verifyKeyColumnsPresent(Map<String, Object> row, IN value) {
        for (String col : keyColumns) {
            if (!row.containsKey(col)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "upsert value row is missing key column '" + col + "'");
            }
        }
    }

    private Map<String, Object> copyRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
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
    public TaskStateSnapshot saveState(long epochId) throws Exception {
        ensureInitialized();
        synchronized (currentBuffer) {
            if (!currentBuffer.isEmpty()) {
                List<DmlOp> batch = new ArrayList<>(currentBuffer);
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

        List<DmlOp> batch;
        if (raw instanceof List) {
            batch = (List<DmlOp>) raw;
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

            // Idempotent guard: check ledger first (keyed by namespace + epoch + subtask
            // so parallel subtask copies AND different sink branches/jobs sharing one
            // ledger table never collide)
            if (ledgerExists(connection, effectiveLedgerNamespace(), checkpointId, subtaskIndex)) {
                // Guard hit upgraded INFO → WARN with full key context (R5-CON-01): a
                // hit is normal only for this copy's own idempotent re-commit; a hit
                // caused by foreign rows would previously masquerade as healthy INFO.
                LOG.warn("Ledger guard hit for epoch {} — skipping data write (idempotent re-commit); "
                                + "ledgerKey=(namespace={}, subtask={}), ledgerTable={}, targetTable={}",
                        checkpointId, effectiveLedgerNamespace(), subtaskIndex, ledgerTableName, tableName);
                connection.commit();
                committed = true;
                // The epoch IS committed — drop the in-memory staging entry
                // here too, exactly like the normal-path remove below (and like
                // both file-sink branches), so every later finishCommit for the
                // same epoch stays a cheap ledger probe and pendingCommits does
                // not accumulate stale entries across recovery-heavy runs.
                getPendingCommits().remove(checkpointId);
                return;
            }

            // Write data rows
            if (!batch.isEmpty()) {
                writeOps(connection, batch);
            }

            // Write ledger entry (same transaction as data)
            writeLedgerEntry(connection, effectiveLedgerNamespace(), checkpointId, subtaskIndex);

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
     * Item 20 (P-REQ-13, D3-①): pre-submit connectivity probe — the adjudicated
     * {@code beginTransaction() + initializeLedgerTable() + rollback()} combination:
     * dialect/querySpace resolution (configuration face), one physical connection via
     * the idempotent ledger DDL, then rollback clears the (empty) buffer. Red line
     * (D3-⑥): the probe NEVER commits, so no ledger ROW is ever written; the ledger
     * TABLE is an idempotent expected object the job's normal run requires anyway
     * (explicitly exempt).
     */
    @Override
    public void checkConnection() {
        beginTransaction();
        initializeLedgerTable();
        rollback();
    }

    /**
     * Returns a portable DDL string for creating the epoch ledger table.
     * The caller should execute this via {@code jdbcTemplate} before starting the stream.
     *
     * <p>The v2 primary key is {@code (sink_namespace, epoch_id, subtask_id)}: with
     * {@code parallelism > 1} each subtask copy commits its own batch for the same
     * epoch (per-subtask rows), and the {@code sink_namespace} column
     * ({@code jobId|vertexId}) keeps different sink branches — and different jobs
     * sharing one database — from hitting each other's idempotent-commit guard
     * (R5-CON-01). The pre-v2 3-column table is neither migrated nor read: first
     * deployment of this version creates the fresh v2 table.
     */
    public String getLedgerTableDDL() {
        IDialect d = jdbcTemplate.getDialectForQuerySpace(querySpace);
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(d.escapeSQLName(ledgerTableName));
        sb.append(" (");
        sb.append(d.escapeSQLName(LEDGER_NAMESPACE_COL)).append(" VARCHAR(500) NOT NULL, ");
        sb.append(d.escapeSQLName(LEDGER_EPOCH_COL)).append(" BIGINT NOT NULL, ");
        sb.append(d.escapeSQLName(LEDGER_SUBTASK_COL)).append(" INT NOT NULL, ");
        sb.append(d.escapeSQLName(LEDGER_TIMESTAMP_COL)).append(" TIMESTAMP, ");
        sb.append("PRIMARY KEY (").append(d.escapeSQLName(LEDGER_NAMESPACE_COL))
                .append(", ").append(d.escapeSQLName(LEDGER_EPOCH_COL))
                .append(", ").append(d.escapeSQLName(LEDGER_SUBTASK_COL)).append(")");
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

    private boolean ledgerExists(Connection connection, String namespace, long epochId,
                                 int subtaskIndex) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(ledgerExistsSql)) {
            ps.setString(1, namespace);
            ps.setLong(2, epochId);
            ps.setInt(3, subtaskIndex);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void writeLedgerEntry(Connection connection, String namespace, long epochId,
                                  int subtaskIndex) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(insertLedgerSql)) {
            ps.setString(1, namespace);
            ps.setLong(2, epochId);
            ps.setInt(3, subtaskIndex);
            ps.setTimestamp(4, CoreMetrics.currentTimestamp());
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private void writeOps(Connection connection, List<DmlOp> batch) throws SQLException {
        if (dmlMode == DmlMode.INSERT) {
            writeInserts(connection, batch);
            return;
        }
        writeKeyedOps(connection, batch);
    }

    /**
     * INSERT mode: append-only batch of KIND_INSERT ops through the cached
     * {@code insertDataSql}. Segments large epochs so no single executeBatch buffers an
     * unbounded number of rows (driver OOM / packet limits); all segments stay inside the
     * caller's single transaction.
     */
    private void writeInserts(Connection connection, List<DmlOp> batch) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(insertDataSql)) {
            int pending = 0;
            for (DmlOp op : batch) {
                if (op.kind != DmlOp.KIND_INSERT) {
                    throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                            .param(ARG_DETAIL, "INSERT-mode batch carries op kind " + op.kind);
                }
                bindRow(ps, columnNames, op.row);
                ps.addBatch();
                if (++pending >= maxBatchSize) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                ps.executeBatch();
            }
        }
    }

    /**
     * Keyed modes: fold the batch by key tuple (last-write-wins, insertion order
     * preserved — same-key ops collapse so intra-batch ordering survives batching), then
     * execute upserts and deletes through their prepared statements. Different keys are
     * row-independent, so statement-grouped execution inside one transaction is
     * order-safe; same keys were already folded.
     */
    private void writeKeyedOps(Connection connection, List<DmlOp> batch) throws SQLException {
        LinkedHashMap<String, DmlOp> folded = new LinkedHashMap<>();
        for (DmlOp op : batch) {
            folded.put(keyTuple(op.row), op);
        }

        List<String> columns = resolveKeyedColumns(folded);
        PreparedStatement upsertPs = null;
        PreparedStatement deletePs = null;
        try {
            int pendingUpserts = 0;
            int pendingDeletes = 0;
            List<DmlOp> deletes = new ArrayList<>();
            for (DmlOp op : folded.values()) {
                if (op.kind == DmlOp.KIND_UPSERT) {
                    if (upsertPs == null) {
                        upsertPs = connection.prepareStatement(upsertSql);
                    }
                    bindRow(upsertPs, columns, op.row);
                    upsertPs.addBatch();
                    if (++pendingUpserts >= maxBatchSize) {
                        upsertPs.executeBatch();
                        pendingUpserts = 0;
                    }
                } else if (op.kind == DmlOp.KIND_DELETE) {
                    if (deletePs == null) {
                        deletePs = connection.prepareStatement(deleteSql);
                    }
                    bindRow(deletePs, keyColumns, op.row);
                    deletePs.addBatch();
                    if (++pendingDeletes >= maxBatchSize) {
                        deletePs.executeBatch();
                        pendingDeletes = 0;
                    }
                } else {
                    throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                            .param(ARG_DETAIL, "keyed batch carries op kind " + op.kind);
                }
            }
            if (upsertPs != null && pendingUpserts > 0) {
                upsertPs.executeBatch();
            }
            if (deletePs != null && pendingDeletes > 0) {
                deletePs.executeBatch();
            }
        } finally {
            if (upsertPs != null) {
                try {
                    upsertPs.close();
                } catch (SQLException closeErr) {
                    LOG.warn("Failed to close upsert statement", closeErr);
                }
            }
            if (deletePs != null) {
                try {
                    deletePs.close();
                } catch (SQLException closeErr) {
                    LOG.warn("Failed to close delete statement", closeErr);
                }
            }
        }
    }

    /**
     * The value column set of the keyed mode, established from the first buffered row and
     * cached together with the upsert/delete SQL; a later batch with a different column
     * set is a typed error (no silent schema drift within one sink instance).
     */
    private List<String> resolveKeyedColumns(LinkedHashMap<String, DmlOp> folded) {
        List<String> first = null;
        for (DmlOp op : folded.values()) {
            if (op.kind == DmlOp.KIND_UPSERT) {
                first = new ArrayList<>(op.row.keySet());
                break;
            }
        }
        if (first == null) {
            // delete-only batch: only the delete statement is needed
            if (deleteSql == null) {
                synchronized (this) {
                    if (deleteSql == null) {
                        deleteSql = buildDeleteSql(dialect);
                    }
                }
            }
            return keyColumns;
        }
        synchronized (this) {
            if (keyedColumns == null) {
                keyedColumns = first;
                upsertDialect = resolveUpsertDialect();
                upsertSql = buildUpsertSql(dialect, upsertDialect, keyedColumns);
                deleteSql = buildDeleteSql(dialect);
            } else if (!keyedColumns.equals(first)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "upsert column set changed within one sink instance: "
                                + keyedColumns + " -> " + first
                                + " (schema drift is not silently widened)");
            }
        }
        return keyedColumns;
    }

    private String keyTuple(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (String col : keyColumns) {
            sb.append(row.get(col)).append('\u0000');
        }
        return sb.toString();
    }

    private void bindRow(PreparedStatement ps, List<String> columns, Map<String, Object> row)
            throws SQLException {
        int index = 1;
        for (String col : columns) {
            Object value = row.get(col);
            if (value == null) {
                ps.setNull(index, Types.NULL);
            } else {
                ps.setObject(index, value);
            }
            index++;
        }
    }

    private UpsertDialect resolveUpsertDialect() {
        String name = dialect.getName() == null ? "" : dialect.getName().toLowerCase();
        if (name.startsWith("postgresql") || name.startsWith("pg")) {
            return UpsertDialect.POSTGRESQL;
        }
        if (name.startsWith("mysql") || name.startsWith("mariadb")) {
            return UpsertDialect.MYSQL;
        }
        if (name.startsWith("h2")) {
            return UpsertDialect.H2;
        }
        throw new StreamException(ERR_STREAM_STATE_ERROR)
                .param(ARG_DETAIL, "dialect '" + dialect.getName()
                        + "' has no keyed upsert support in the built-in sync scope"
                        + " (supported: mysql/mariadb, postgresql; h2 for tests)");
    }

    /**
     * Keyed upsert statement for the resolved dialect (cdc-design.md §3.5):
     * MySQL/MariaDB INSERT ... ON DUPLICATE KEY UPDATE, PostgreSQL INSERT ... ON CONFLICT
     * DO UPDATE SET col = EXCLUDED.col, H2 MERGE INTO ... KEY (...) for tests.
     */
    private String buildUpsertSql(IDialect d, UpsertDialect style, List<String> columns) {
        StringBuilder columnList = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                columnList.append(", ");
            }
            columnList.append(d.escapeSQLName(columns.get(i)));
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
        }
        StringBuilder keyList = new StringBuilder();
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0) {
                keyList.append(", ");
            }
            keyList.append(d.escapeSQLName(keyColumns.get(i)));
        }

        StringBuilder updateColumns = new StringBuilder();
        for (String col : columns) {
            if (keyColumns.contains(col)) {
                continue;
            }
            if (updateColumns.length() > 0) {
                updateColumns.append(", ");
            }
            updateColumns.append(d.escapeSQLName(col));
        }

        String insert = "INSERT INTO " + d.escapeSQLName(tableName)
                + " (" + columnList + ") VALUES (" + placeholders + ")";
        switch (style) {
            case POSTGRESQL: {
                if (updateColumns.length() == 0) {
                    return insert + " ON CONFLICT (" + keyList + ") DO NOTHING";
                }
                StringBuilder set = new StringBuilder();
                for (String col : columns) {
                    if (keyColumns.contains(col)) {
                        continue;
                    }
                    if (set.length() > 0) {
                        set.append(", ");
                    }
                    set.append(d.escapeSQLName(col)).append(" = EXCLUDED.")
                            .append(d.escapeSQLName(col));
                }
                return insert + " ON CONFLICT (" + keyList + ") DO UPDATE SET " + set;
            }
            case MYSQL: {
                if (updateColumns.length() == 0) {
                    String firstKey = d.escapeSQLName(keyColumns.get(0));
                    return insert + " ON DUPLICATE KEY UPDATE " + firstKey + " = " + firstKey;
                }
                StringBuilder set = new StringBuilder();
                for (String col : columns) {
                    if (keyColumns.contains(col)) {
                        continue;
                    }
                    if (set.length() > 0) {
                        set.append(", ");
                    }
                    set.append(d.escapeSQLName(col)).append(" = VALUES(")
                            .append(d.escapeSQLName(col)).append(")");
                }
                return insert + " ON DUPLICATE KEY UPDATE " + set;
            }
            case H2:
                return "MERGE INTO " + d.escapeSQLName(tableName)
                        + " (" + columnList + ") KEY (" + keyList + ") VALUES (" + placeholders + ")";
            default:
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "unsupported upsert dialect style: " + style);
        }
    }

    /**
     * Parameterized keyed delete: DELETE FROM table WHERE key1 = ? AND key2 = ? ...
     */
    private String buildDeleteSql(IDialect d) {
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            where.append(d.escapeSQLName(keyColumns.get(i))).append(" = ?");
        }
        return "DELETE FROM " + d.escapeSQLName(tableName) + " WHERE " + where;
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
                + " (" + d.escapeSQLName(LEDGER_NAMESPACE_COL) + ", "
                + d.escapeSQLName(LEDGER_EPOCH_COL) + ", " + d.escapeSQLName(LEDGER_SUBTASK_COL)
                + ", " + d.escapeSQLName(LEDGER_TIMESTAMP_COL) + ")"
                + " VALUES (?, ?, ?, ?)";
    }

    private String buildLedgerExistsSql(IDialect d) {
        return "SELECT 1 FROM " + d.escapeSQLName(ledgerTableName)
                + " WHERE " + d.escapeSQLName(LEDGER_NAMESPACE_COL) + " = ?"
                + " AND " + d.escapeSQLName(LEDGER_EPOCH_COL) + " = ?"
                + " AND " + d.escapeSQLName(LEDGER_SUBTASK_COL) + " = ?";
    }
}
