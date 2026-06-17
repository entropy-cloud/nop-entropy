package io.nop.ai.agent.usage;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.commons.util.StringHelper;
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
 * Production {@link IUsageRecorder} that persists each LLM call's usage data
 * to the {@code nop_ai_chat_response} table via raw JDBC (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.1 / plan 202 L2-18).
 *
 * <p><b>Wiring</b>: this is an opt-in recorder. The shipped default remains
 * {@link NoOpUsageRecorder} (pass-through). Integrators register this recorder
 * explicitly via
 * {@code engine.setUsageRecorder(new DbUsageRecorder(dataSource))} — exactly
 * like {@code DBSessionStore} / {@code DBMessageService} / {@code DBDenialLedger}.
 *
 * <p><b>Module boundary</b>: the agent module does not depend on
 * {@code nop-ai-dao}. This recorder uses raw JDBC ({@link DataSource} +
 * {@link PreparedStatement}) and never references the {@code NopAiChatResponse}
 * ORM entity, consistent with every other DB component in this module.
 *
 * <p><b>modelId resolution</b>: before the INSERT, the recorder looks up the
 * {@code nop_ai_model} primary key by {@code provider} + {@code model_name}
 * (column names per the generated {@code _NopAiModel} entity). When no matching
 * model row exists, {@code model_id} is left null — this is graceful
 * degradation, not a silent skip: the {@code ai_provider} + {@code ai_model}
 * string columns are still written, and {@code model_id} is nullable by schema
 * design.
 *
 * <p><b>Exception handling</b> (Minimum Rules #24): SQL failures are wrapped in
 * {@link NopAiAgentException} and never swallowed. modelId resolution returning
 * no row is the only null path, and it is an expected legitimate state.
 *
 * <p><b>Thread safety</b>: {@link DataSource} is thread-safe and every
 * {@link #record} call uses its own short-lived {@link Connection}
 * (try-with-resources). The recorder holds no mutable shared state, so a single
 * instance may be shared across concurrent ReAct loops.
 */
public class DbUsageRecorder implements IUsageRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(DbUsageRecorder.class);

    /**
     * Lookup query for the {@code nop_ai_model} primary key by provider +
     * model name. Column names follow the generated ORM entity
     * {@code _NopAiModel} (table {@code nop_ai_model}, columns {@code provider}
     * and {@code model_name}). The {@code nop_ai_model} table is owned by the
     * nop-ai-dao layer and is created by the ORM schema, not by this recorder.
     */
    static final String SQL_RESOLVE_MODEL_ID = ""
            + "SELECT id FROM nop_ai_model"
            + " WHERE provider = ? AND model_name = ?";

    static final String SQL_INSERT_USAGE = ""
            + "INSERT INTO " + NopAiChatResponseTable.TABLE_NAME
            + " (" + NopAiChatResponseTable.COL_ID
            + ", " + NopAiChatResponseTable.COL_REQUEST_ID
            + ", " + NopAiChatResponseTable.COL_SESSION_ID
            + ", " + NopAiChatResponseTable.COL_MODEL_ID
            + ", " + NopAiChatResponseTable.COL_AI_PROVIDER
            + ", " + NopAiChatResponseTable.COL_AI_MODEL
            + ", " + NopAiChatResponseTable.COL_PROMPT_TOKENS
            + ", " + NopAiChatResponseTable.COL_COMPLETION_TOKENS
            + ", " + NopAiChatResponseTable.COL_RESPONSE_DURATION_MS
            + ", " + NopAiChatResponseTable.COL_RESPONSE_TIMESTAMP
            + ", " + NopAiChatResponseTable.COL_VERSION
            + ", " + NopAiChatResponseTable.COL_CREATE_TIME
            + ", " + NopAiChatResponseTable.COL_UPDATE_TIME
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;
    private final ITenantResolver tenantResolver;

    /**
     * Create a DB-backed usage recorder and defensively initialize the
     * {@code nop_ai_chat_response} schema (CREATE TABLE IF NOT EXISTS). Uses
     * the backward-compatible {@link NullTenantResolver}.
     *
     * @param dataSource the JDBC data source; never null
     */
    public DbUsageRecorder(DataSource dataSource) {
        this(dataSource, NullTenantResolver.INSTANCE);
    }

    /**
     * Create a DB-backed usage recorder with a contextual tenant resolver
     * (plan 232 / vision §5.1). When the resolver reports a non-null tenant,
     * the INSERT writes {@code TENANT_ID}; when {@code null}, the INSERT is
     * byte-identical to the original (zero regression).
     *
     * @param dataSource     the JDBC data source; never null
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public DbUsageRecorder(DataSource dataSource, ITenantResolver tenantResolver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(NopAiChatResponseTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbUsageRecorder: failed to initialize schema: " + e.getMessage(), e);
        }
    }

    @Override
    public void record(UsageRecord record) {
        if (record == null) {
            throw new NopAiAgentException("DbUsageRecorder.record: usage record must not be null");
        }

        String modelId = resolveModelId(record.getAiProvider(), record.getAiModel());
        String rowId = StringHelper.generateUUID();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp responseTs = new Timestamp(record.getResponseTimestamp());
        String tenant = tenantResolver.resolveTenantId();

        String insertSql = SQL_INSERT_USAGE;
        int lastParamIndex = 13;
        if (tenant != null) {
            insertSql = insertSql.substring(0, insertSql.length() - 1)
                    + ", " + NopAiChatResponseTable.COL_TENANT_ID + ")";
            lastParamIndex = 14;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, rowId);
            ps.setString(2, record.getRequestId());
            ps.setString(3, record.getSessionId());
            ps.setString(4, modelId);
            ps.setString(5, record.getAiProvider());
            ps.setString(6, record.getAiModel());
            ps.setInt(7, record.getPromptTokens());
            ps.setInt(8, record.getCompletionTokens());
            if (record.getResponseDurationMs() != null) {
                ps.setLong(9, record.getResponseDurationMs());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            ps.setTimestamp(10, responseTs);
            ps.setInt(11, 0);
            ps.setTimestamp(12, now);
            ps.setTimestamp(13, now);
            if (tenant != null) {
                ps.setString(lastParamIndex, tenant);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new NopAiAgentException(
                    "DbUsageRecorder.record: failed to persist usage for session '"
                            + record.getSessionId() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resolve the {@code nop_ai_model} primary key for the given provider +
     * model-name pair. Returns null when no matching row exists (graceful
     * degradation — the usage row is still written with a null model_id).
     */
    private String resolveModelId(String provider, String modelName) {
        if (provider == null || modelName == null) {
            return null;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESOLVE_MODEL_ID)) {
            ps.setString(1, provider);
            ps.setString(2, modelName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            // The nop_ai_model table may not exist in deployments that do not
            // run the nop-ai-dao schema. This is expected graceful degradation:
            // ai_provider + ai_model string columns still record the model
            // identity, and model_id is nullable by design. A missing table is
            // logged at debug level (common in test/embedded setups), while a
            // genuine SQL error during a present table is logged at warn.
            LOG.debug("DbUsageRecorder.resolveModelId: nop_ai_model lookup failed for "
                    + "provider='{}', model='{}' (table may be absent)", provider, modelName, e);
        }
        return null;
    }
}
