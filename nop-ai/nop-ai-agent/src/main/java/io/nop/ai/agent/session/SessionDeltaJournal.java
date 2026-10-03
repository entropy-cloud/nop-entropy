package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.core.lang.json.JsonTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;

/**
 * Plan 2295 (WI14): codec for one incremental journal entry — the delta
 * between the base snapshot and one subsequent {@code save}. Storage-neutral:
 * the file store writes entries as JSONL lines, the DB store as
 * {@code ENTRY_JSON} CLOB rows; both share this codec and the replay rules.
 *
 * <p><b>Entry shape</b> (one JSON object):
 * <pre>
 * {
 *   "baseVersion": 3,                  // snapshotVersion this entry extends
 *   "seq": 5,                          // 0-based position in the journal
 *   "totalMessageCount": 42,           // message count at capture time (truncation guard)
 *   "appendedMessages": [ ... ],       // messages appended since the previous save (may be empty)
 *   "state": { ... }                   // full mutable scalar state at capture time (absolute semantics)
 * }
 * </pre>
 *
 * <p><b>Replay rules</b> (shared by both stores): entries apply in seq order
 * onto the base snapshot only while {@code baseVersion == snapshotVersion}
 * and {@code seq} is contiguous from 0. The first invalid, torn, or
 * structurally-illegal entry ends the replay and the store self-heals by
 * deleting the residual journal (file rows / table rows for that session) —
 * the base snapshot is self-sufficient, so this is a documented degradation,
 * not data loss. After each applied entry the session is truncated to the
 * entry's {@code totalMessageCount} (guards against same-reference shrinks,
 * which per-position fingerprints cannot see).
 */
public final class SessionDeltaJournal {

    static final String FIELD_BASE_VERSION = "baseVersion";
    static final String FIELD_SEQ = "seq";
    static final String FIELD_TOTAL_MESSAGE_COUNT = "totalMessageCount";
    static final String FIELD_APPENDED_MESSAGES = "appendedMessages";
    static final String FIELD_STATE = "state";

    private SessionDeltaJournal() {
    }

    /**
     * One decoded journal entry.
     */
    public static final class Entry {
        private final long baseVersion;
        private final long seq;
        private final int totalMessageCount;
        private final List<ChatMessage> appendedMessages;
        private final Map<String, Object> state;

        Entry(long baseVersion, long seq, int totalMessageCount,
              List<ChatMessage> appendedMessages, Map<String, Object> state) {
            this.baseVersion = baseVersion;
            this.seq = seq;
            this.totalMessageCount = totalMessageCount;
            this.appendedMessages = appendedMessages;
            this.state = state;
        }

        public long getBaseVersion() {
            return baseVersion;
        }

        public long getSeq() {
            return seq;
        }

        public int getTotalMessageCount() {
            return totalMessageCount;
        }

        public List<ChatMessage> getAppendedMessages() {
            return appendedMessages;
        }

        Map<String, Object> getState() {
            return state;
        }
    }

    /**
     * Encode one journal entry from the save delta.
     *
     * @param baseVersion       the snapshotVersion the entry extends
     * @param seq               the entry's 0-based position in the journal
     * @param totalMessageCount the session's total message count at capture time
     * @param appendedMessages  messages appended since the previous save; never null
     * @param stateSource       the session whose scalar state is captured
     * @return one JSON line (no trailing newline)
     */
    public static String encodeEntry(long baseVersion, long seq, int totalMessageCount,
                                     List<ChatMessage> appendedMessages, AgentSession stateSource) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(FIELD_BASE_VERSION, baseVersion);
        map.put(FIELD_SEQ, seq);
        map.put(FIELD_TOTAL_MESSAGE_COUNT, totalMessageCount);
        map.put(FIELD_APPENDED_MESSAGES, appendedMessages);
        map.put(FIELD_STATE, SessionFileWriter.serializeState(stateSource));
        try {
            return JsonTool.stringify(map);
        } catch (Exception e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "SessionDeltaJournal.encodeEntry: failed to serialize journal entry: " + e.getMessage());
        }
    }

    /**
     * Decode one journal entry line.
     *
     * @param line one JSON object line
     * @return the decoded entry
     * @throws NopAiAgentException if the line is unparseable or structurally
     *                             illegal (missing/non-numeric required fields) — callers treat
     *                             this as end-of-valid-journal and self-heal
     */
    public static Entry decodeEntry(String line) {
        Object parsed;
        try {
            parsed = JsonTool.parseNonStrict(line);
        } catch (Exception e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "SessionDeltaJournal.decodeEntry: unparseable journal line: " + e.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionDeltaJournal.decodeEntry: expected JSON object, got: "
                    + (parsed == null ? "null" : parsed.getClass().getName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;

        long baseVersion = requireLong(map, FIELD_BASE_VERSION);
        long seq = requireLong(map, FIELD_SEQ);
        int totalMessageCount = (int) requireLong(map, FIELD_TOTAL_MESSAGE_COUNT);

        List<ChatMessage> appended = SessionFileReader.readMessages(map.get(FIELD_APPENDED_MESSAGES));
        if (appended == null) {
            appended = new java.util.ArrayList<>();
        }

        Object stateRaw = map.get(FIELD_STATE);
        if (!(stateRaw instanceof Map)) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionDeltaJournal.decodeEntry: missing or illegal state block");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) stateRaw;

        return new Entry(baseVersion, seq, totalMessageCount, appended, state);
    }

    /**
     * Apply one validated entry onto the session: append the delta messages,
     * apply the scalar state block (absolute semantics), then truncate to the
     * captured total message count (same-reference shrink guard).
     *
     * @param session the replay target (base snapshot or previous replay state)
     * @param entry   the decoded entry; callers must have validated
     *                {@code baseVersion}/contiguous {@code seq} first
     */
    public static void applyTo(AgentSession session, Entry entry) {
        if (!entry.getAppendedMessages().isEmpty()) {
            session.appendMessages(entry.getAppendedMessages());
        }
        SessionFileReader.applyState(session, entry.getState());
        session.truncateMessages(entry.getTotalMessageCount());
    }

    private static long requireLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                // fall through to the error below
            }
        }
        throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "SessionDeltaJournal.decodeEntry: missing or illegal field '" + key + "'");
    }
}
