package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * Database-backed {@link IDenialLedger} implementation — a sibling of
 * {@link NoOpDenialLedger} (pass-through default). Per-session denial records
 * are persisted to the {@code ai_agent_denial} table so the count and the
 * paused state survive ledger-instance reconstruction (session recovery /
 * cross-process).
 *
 * <p>This is the functional successor to the {@link NoOpDenialLedger} default
 * (design §6.2 {@code persistence = DB}). It is registered explicitly via
 * {@code DefaultAgentEngine.setDenialLedger(new DBDenialLedger(dataSource))};
 * the shipped default remains {@link NoOpDenialLedger}.
 *
 * <p><b>Persistence scheme</b>: raw JDBC ({@link DataSource} +
 * {@link PreparedStatement}), consistent with {@code DBMessageService}. The
 * {@code DenialRecord} fields are stored as columns (one column per field)
 * rather than a JSON blob, because all fields are simple types (String + enum
 * + long) and the core operations are per-session {@code COUNT} and
 * per-session {@code DELETE} — efficient native SQL with column storage.
 *
 * <p><b>Thread safety</b>: guaranteed by DB operations, not in-memory locks.
 * Each {@link #recordDenial} is an atomic INSERT followed by a COUNT read from
 * the DB (the count is never accumulated in memory). Multiple sessions may
 * access the same ledger instance concurrently; per-session
 * INSERT/COUNT/DELETE are isolated by {@code WHERE SESSION_ID = ?}. No
 * {@code ConcurrentHashMap} in-memory state is used — the count is always read
 * from the DB, ensuring cross-instance consistency.
 *
 * <p><b>Anonymous session</b>: a null {@code sessionId} is not persisted
 * (anonymous denials are not counted). {@link #recordDenial} returns
 * {@code count = 0, thresholdExceeded = false}; {@link #isPaused} and
 * {@link #getDenialCount} return {@code false} / {@code 0}. This is a
 * predictable, documented behavior — anonymous denials cannot accumulate a
 * pause state.
 */
public class DBDenialLedger implements IDenialLedger {

    private static final Logger LOG = LoggerFactory.getLogger(DBDenialLedger.class);

    /**
     * The default denial threshold (design §6.2 {@code denialThreshold = 3}):
     * after this many per-session denials the session is paused.
     */
    public static final int DEFAULT_DENIAL_THRESHOLD = 3;

    private final DataSource dataSource;
    private final int denialThreshold;

    /**
     * Create a {@code DBDenialLedger} with the default threshold
     * ({@value #DEFAULT_DENIAL_THRESHOLD}) and initialize the DB schema.
     *
     * @param dataSource the JDBC data source; never null
     */
    public DBDenialLedger(DataSource dataSource) {
        this(dataSource, DEFAULT_DENIAL_THRESHOLD);
    }

    /**
     * Create a {@code DBDenialLedger} with a configurable threshold and
     * initialize the DB schema (create table + index if absent).
     *
     * @param dataSource       the JDBC data source; never null
     * @param denialThreshold  the per-session denial count at which the session
     *                         is paused; must be positive
     */
    public DBDenialLedger(DataSource dataSource, int denialThreshold) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        if (denialThreshold <= 0) {
            throw new IllegalArgumentException(
                    "denialThreshold must be positive, got: " + denialThreshold);
        }
        this.denialThreshold = denialThreshold;
        initSchema();
    }

    /**
     * @return the configured denial threshold for this ledger
     */
    public int getDenialThreshold() {
        return denialThreshold;
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentDenialTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentDenialTable.DDL_CREATE_INDEX);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBDenialLedger: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    @Override
    public DenialRecordOutcome recordDenial(DenialRecord record) {
        Objects.requireNonNull(record, "DenialRecord must not be null");

        String sessionId = record.getSessionId();
        if (sessionId == null) {
            // Anonymous denials are not persisted: a null sessionId cannot
            // accumulate a pause state. Return a predictable non-exceeded
            // outcome — this is documented behavior, not a silent skip.
            return DenialRecordOutcome.of(0, false);
        }

        String sid = generateSid();
        String insertSql = "INSERT INTO " + AiAgentDenialTable.TABLE_NAME
                + " (" + AiAgentDenialTable.COL_SID
                + ", " + AiAgentDenialTable.COL_SESSION_ID
                + ", " + AiAgentDenialTable.COL_TOOL_NAME
                + ", " + AiAgentDenialTable.COL_LAYER_SOURCE
                + ", " + AiAgentDenialTable.COL_REASON
                + ", " + AiAgentDenialTable.COL_MATCHED_RULE
                + ", " + AiAgentDenialTable.COL_DENIAL_TIMESTAMP
                + ", " + AiAgentDenialTable.COL_CREATED_AT
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, sid);
            ps.setString(2, sessionId);
            ps.setString(3, record.getToolName());
            ps.setString(4, record.getLayerSource().name());
            ps.setString(5, record.getReason());
            ps.setString(6, record.getMatchedRule());
            ps.setLong(7, record.getTimestamp());
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBDenialLedger: failed to record denial for session '"
                            + sessionId + "': " + e.getMessage(), e);
        }

        // Read the cumulative count from the DB (never accumulated in memory).
        int count = countDenials(sessionId);
        boolean exceeded = count >= denialThreshold;
        if (exceeded) {
            LOG.warn("Session {} reached denial threshold (count={}, threshold={})",
                    sessionId, count, denialThreshold);
        }
        return DenialRecordOutcome.of(count, exceeded);
    }

    @Override
    public boolean isPaused(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return countDenials(sessionId) >= denialThreshold;
    }

    @Override
    public int getDenialCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        return countDenials(sessionId);
    }

    @Override
    public void reset(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String deleteSql = "DELETE FROM " + AiAgentDenialTable.TABLE_NAME
                + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBDenialLedger: failed to reset denials for session '"
                            + sessionId + "': " + e.getMessage(), e);
        }
    }

    private int countDenials(String sessionId) {
        String sql = "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME
                + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DBDenialLedger: failed to count denials for session '"
                            + sessionId + "': " + e.getMessage(), e);
        }
        return 0;
    }

    private static String generateSid() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
