package io.nop.ai.agent.session;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.api.core.time.CoreMetrics;
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

/** * <p><b>store-layer 边界（审计 AI-2/AI-19 裁定）</b>：自建表/本地文件为引擎内部运行时状态，
 * 保留独立 store 层（不注册 ORM）；租户/软删不适用理由与表清单见
 * {@code ai-dev/design/nop-ai-agent/store-layer-contract.md}。
 */
public final class SessionFileReader {

    private static final IGenericType MESSAGES_LIST_TYPE =
            JavaGenericTypeBuilder.buildListType(ChatMessage.class);

    /**
     * Plan 2295 (WI14): a parsed base snapshot plus the {@code snapshotVersion}
     * it was written with. Journal entries validate their {@code baseVersion}
     * against this value on replay.
     */
    public static final class PersistedSnapshot {
        private final AgentSession session;
        private final long snapshotVersion;

        PersistedSnapshot(AgentSession session, long snapshotVersion) {
            this.session = session;
            this.snapshotVersion = snapshotVersion;
        }

        public AgentSession getSession() {
            return session;
        }

        public long getSnapshotVersion() {
            return snapshotVersion;
        }
    }

    /**
     * Read the session file if it exists (snapshot version 0 convention —
     * legacy callers that ignore the version).
     *
     * @param sessionFile the per-session session.json path; never null
     * @return the parsed session, or {@code null} if the file does not exist
     * @throws NopAiAgentException if the file exists but cannot be parsed
     */
    public AgentSession readIfExists(Path sessionFile) {
        PersistedSnapshot snapshot = readSnapshotIfExists(sessionFile);
        return snapshot == null ? null : snapshot.getSession();
    }

    /**
     * Read the session file if it exists, exposing the stamped
     * {@code snapshotVersion} (plan 2295 / WI14). Absent field in legacy
     * files → version 0.
     *
     * @param sessionFile the per-session session.json path; never null
     * @return the parsed snapshot, or {@code null} if the file does not exist
     * @throws NopAiAgentException if the file exists but cannot be parsed
     */
    public PersistedSnapshot readSnapshotIfExists(Path sessionFile) {
        if (sessionFile == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader.readSnapshotIfExists: sessionFile must not be null");
        }
        if (!Files.exists(sessionFile)) {
            return null;
        }
        String json;
        try {
            json = Files.readString(sessionFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "SessionFileReader: failed to read " + sessionFile + ": " + e.getMessage());
        }
        return deserializeSnapshot(json);
    }

    static AgentSession deserialize(String json) {
        return deserializeSnapshot(json).getSession();
    }

    static PersistedSnapshot deserializeSnapshot(String json) {
        Object parsed;
        try {
            parsed = JsonTool.parseNonStrict(json);
        } catch (Exception e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "SessionFileReader: failed to parse JSON: " + e.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader: expected JSON object, got: "
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
                createdAt > 0 ? createdAt : CoreMetrics.currentTimeMillis(),
                updatedAt > 0 ? updatedAt : CoreMetrics.currentTimeMillis());

        // Messages use the polymorphic ChatMessage list type — JsonTool
        // dispatches each entry to the correct subclass via the "role"
        // discriminator (validated by TestFileBackedSessionStore).
        List<ChatMessage> messages = readMessages(map.get(SessionFileWriter.FIELD_MESSAGES));
        if (messages != null && !messages.isEmpty()) {
            session.appendMessages(messages);
        }

        // Mutable scalar state (tokens/iterations/status/metadata/... incl.
        // updatedAt, applied last so it survives appendMessages' touch).
        applyState(session, map);

        long snapshotVersion = getLong(map, SessionFileWriter.FIELD_SNAPSHOT_VERSION);
        return new PersistedSnapshot(session, snapshotVersion);
    }

    /**
     * Plan 2295 (WI14): apply the mutable scalar state block onto the session
     * with absolute (last-writer-wins) semantics. Shared by the snapshot
     * deserialize path and the journal-entry replay path so both persistence
     * shapes apply identical field semantics. Callers must invoke this AFTER
     * message application — {@code updatedAt} is restored last so it survives
     * {@link AgentSession#appendMessages}'s touch.
     */
    static void applyState(AgentSession session, Map<String, Object> map) {
        // Absolute setters (plan 2295 / WI14): the state block carries the
        // whole scalar state at capture time — accumulators would re-add on
        // top of already-applied values during journal replay.
        long tokens = getLong(map, SessionFileWriter.FIELD_TOTAL_TOKENS_USED);
        session.restoreTotalTokensUsed(tokens);
        int iterations = getInt(map, SessionFileWriter.FIELD_TOTAL_ITERATIONS);
        session.restoreTotalIterations(iterations);

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

        // (resumeSession/restoreSession) can re-establish the tenant context
        // before tenant-scoped DB operations. Absent field → null (legacy /
        // single-tenant, backward compatible).
        String tenantId = getString(map, SessionFileWriter.FIELD_TENANT_ID);
        session.setTenantId(tenantId);

        // Plan 2288 (WI7): seen-input dedup keys — absent in legacy session
        // files → empty map (backward compatible)
        Object seenRaw = map.get(SessionFileWriter.FIELD_SEEN_INPUT_KEYS);
        if (seenRaw instanceof Map) {
            java.util.Map<String, Long> seen = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) seenRaw).entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof Number) {
                    seen.put(entry.getKey().toString(), ((Number) entry.getValue()).longValue());
                }
            }
            session.setSeenInputKeys(seen);
        }

        // Plan 2289 (WI8): offline injection staging — absent in legacy files.
        // Absolute semantics: clear-then-add (the staging list has no bulk
        // setter), so a replayed entry replaces stale staged entries.
        Object pendingRaw = map.get(SessionFileWriter.FIELD_PENDING_INJECTIONS);
        session.clearPendingInjections();
        if (pendingRaw instanceof java.util.List) {
            for (Object o : (java.util.List<?>) pendingRaw) {
                if (o instanceof java.util.Map) {
                    Object sem = ((java.util.Map<?, ?>) o).get("semantics");
                    Object mj = ((java.util.Map<?, ?>) o).get("messageJson");
                    if (sem != null && mj != null) {
                        session.addPendingInjection(sem.toString(), mj.toString());
                    }
                }
            }
        }

        // Applied last so the persisted updatedAt survives appendMessages'
        // touch (both in deserialize and in journal replay).
        long updatedAt = getLong(map, SessionFileWriter.FIELD_UPDATED_AT);
        if (updatedAt > 0) {
            session.restoreUpdatedAt(updatedAt);
        }
    }

    // package-private: shared with the journal entry decoder (plan 2295/WI14)
    static List<ChatMessage> readMessages(Object raw) {
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
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "SessionFileReader: failed to deserialize messages list: " + e.getMessage());
        }
    }

    private static AgentExecStatus parseStatus(String name) {
        try {
            return AgentExecStatus.valueOf(name);
        } catch (Exception e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader: invalid status name '" + name + "'");
        }
    }

    private static String getRequiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader: missing required field '" + key + "'");
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
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader: invalid int '" + value + "' for field '" + key + "'");
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
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader: invalid long '" + value + "' for field '" + key + "'");
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
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionFileReader: invalid long '" + value + "' for field '" + key + "'");
        }
    }
}
