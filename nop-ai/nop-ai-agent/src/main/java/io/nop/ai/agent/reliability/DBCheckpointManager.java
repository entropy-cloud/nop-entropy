package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.TenantSql;

import javax.sql.DataSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-backed {@link ICheckpointManager} implementation — the drop-in
 * persistent sibling of {@link FileBackedCheckpointManager} (file-backed) and
 * {@link ToolExecutionCheckpoint} (in-memory). Per-session checkpoints are
 * persisted to the {@code ai_agent_checkpoint} table so the resume-point
 * watermark survives manager-instance reconstruction and is retrievable by any
 * service instance sharing the same DB (cross-process recovery).
 *
 * <p>This is the DB-backed successor referenced in the Javadoc of
 * {@link ICheckpointManager}, {@link Checkpoint}, and
 * {@link ToolExecutionCheckpoint}. It is registered explicitly via
 * {@code DefaultAgentEngine.setCheckpointManager(new DBCheckpointManager(dataSource))};
 * the shipped default remains {@link NoOpCheckpoint}.
 *
 * <p><b>Persistence scheme</b>: raw JDBC ({@link DataSource} +
 * {@link PreparedStatement}), consistent with {@code DBSessionStore} /
 * {@code DBDenialLedger} / {@code DBMessageService}. The {@link Checkpoint}
 * fields use a <b>hybrid column layout</b>: 9 scalar columns are directly
 * queryable via native SQL ({@code WHERE SESSION_ID = ? ORDER BY SEQ DESC});
 * the two long-text fields ({@code INPUT_SUMMARY} / {@code OUTPUT_SUMMARY})
 * are stored as CLOB because they carry the full tool-call I/O payload, which
 * can exceed VARCHAR limits for tools like {@code file_write} /
 * {@code file_read}.
 *
 * <p><b>Append-only (INSERT, not upsert)</b>: checkpoint semantics are
 * append-only (a new watermark = a new row). {@link #saveCheckpoint} uses
 * {@code INSERT INTO}, not {@code MERGE INTO}. A duplicate-watermark INSERT is
 * a programming error rejected by the DB PK constraint (fail-fast
 * {@link NopAiAgentException}, not a silent overwrite). This differs from
 * {@code DBSessionStore.save} which uses {@code MERGE INTO} upsert because a
 * session is full-state overwrite.
 *
 * <p><b>Write-through cache</b>: a {@link ConcurrentHashMap} byWatermark index
 * + a {@link ConcurrentHashMap} bySession list + a loadedSessions negative
 * cache. {@code saveCheckpoint} writes through (DB + cache).
 * {@code getLatestCheckpoint} returns from cache when the session is loaded;
 * on cache-miss it loads the full session checkpoint set from the DB
 * ({@code WHERE SESSION_ID = ? ORDER BY SEQ DESC}, no {@code LIMIT 1}) into the
 * warm cache, then returns the highest-seq entry. {@code getCheckpoint} does
 * a PK lookup on cache-miss. The {@code loadedSessions} negative cache
 * prevents repeated DB round-trips for sessions that have no checkpoints.
 *
 * <p><b>Anonymous session</b>: a null {@code sessionId} is persisted to the DB
 * with a null {@code SESSION_ID} column (the column is nullable). Such
 * checkpoints are retrievable via {@link #getCheckpoint(watermark)} PK lookup.
 * {@link #getLatestCheckpoint} returns {@code null} for a null sessionId
 * (consistent with {@link FileBackedCheckpointManager}).
 *
 * <p><b>Thread safety</b>: guaranteed by DB operations + concurrent data
 * structures. Each {@code saveCheckpoint} / {@code getLatestCheckpoint} /
 * {@code getCheckpoint} DB operation is an atomic SQL statement
 * (INSERT / SELECT). The cache maps are {@link ConcurrentHashMap}. Multiple
 * sessions may access the same manager instance concurrently; per-session
 * operations are isolated by {@code WHERE SESSION_ID = ?}.
 */
public class DBCheckpointManager implements ICheckpointManager {

    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;

    private final ConcurrentHashMap<String, Checkpoint> byWatermark = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Checkpoint>> bySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> loadedSessions = new ConcurrentHashMap<>();

    /**
     * Create a DB-backed checkpoint manager and initialize the DB schema
     * (create table + index if absent). Uses the backward-compatible
     * {@link NullTenantResolver}.
     *
     * @param dataSource the JDBC data source; never null
     */
    public DBCheckpointManager(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed checkpoint manager with a contextual tenant resolver
     * (plan 232 / vision §5.1). When the resolver reports a non-null tenant,
     * INSERT writes {@code TENANT_ID}, SELECTs inject the tenant {@code WHERE},
     * and the write-through cache is bypassed (the bySession cache is keyed by
     * sessionId without a tenant dimension). When the resolver reports
     * {@code null}, behaviour is byte-identical to the original (zero
     * regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DBCheckpointManager(DataSource dataSource, ITenantResolver tenantResolver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
        initSchema();
    }

    private String currentTenant() {
        return tenantResolver.resolveTenantId();
    }

    /**
     * Plan 232: the bySession cache is keyed by sessionId without a tenant
     * dimension, so it is bypassed when a tenant context is active (mirrors
     * {@code DBSessionStore} cache bypass).
     */
    private boolean cacheEnabled() {
        return currentTenant() == null;
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentCheckpointTable.DDL_CREATE_INDEX);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBCheckpointManager: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveCheckpoint(Checkpoint checkpoint) {
        if (checkpoint == null) {
            throw new NopAiAgentException("DBCheckpointManager.saveCheckpoint: checkpoint must not be null");
        }

        String tenant = currentTenant();
        String insertSql = "INSERT INTO " + AiAgentCheckpointTable.TABLE_NAME
                + " (" + AiAgentCheckpointTable.COL_WATERMARK
                + ", " + AiAgentCheckpointTable.COL_SESSION_ID
                + ", " + AiAgentCheckpointTable.COL_SEQ
                + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TIMESTAMP
                + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TYPE
                + ", " + AiAgentCheckpointTable.COL_TOOL_NAME
                + ", " + AiAgentCheckpointTable.COL_CALL_ID
                + ", " + AiAgentCheckpointTable.COL_INPUT_SUMMARY
                + ", " + AiAgentCheckpointTable.COL_OUTPUT_SUMMARY
                + ", " + AiAgentCheckpointTable.COL_MESSAGE_COUNT
                + ", " + AiAgentCheckpointTable.COL_TOKEN_ESTIMATE;
        if (tenant != null) {
            insertSql += ", " + AiAgentCheckpointTable.COL_TENANT_ID;
        }
        insertSql += ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
        if (tenant != null) {
            insertSql += ", ?";
        }
        insertSql += ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, checkpoint.getWatermark());
            ps.setString(2, checkpoint.getSessionId());
            ps.setInt(3, checkpoint.getSeq());
            ps.setLong(4, checkpoint.getTimestamp());
            ps.setString(5, checkpoint.getType().name());
            ps.setString(6, checkpoint.getToolName());
            ps.setString(7, checkpoint.getCallId());
            setClob(ps, 8, checkpoint.getInputSummary());
            setClob(ps, 9, checkpoint.getOutputSummary());
            ps.setInt(10, checkpoint.getMessageCount());
            ps.setLong(11, checkpoint.getTokenEstimate());
            if (tenant != null) {
                ps.setString(12, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBCheckpointManager.saveCheckpoint: failed to persist checkpoint '"
                            + checkpoint.getWatermark() + "': " + e.getMessage(), e);
        }

        if (cacheEnabled()) {
            // Write-through cache
            byWatermark.put(checkpoint.getWatermark(), checkpoint);
            String sid = checkpoint.getSessionId();
            if (sid != null) {
                bySession.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(checkpoint);
            }
        }
    }

    @Override
    public Checkpoint getLatestCheckpoint(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        // Plan 232: bypass the bySession cache when a tenant context is active
        // (the cache key has no tenant dimension). Go direct to the DB with a
        // tenant-scoped latest-row query.
        if (!cacheEnabled()) {
            return loadLatestCheckpointFromDb(sessionId);
        }
        ensureSessionLoaded(sessionId);
        List<Checkpoint> list = bySession.get(sessionId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        synchronized (list) {
            return list.get(list.size() - 1);
        }
    }

    @Override
    public Checkpoint getCheckpoint(String watermark) {
        if (watermark == null) {
            return null;
        }
        if (cacheEnabled()) {
            Checkpoint cached = byWatermark.get(watermark);
            if (cached != null) {
                return cached;
            }
        }
        return loadCheckpointFromDb(watermark);
    }

    /**
     * Return an unmodifiable snapshot copy of all checkpoints recorded for a
     * session, in ascending seq order. Mirrors
     * {@link ToolExecutionCheckpoint#getCheckpoints(String)} /
     * {@link FileBackedCheckpointManager#getCheckpoints(String)} so callers can
     * inspect the full checkpoint history regardless of the manager backend.
     *
     * @param sessionId the session identifier; may be null (returns empty list)
     * @return an unmodifiable list of checkpoints for the session; empty if
     *         none recorded
     */
    public List<Checkpoint> getCheckpoints(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        // Plan 232: bypass cache when a tenant context is active — go direct
        // to the DB with a tenant-scoped query (highest-seq-first, then
        // reverse to ascending).
        if (!cacheEnabled()) {
            List<Checkpoint> loaded = loadSessionListFromDb(sessionId);
            return List.copyOf(loaded);
        }
        ensureSessionLoaded(sessionId);
        List<Checkpoint> list = bySession.get(sessionId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    // ========================================================================
    // Internal: cache-miss DB loaders
    // ========================================================================

    private void ensureSessionLoaded(String sessionId) {
        if (loadedSessions.putIfAbsent(sessionId, Boolean.TRUE) == null) {
            loadSessionFromDb(sessionId);
        }
    }

    private Checkpoint loadLatestCheckpointFromDb(String sessionId) {
        String tenant = currentTenant();
        String sql = checkpointColumnsSelect()
                + " WHERE " + AiAgentCheckpointTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentCheckpointTable.COL_TENANT_ID);
        }
        sql += " ORDER BY " + AiAgentCheckpointTable.COL_SEQ + " DESC FETCH FIRST 1 ROWS ONLY";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readCheckpoint(rs);
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBCheckpointManager.loadLatestCheckpointFromDb: failed to load latest checkpoint for session '"
                            + sessionId + "': " + e.getMessage(), e);
        }
        return null;
    }

    private List<Checkpoint> loadSessionListFromDb(String sessionId) {
        List<Checkpoint> loaded = loadSessionRowsFromDb(sessionId);
        // loadSessionRowsFromDb returns DESC; reverse to ascending.
        Collections.reverse(loaded);
        return loaded;
    }

    private void loadSessionFromDb(String sessionId) {
        List<Checkpoint> loaded = loadSessionRowsFromDb(sessionId);
        if (!loaded.isEmpty()) {
            // Reverse to ascending order so list.get(size-1) is the highest seq
            Collections.reverse(loaded);

            // Populate the byWatermark index with the full set (cache-enabled
            // path only — the tenant-active path never calls loadSessionFromDb).
            for (Checkpoint cp : loaded) {
                byWatermark.putIfAbsent(cp.getWatermark(), cp);
            }

            // Compaction-aware truncation (plan 188): truncate the active
            // restore set (bySession) to start from the most recent
            // CheckpointType.COMPACTION checkpoint (inclusive). Pre-compaction
            // checkpoints reference messageCount values beyond the compacted
            // session's messageCount, violating the documented invariant
            // checkpoint.messageCount <= session.messageCount. The byWatermark
            // index was already populated above with the full set, and
            // DB-backed getCheckpoint(oldWatermark) additionally falls back to
            // loadCheckpointFromDb — so pre-compaction checkpoints remain
            // resolvable for audit/debug by watermark even after truncation.
            // The ai_agent_checkpoint table rows are never deleted.
            List<Checkpoint> active = CompactionAwareTruncation.truncateToLatestCompaction(loaded);
            bySession.put(sessionId, Collections.synchronizedList(active));
        }
    }

    /**
     * Select all checkpoint rows for a session in DESC seq order (no cache
     * writes). Plan 232: injects the tenant {@code WHERE} when a tenant context
     * is active.
     */
    private List<Checkpoint> loadSessionRowsFromDb(String sessionId) {
        String tenant = currentTenant();
        String sql = checkpointColumnsSelect()
                + " WHERE " + AiAgentCheckpointTable.COL_SESSION_ID + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentCheckpointTable.COL_TENANT_ID);
        }
        sql += " ORDER BY " + AiAgentCheckpointTable.COL_SEQ + " DESC";

        List<Checkpoint> loaded = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    loaded.add(readCheckpoint(rs));
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBCheckpointManager.loadSessionRowsFromDb: failed to load checkpoints for session '"
                            + sessionId + "': " + e.getMessage(), e);
        }
        return loaded;
    }

    private String checkpointColumnsSelect() {
        return "SELECT " + AiAgentCheckpointTable.COL_WATERMARK
                + ", " + AiAgentCheckpointTable.COL_SESSION_ID
                + ", " + AiAgentCheckpointTable.COL_SEQ
                + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TIMESTAMP
                + ", " + AiAgentCheckpointTable.COL_CHECKPOINT_TYPE
                + ", " + AiAgentCheckpointTable.COL_TOOL_NAME
                + ", " + AiAgentCheckpointTable.COL_CALL_ID
                + ", " + AiAgentCheckpointTable.COL_INPUT_SUMMARY
                + ", " + AiAgentCheckpointTable.COL_OUTPUT_SUMMARY
                + ", " + AiAgentCheckpointTable.COL_MESSAGE_COUNT
                + ", " + AiAgentCheckpointTable.COL_TOKEN_ESTIMATE
                + " FROM " + AiAgentCheckpointTable.TABLE_NAME;
    }

    private Checkpoint loadCheckpointFromDb(String watermark) {
        String tenant = currentTenant();
        String sql = checkpointColumnsSelect()
                + " WHERE " + AiAgentCheckpointTable.COL_WATERMARK + " = ?";
        if (tenant != null) {
            sql += TenantSql.whereTenant(AiAgentCheckpointTable.COL_TENANT_ID);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, watermark);
            if (tenant != null) {
                ps.setString(2, tenant);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Checkpoint cp = readCheckpoint(rs);
                    if (cacheEnabled()) {
                        byWatermark.putIfAbsent(cp.getWatermark(), cp);
                    }
                    return cp;
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBCheckpointManager.loadCheckpointFromDb: failed to load checkpoint '"
                            + watermark + "': " + e.getMessage(), e);
        }
        return null;
    }

    private static Checkpoint readCheckpoint(ResultSet rs) throws SQLException {
        return Checkpoint.of(
                rs.getString(AiAgentCheckpointTable.COL_SESSION_ID),
                rs.getString(AiAgentCheckpointTable.COL_WATERMARK),
                rs.getInt(AiAgentCheckpointTable.COL_SEQ),
                rs.getLong(AiAgentCheckpointTable.COL_CHECKPOINT_TIMESTAMP),
                CheckpointType.valueOf(rs.getString(AiAgentCheckpointTable.COL_CHECKPOINT_TYPE)),
                rs.getString(AiAgentCheckpointTable.COL_TOOL_NAME),
                rs.getString(AiAgentCheckpointTable.COL_CALL_ID),
                readClob(rs, AiAgentCheckpointTable.COL_INPUT_SUMMARY),
                readClob(rs, AiAgentCheckpointTable.COL_OUTPUT_SUMMARY),
                rs.getInt(AiAgentCheckpointTable.COL_MESSAGE_COUNT),
                rs.getLong(AiAgentCheckpointTable.COL_TOKEN_ESTIMATE)
        );
    }

    private static void setClob(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.CLOB);
        } else {
            ps.setCharacterStream(index, new StringReader(value), value.length());
        }
    }

    private static String readClob(ResultSet rs, String columnLabel) throws SQLException {
        java.sql.Clob clob = rs.getClob(columnLabel);
        if (clob == null) {
            return null;
        }
        try (StringWriter writer = new StringWriter()) {
            try (java.io.Reader reader = clob.getCharacterStream()) {
                reader.transferTo(writer);
            }
            return writer.toString();
        } catch (java.io.IOException e) {
            throw new SQLException("Failed to read CLOB column " + columnLabel, e);
        }
    }
}
