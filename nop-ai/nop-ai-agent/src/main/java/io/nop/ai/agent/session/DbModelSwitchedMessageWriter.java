package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Production {@link IModelSwitchedMessageWriter} that persists model-switched
 * audit messages to the {@code nop_ai_session_message} table via raw JDBC
 * (design {@code nop-ai-agent-usage-and-billing.md} §3.5 / plan 205 L2-21).
 *
 * <p><b>Wiring</b>: this is an opt-in writer. The shipped default remains
 * {@link NoOpModelSwitchedMessageWriter} (pass-through). Integrators register
 * this writer explicitly via
 * {@code ReActAgentExecutor.Builder.modelSwitchedMessageWriter} — exactly like
 * {@code DbUsageRecorder} / {@code DBSessionStore} / {@code DBMessageService}.
 *
 * <p><b>Module boundary</b>: the agent module does not depend on
 * {@code nop-ai-dao}. This writer uses raw JDBC ({@link DataSource} +
 * {@link PreparedStatement}) and never references the
 * {@code NopAiSessionMessage} ORM entity, consistent with every other DB
 * component in this module.
 *
 * <p><b>role=80 constant</b>: the canonical constant lives in
 * {@code _NopAiDaoConstants.MESSAGE_TYPE_MODEL_SWITCHED} (nop-ai-dao), but
 * since {@code nop-ai-agent} cannot reference {@code nop-ai-dao}, this class
 * defines a local mirror constant {@link #ROLE_MODEL_SWITCHED}. The value is
 * kept in sync with the nop-ai-dao constant. This follows the
 * {@code AiAgentSessionTable} / {@code NopAiChatResponseTable} precedent of
 * defining local table/column constants rather than referencing generated
 * nop-ai-dao constants.
 *
 * <p><b>SEQ management</b>: the {@code seq} value is supplied by the caller
 * (the ReAct loop maintains a per-execution monotonically increasing counter).
 * The writer does not manage SEQ allocation — it trusts the caller's value and
 * writes it as-is. The unique index {@code uk_nop_ai_session_msg_seq
 * (session_id, seq)} enforces per-session sequence uniqueness at the DB level.
 *
 * <p><b>Exception handling</b> (Minimum Rules #24): SQL failures are wrapped
 * in {@link NopAiAgentException} and never swallowed. routingReason/complexity
 * being null is legitimate graceful degradation ({@code PassThroughModelRouter}
 * returns null complexity) and is written as-is to the metadata JSON.
 *
 * <p><b>Thread safety</b>: {@link DataSource} is thread-safe and every
 * {@link #writeModelSwitched} call uses its own short-lived {@link Connection}
 * (try-with-resources). The writer holds no mutable shared state, so a single
 * instance may be shared across concurrent ReAct loops.
 */
public class DbModelSwitchedMessageWriter implements IModelSwitchedMessageWriter {

    private static final Logger LOG = LoggerFactory.getLogger(DbModelSwitchedMessageWriter.class);

    /**
     * Role value for model-switched audit messages. Mirrors
     * {@code _NopAiDaoConstants.MESSAGE_TYPE_MODEL_SWITCHED = 80} in
     * nop-ai-dao. Kept as a local constant because {@code nop-ai-agent} does
     * not depend on {@code nop-ai-dao}.
     */
    public static final int ROLE_MODEL_SWITCHED = 80;

    static final String SQL_INSERT_MESSAGE = ""
            + "INSERT INTO " + NopAiSessionMessageTable.TABLE_NAME
            + " (" + NopAiSessionMessageTable.COL_ID
            + ", " + NopAiSessionMessageTable.COL_SESSION_ID
            + ", " + NopAiSessionMessageTable.COL_ROLE
            + ", " + NopAiSessionMessageTable.COL_SEQ
            + ", " + NopAiSessionMessageTable.COL_CONTENT
            + ", " + NopAiSessionMessageTable.COL_METADATA
            + ", " + NopAiSessionMessageTable.COL_VERSION
            + ", " + NopAiSessionMessageTable.COL_CREATED_BY
            + ", " + NopAiSessionMessageTable.COL_CREATE_TIME
            + ", " + NopAiSessionMessageTable.COL_UPDATED_BY
            + ", " + NopAiSessionMessageTable.COL_UPDATE_TIME
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    /**
     * Create a DB-backed model-switched message writer and defensively
     * initialize the {@code nop_ai_session_message} schema (CREATE TABLE IF
     * NOT EXISTS + CREATE UNIQUE INDEX IF NOT EXISTS).
     *
     * @param dataSource the JDBC data source; never null
     */
    public DbModelSwitchedMessageWriter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(NopAiSessionMessageTable.DDL_CREATE_TABLE);
            stmt.execute(NopAiSessionMessageTable.DDL_CREATE_INDEX_UK_SEQ);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbModelSwitchedMessageWriter: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeModelSwitched(String sessionId, String fromModel, String toModel,
                                   String routingReason, String complexity, long seq) {
        if (sessionId == null) {
            throw new NopAiAgentException(
                    "DbModelSwitchedMessageWriter.writeModelSwitched: sessionId must not be null");
        }

        String rowId = StringHelper.generateUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fromModel", fromModel);
        metadata.put("toModel", toModel);
        metadata.put("routingReason", routingReason);
        metadata.put("complexity", complexity);
        String metadataJson = JsonTool.stringify(metadata);

        String content = "model-switched: " + fromModel + " -> " + toModel;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_MESSAGE)) {
            ps.setString(1, rowId);
            ps.setString(2, sessionId);
            ps.setInt(3, ROLE_MODEL_SWITCHED);
            ps.setLong(4, seq);
            ps.setString(5, content);
            ps.setString(6, metadataJson);
            ps.setInt(7, 0);
            ps.setString(8, "ai-agent");
            ps.setTimestamp(9, now);
            ps.setString(10, "ai-agent");
            ps.setTimestamp(11, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbModelSwitchedMessageWriter.writeModelSwitched: failed to persist"
                            + " model-switched message for session '" + sessionId
                            + "' (from=" + fromModel + ", to=" + toModel + ", seq=" + seq + ")"
                            + ": " + e.getMessage(), e);
        }

        LOG.debug("DbModelSwitchedMessageWriter: persisted model-switched message for session '{}'"
                        + " (from={}, to={}, seq={})", sessionId, fromModel, toModel, seq);
    }
}
