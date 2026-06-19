package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a per-session {@code session.json} file (written by
 * {@link SessionFileWriter}) back into an {@link AgentSession}.
 *
 * <p><b>Missing-file semantics</b> (declared in Javadoc): if the file does not
 * exist, {@link #readIfExists} returns {@code null} (legitimate — no session
 * has been persisted for this id yet; the caller treats null as "unknown
 * session"). This is not an error and not a silent skip — it is an explicit,
 * documented null return.
 *
 * <p><b>Corrupt-file semantics</b>: if the file exists but cannot be parsed
 * (malformed JSON, missing required fields, invalid enum name, etc.), the
 * reader fails fast with {@link NopAiAgentException} rather than silently
 * returning a partial session (Minimum Rules #24 No Silent No-Op). Corrupt
 * state must surface to the operator so the file can be repaired, not be
 * hidden behind a synthetic empty session.
 */
public final class SessionFileReader {

    private static final IGenericType MESSAGES_LIST_TYPE =
            JavaGenericTypeBuilder.buildListType(ChatMessage.class);

    /**
     * Read the session file if it exists.
     *
     * @param sessionFile the per-session session.json path; never null
     * @return the parsed session, or {@code null} if the file does not exist
     * @throws NopAiAgentException if the file exists but cannot be parsed
     */
    public AgentSession readIfExists(Path sessionFile) {
        if (sessionFile == null) {
            throw new NopAiAgentException("SessionFileReader.readIfExists: sessionFile must not be null");
        }
        if (!Files.exists(sessionFile)) {
            return null;
        }
        String json;
        try {
            json = Files.readString(sessionFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopAiAgentException(
                    "SessionFileReader: failed to read " + sessionFile + ": " + e.getMessage(), e);
        }
        return deserialize(json);
    }

    static AgentSession deserialize(String json) {
        Object parsed;
        try {
            parsed = JsonTool.parseNonStrict(json);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "SessionFileReader: failed to parse JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map)) {
            throw new NopAiAgentException(
                    "SessionFileReader: expected JSON object, got: "
                            + (parsed == null ? "null" : parsed.getClass().getName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;

        String sessionId = getRequiredString(map, SessionFileWriter.FIELD_SESSION_ID);
        String agentName = getRequiredString(map, SessionFileWriter.FIELD_AGENT_NAME);

        long createdAt = getLong(map, SessionFileWriter.FIELD_CREATED_AT);
        long updatedAt = getLong(map, SessionFileWriter.FIELD_UPDATED_AT);
        // Use the restore factory so the persisted createdAt/updatedAt
        // timestamps survive the round-trip (createdAt is final, set via the
        // private constructor; updatedAt is restored via the factory).
        AgentSession session = AgentSession.restore(sessionId, agentName,
                createdAt > 0 ? createdAt : System.currentTimeMillis(),
                updatedAt > 0 ? updatedAt : System.currentTimeMillis());

        // Messages use the polymorphic ChatMessage list type — JsonTool
        // dispatches each entry to the correct subclass via the "role"
        // discriminator (validated by TestFileBackedSessionStore).
        List<ChatMessage> messages = readMessages(map.get(SessionFileWriter.FIELD_MESSAGES));
        if (messages != null && !messages.isEmpty()) {
            session.appendMessages(messages);
            // appendMessages updates updatedAt to now; restore the persisted
            // value so the round-trip is faithful (messages were already
            // present when the session was persisted).
            session.restoreUpdatedAt(updatedAt > 0 ? updatedAt : session.getUpdatedAt());
        }

        // addTokensUsed / addIterations accumulate from 0 — set the persisted
        // totals directly rather than incrementally.
        long tokens = getLong(map, SessionFileWriter.FIELD_TOTAL_TOKENS_USED);
        if (tokens > 0) {
            session.addTokensUsed(tokens);
        }
        int iterations = getInt(map, SessionFileWriter.FIELD_TOTAL_ITERATIONS);
        if (iterations > 0) {
            session.addIterations(iterations);
        }

        String statusName = getString(map, SessionFileWriter.FIELD_STATUS);
        if (statusName != null && !statusName.isEmpty()) {
            session.setStatus(parseStatus(statusName));
        }

        Object metaRaw = map.get(SessionFileWriter.FIELD_METADATA);
        if (metaRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) metaRaw;
            session.setMetadata(new HashMap<>(meta));
        }

        String parentSessionId = getString(map, SessionFileWriter.FIELD_PARENT_SESSION_ID);
        if (parentSessionId != null) {
            session.setParentSessionId(parentSessionId);
        }

        String planId = getString(map, SessionFileWriter.FIELD_PLAN_ID);
        if (planId != null) {
            session.setPlanId(planId);
        }

        Long compactedAt = getLongOrNull(map, SessionFileWriter.FIELD_COMPACTED_AT);
        if (compactedAt != null) {
            session.setCompactedAt(compactedAt);
        }

        // Plan 270 finding 13-12: restore the tenantId so recovery paths
        // (resumeSession/restoreSession) can re-establish the tenant context
        // before tenant-scoped DB operations. Absent field → null (legacy /
        // single-tenant, backward compatible).
        String tenantId = getString(map, SessionFileWriter.FIELD_TENANT_ID);
        session.setTenantId(tenantId);

        return session;
    }

    private static List<ChatMessage> readMessages(Object raw) {
        if (raw == null) {
            return null;
        }
        // JsonTool.parseBeanFromText needs a JSON string, but raw is already a
        // parsed List<Map>. Use BeanTool via jsonObjectToBean to convert.
        try {
            Object bean = JsonTool.jsonObjectToBean(raw, MESSAGES_LIST_TYPE);
            @SuppressWarnings("unchecked")
            List<ChatMessage> list = (List<ChatMessage>) bean;
            return list;
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "SessionFileReader: failed to deserialize messages list: " + e.getMessage(), e);
        }
    }

    private static AgentExecStatus parseStatus(String name) {
        try {
            return AgentExecStatus.valueOf(name);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "SessionFileReader: invalid status name '" + name + "'");
        }
    }

    private static String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new NopAiAgentException(
                    "SessionFileReader: missing required field '" + key + "'");
        }
        return value.toString();
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(
                    "SessionFileReader: invalid int '" + value + "' for field '" + key + "'");
        }
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(
                    "SessionFileReader: invalid long '" + value + "' for field '" + key + "'");
        }
    }

    private static Long getLongOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new NopAiAgentException(
                    "SessionFileReader: invalid long '" + value + "' for field '" + key + "'");
        }
    }
}
