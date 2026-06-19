package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.core.lang.json.JsonTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes an {@link AgentSession} to a per-session {@code session.json}
 * file (design §1.1 recovery model + plan 183 crash/restart restore protocol).
 * The session file is the source of truth for crash/restart recovery: the full
 * message history ({@code List<ChatMessage>} including tool-call / tool-result
 * messages) is persisted so the restore path can rebuild the
 * {@code AgentExecutionContext} without re-executing completed tools.
 *
 * <p><b>Full-rewrite semantics</b>: the session file is overwritten on each
 * write — it always represents the latest aggregate session state. Unlike the
 * checkpoint journal (append-only, derived from per-tool-execution events),
 * the session is a mutable aggregate state; the per-session file is small, so
 * full-rewrite is simple and consistent.
 *
 * <p><b>Polymorphic message serialization</b>: the {@link ChatMessage} base
 * class is annotated with Jackson {@code @JsonTypeInfo(property="role")} +
 * {@code @JsonSubTypes}. {@link JsonTool#stringify} and
 * {@link JsonTool#parseBeanFromText} honour these annotations and dispatch
 * each message map to the correct subclass on read-back, so user/assistant/
 * system/tool/custom roles round-trip correctly (validated by
 * {@code TestFileBackedSessionStore}).
 *
 * <p><b>Thread safety</b>: writes are serialized on a per-instance lock.
 */
public final class SessionFileWriter {

    static final String FIELD_SESSION_ID = "sessionId";
    static final String FIELD_AGENT_NAME = "agentName";
    static final String FIELD_MESSAGES = "messages";
    static final String FIELD_TOTAL_TOKENS_USED = "totalTokensUsed";
    static final String FIELD_TOTAL_ITERATIONS = "totalIterations";
    static final String FIELD_CREATED_AT = "createdAt";
    static final String FIELD_UPDATED_AT = "updatedAt";
    static final String FIELD_STATUS = "status";
    static final String FIELD_METADATA = "metadata";
    static final String FIELD_PARENT_SESSION_ID = "parentSessionId";
    static final String FIELD_PLAN_ID = "planId";
    static final String FIELD_COMPACTED_AT = "compactedAt";
    static final String FIELD_TENANT_ID = "tenantId";

    private final Object ioLock = new Object();

    /**
     * Write (or overwrite) the session file.
     *
     * @param sessionFile the per-session session.json path; never null
     * @param session     the session to serialize; never null
     * @throws NopAiAgentException if serialization or I/O fails
     */
    public void write(Path sessionFile, AgentSession session) {
        if (sessionFile == null) {
            throw new NopAiAgentException("SessionFileWriter.write: sessionFile must not be null");
        }
        if (session == null) {
            throw new NopAiAgentException("SessionFileWriter.write: session must not be null");
        }

        String json = serialize(session);

        synchronized (ioLock) {
            // Crash-safe write (plan 195): write to a sibling .tmp file, then
            // atomically replace the target via Files.move(ATOMIC_MOVE,
            // REPLACE_EXISTING). POSIX rename(2) guarantees the target file
            // is at all times either the complete previous content or the
            // complete new content — never truncated or partially written.
            // The .tmp file is a sibling of the target so both reside on the
            // same filesystem/mount point (required for ATOMIC_MOVE).
            Path tmp = sessionFile.resolveSibling(sessionFile.getFileName() + ".tmp");
            try {
                Path parent = sessionFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(tmp, json.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(tmp, sessionFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new NopAiAgentException(
                        "SessionFileWriter.write: failed to write " + sessionFile
                                + ": " + e.getMessage(), e);
            } finally {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException deleteErr) {
                    // Best-effort cleanup of a stale tmp — must not mask the
                    // primary exception (already thrown above if write/move
                    // failed). Only logged.
                }
            }
        }
    }

    static String serialize(AgentSession session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(FIELD_SESSION_ID, session.getSessionId());
        map.put(FIELD_AGENT_NAME, session.getAgentName());
        // messages serialize via ChatMessage polymorphism — each map carries
        // a "role" discriminator that the reader uses to pick the subclass.
        map.put(FIELD_MESSAGES, session.getMessages());
        map.put(FIELD_TOTAL_TOKENS_USED, session.getTotalTokensUsed());
        map.put(FIELD_TOTAL_ITERATIONS, session.getTotalIterations());
        map.put(FIELD_CREATED_AT, session.getCreatedAt());
        map.put(FIELD_UPDATED_AT, session.getUpdatedAt());
        map.put(FIELD_STATUS, session.getStatus() != null ? session.getStatus().name() : null);
        map.put(FIELD_METADATA, session.getMetadata());
        map.put(FIELD_PARENT_SESSION_ID, session.getParentSessionId());
        map.put(FIELD_PLAN_ID, session.getPlanId());
        map.put(FIELD_COMPACTED_AT, session.getCompactedAt());
        map.put(FIELD_TENANT_ID, session.getTenantId());
        try {
            return JsonTool.stringify(map);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "SessionFileWriter.serialize: failed to serialize session: " + e.getMessage(), e);
        }
    }
}
