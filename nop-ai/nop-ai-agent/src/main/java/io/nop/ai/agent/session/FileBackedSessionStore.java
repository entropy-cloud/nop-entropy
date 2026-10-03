package io.nop.ai.agent.session;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.SessionIds;
import io.nop.ai.api.chat.messages.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-backed {@link ISessionStore} — the drop-in persistent sibling of
 * {@link InMemorySessionStore}. Implements the same contract
 * ({@code getOrCreate}/{@code get}/{@code remove}/{@code getAll} +
 * {@code forkSession}) with the storage backend changed from a
 * {@code ConcurrentHashMap} to per-session {@code session.json} files.
 *
 * <p><b>Drop-in replacement</b>: the engine / executor dispatch-path wiring
 * is unchanged — {@code sessionStore.save(session)} is called at the same
 * points in the ReAct dispatch loop (after {@code saveCheckpoint}) and at
 * the end of {@code doExecute}/{@code resumeSession}/{@code restoreSession}.
 * Selecting {@code FileBackedSessionStore} instead of
 * {@link InMemorySessionStore} only changes whether the session state
 * survives a process restart. The {@link InMemorySessionStore} default
 * remains the shipped default; this store is registered explicitly when
 * crash/restart durability is needed.
 *
 * <p><b>File layout</b>: per-session isolation via subdirectories:
 * <pre>
 *   {rootDirectory}/
 *     {sessionId}/
 *       session.json       (full session state, overwrite-on-write)
 * </pre>
 *
 * <p><b>Crash-survival semantics</b>: after a process restart, a new
 * {@code FileBackedSessionStore} instance pointed at the same root directory
 * reloads session state from {@code session.json} on first access
 * ({@code get} cache-miss triggers {@link #loadSessionFile}). The full
 * message history (including tool-call / tool-result messages) is restored,
 * so the restore path can rebuild the {@code AgentExecutionContext} without
 * re-executing completed tools. The checkpoint journal
 * ({@code FileBackedCheckpointManager}) provides resume-point metadata and
 * consistency verification on top of this session store, but the session
 * store — not the journal — is the message-history source of truth.
 *
 * <p><b>Cache</b>: an in-memory {@code ConcurrentHashMap} mirrors the on-disk
 * state for read performance. {@code save} writes through to disk and updates
 * the cache atomically. {@code remove} deletes both the file and the cache
 * entry. {@code get} cache-miss triggers a lazy file load.
 *
 * <p><b>Thread safety</b>: the cache is a {@code ConcurrentHashMap}. File
 * writes are serialized by {@link SessionFileWriter}'s internal lock.
 *
 * <p><b>Retention / incremental write path (plan 2295, WI14)</b>: the base
 * {@code session.json} is rewritten only on fold (first save, structural
 * change, in-place content mutation, or after {@link SessionPersistenceState}
 * journal threshold). Between folds each save appends one entry to the
 * sidecar {@code session.journal.jsonl} (delta messages + scalar state
 * block), so disk writes are O(delta) instead of O(session). The snapshot
 * remains the authoritative state; the journal is a bounded optimization
 * replayed through a version check ({@code baseVersion}/{@code seq}) that
 * self-heals stale journals away.
 *
 * <p><b>Fail-closed sessionId validation</b> (P0 path-traversal fix,
 * finding [13-15]): every caller-supplied {@code sessionId} that reaches a
 * filesystem {@code Path.resolve} is validated by
 * {@link SessionIds#requireContainedPath} — only {@code [A-Za-z0-9_-]} ids
 * are accepted and the resolved path must stay inside {@code rootDirectory}.
 * Any invalid id throws {@link NopAiAgentException}; there is no silent
 * sanitization or fall-back. This also guards {@code resumeSession}/
 * {@code restoreSession}/{@code cancelSession} paths, which bypass the
 * engine-level identifier check and reach this store via {@code get(sessionId)}.
 */
public class FileBackedSessionStore implements ISessionStore {

    private static final Logger LOG = LoggerFactory.getLogger(FileBackedSessionStore.class);

    static final String SESSION_FILE_NAME = "session.json";

    /**
     * Plan 2295 (WI14): append-only sidecar holding the journal entries
     * (one JSON object per line) accumulated since the last fold. Deleted on
     * fold and on replay-validated self-heal.
     */
    static final String JOURNAL_FILE_NAME = "session.journal.jsonl";

    static final String PROPS_KEY_AGENT_NAME = "agentName";

    private final Path rootDirectory;
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
    /**
     * Plan 2295 (WI14): persisted-state bookkeeping per session — independent
     * of {@link #sessions} (the cache holds live objects; this tracks what is
     * on disk). Guarded by the per-session lock from {@link #sessionLocks}.
     */
    private final ConcurrentHashMap<String, SessionPersistenceState> persistedStates = new ConcurrentHashMap<>();
    /**
     * Plan 2295 (WI14): per-session locks serializing the whole
     * diff→write→state-update save sequence. The engine has real concurrent
     * writers per session (injectInput/steering on the caller thread vs the
     * ReAct worker thread); full-rewrite used to converge them into
     * "whole-file either/or" via the writer's ioLock + atomic replace — the
     * journal needs explicit serialization to stay line-consistent. Lock
     * order: sessionLock → SessionFileWriter.ioLock (one-way, no deadlock).
     */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final SessionFileWriter writer = new SessionFileWriter();
    private final SessionFileReader reader = new SessionFileReader();

    /**
     * Create a file-backed session store.
     *
     * @param rootDirectory the session root directory; per-session
     *                      subdirectories are created under it; never null
     */
    public FileBackedSessionStore(Path rootDirectory) {
        if (rootDirectory == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "FileBackedSessionStore: rootDirectory must not be null");
        }
        this.rootDirectory = rootDirectory;
    }

    /**
     * @return the root directory under which per-session files live
     */
    public Path getRootDirectory() {
        return rootDirectory;
    }

    @Override
    public AgentSession getOrCreate(String sessionId, String agentName) {
        AgentSession existing = sessions.get(sessionId);
        if (existing != null) {
            return existing;
        }
        AgentSession loaded = loadPersisted(sessionId);
        if (loaded != null) {
            AgentSession raced = putIfAbsent(sessionId, loaded);
            return raced != null ? raced : loaded;
        }
        AgentSession fresh = AgentSession.create(sessionId, agentName);
        AgentSession raced = putIfAbsent(sessionId, fresh);
        return raced != null ? raced : fresh;
    }

    @Override
    public AgentSession get(String sessionId) {
        AgentSession cached = sessions.get(sessionId);
        if (cached != null) {
            return cached;
        }
        AgentSession loaded = loadPersisted(sessionId);
        if (loaded != null) {
            AgentSession raced = putIfAbsent(sessionId, loaded);
            return raced != null ? raced : loaded;
        }
        return null;
    }

    @Override
    public void remove(String sessionId) {
        Object lock = sessionLock(sessionId);
        synchronized (lock) {
            sessions.remove(sessionId);
            persistedStates.remove(sessionId);
            sessionLocks.remove(sessionId);
            Path sessionFile = sessionFilePath(sessionId);
            try {
                // Journal first (plan 2295/WI14): the sidecar must not outlive
                // the snapshot, or a later directory reuse could replay stale
                // entries (version check would discard them, but cleanliness
                // beats relying on the guard).
                Files.deleteIfExists(journalFilePath(sessionId));
                Files.deleteIfExists(sessionFile);
                // Best-effort cleanup of the now-empty per-session directory.
                Path sessionDir = sessionDirPath(sessionId);
                try (Stream<Path> entries = Files.list(sessionDir)) {
                    if (!entries.findAny().isPresent()) {
                        Files.delete(sessionDir);
                    }
                }
            } catch (IOException e) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "FileBackedSessionStore.remove: failed to delete " + sessionFile
                                + ": " + e.getMessage());
            }
        }
    }

    @Override
    public Collection<AgentSession> getAll() {
        return sessions.values();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Full disk discovery (plan 184 auto-restore-on-startup contract)</b>:
     * scans every subdirectory under {@link #getRootDirectory()} — each
     * subdirectory name is a sessionId and contains a {@code session.json}.
     * Successfully loaded sessions are stored in the in-memory cache so
     * subsequent {@link #get} calls hit the cache (consistent with the
     * cache-on-load behaviour of {@link #get}).
     *
     * <p><b>Crash-survival semantics</b>: this is the method that lets a
     * fresh {@code FileBackedSessionStore} instance (e.g. created after a
     * process restart) discover sessions persisted by a previous instance,
     * without any caller having to know a session id ahead of time. The
     * engine's {@code restorePendingSessions} orchestrator calls this to
     * enumerate restore candidates.
     *
     * <p><b>Corruption isolation</b>: a corrupt or truncated
     * {@code session.json} (unparseable JSON, missing required fields, etc.)
     * is skipped and logged as a warning, so that one corrupt file does not
     * block discovery of the remaining sessions (Minimum Rules #24: the
     * corruption is surfaced via the warning log, not silently swallowed —
     * a subsequent {@link #get} on the same sessionId will still fail fast
     * with {@link NopAiAgentException} for the operator). A subdirectory
     * that does not contain a {@code session.json} is likewise skipped.
     *
     * <p><b>Empty / missing root</b>: if {@code rootDirectory} does not
     * exist or contains no session subdirectories, returns an empty
     * collection (a legitimate "no persisted sessions" state, not an error).
     *
     * @return all sessions persisted on disk (including any already in the
     *         cache); never null, possibly empty
     */
    @Override
    public Collection<AgentSession> listAllSessions() {
        Collection<AgentSession> discovered = new ArrayList<>();
        if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
            return discovered;
        }
        try (Stream<Path> entries = Files.list(rootDirectory)) {
            entries.filter(Files::isDirectory).forEach(sessionDir -> {
                String sessionId = sessionDir.getFileName().toString();
                Path sessionFile = sessionDir.resolve(SESSION_FILE_NAME);
                if (!Files.exists(sessionFile)) {
                    // Subdirectory without a session.json — not a session dir;
                    // skip silently (legitimate, e.g. a stray dir).
                    return;
                }
                // Prefer the cache when already loaded (avoid re-reading the
                // file for sessions the engine has already touched).
                AgentSession cached = sessions.get(sessionId);
                if (cached != null) {
                    discovered.add(cached);
                    return;
                }
                try {
                    // Unified load path (plan 2295/WI14): snapshot + journal
                    // replay — discovery must not return a journal-missing
                    // tail that a later get() would then treat as authoritative.
                    AgentSession loaded = loadPersisted(sessionId);
                    if (loaded != null) {
                        AgentSession raced = sessions.putIfAbsent(sessionId, loaded);
                        discovered.add(raced != null ? raced : loaded);
                    }
                } catch (NopAiAgentException e) {
                    // Corrupt / truncated session.json — surface via warning
                    // and continue discovery of the remaining sessions. A
                    // later get(sessionId) will still fail fast for this id.
                    LOG.warn("FileBackedSessionStore.listAllSessions: skipping unreadable "
                                    + "session file (corrupt or truncated JSON): file={}",
                            sessionDir.resolve(SESSION_FILE_NAME), e);
                }
            });
        } catch (IOException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "FileBackedSessionStore.listAllSessions: failed to list root directory "
                            + rootDirectory + ": " + e.getMessage());
        }
        return discovered;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Incremental write path (plan 2295 / WI14)</b>: the save first tries
     * the append fast path — the incoming message list must fingerprint-match
     * the persisted prefix per position (catches guardrail in-place rewrites
     * of persisted messages) and the journal must be below the fold threshold.
     * A matching save appends one journal entry (delta messages + scalar
     * state block) instead of rewriting the snapshot. Every non-append case
     * (first save, structural change, content mutation, threshold) folds:
     * full snapshot rewrite with a bumped {@code snapshotVersion}, then the
     * journal is cleared. Fold ordering (snapshot first, journal second) is
     * crash-safe: an orphaned journal fails the replay version check and is
     * self-healed away on the next load.
     */
    @Override
    public void save(AgentSession session) {
        if (session == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "FileBackedSessionStore.save: session must not be null");
        }
        String sessionId = session.getSessionId();
        Object lock = sessionLock(sessionId);
        synchronized (lock) {
            SessionPersistenceState state = persistedStates.get(sessionId);
            List<ChatMessage> current = session.getMessages();

            if (state != null && state.canAppend() && tryAppend(state, session, current)) {
                sessions.put(sessionId, session);
                return;
            }

            fold(state, session, current);
            sessions.put(sessionId, session);
        }
    }

    /**
     * Append fast path: verify the persisted prefix still matches by content
     * fingerprint, then append one journal entry with the delta messages and
     * the current scalar state. Returns false when the caller must fold.
     */
    private boolean tryAppend(SessionPersistenceState state, AgentSession session, List<ChatMessage> current) {
        int persistedCount = state.prefixFingerprints.size();
        if (current.size() < persistedCount) {
            return false; // shrink → fold
        }
        Path sessionFile = sessionFilePath(session.getSessionId());
        if (!Files.exists(sessionFile)) {
            return false; // snapshot externally deleted → rebuild base first
        }
        // O(prefix) fingerprint recompute — the deliberate cost of the append
        // fast path (WI14 trades I/O, not CPU, for determinism).
        for (int i = 0; i < persistedCount; i++) {
            if (ChatMessageFingerprints.fingerprint(current.get(i)) != state.prefixFingerprints.get(i)) {
                return false; // in-place content mutation → fold
            }
        }
        List<ChatMessage> appended =
                current.subList(persistedCount, current.size());

        String line = SessionDeltaJournal.encodeEntry(state.snapshotVersion, state.journalEntryCount,
                current.size(), appended, session);
        try {
            Path journalFile = journalFilePath(session.getSessionId());
            Files.createDirectories(journalFile.getParent());
            Files.write(journalFile, (line + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "FileBackedSessionStore.tryAppend: failed to append journal for session '"
                    + session.getSessionId() + "': " + e.getMessage());
        }
        state.journalEntryCount++;
        // Register the delta as persisted (fingerprints for new tail messages).
        for (ChatMessage message : appended) {
            state.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
        }
        return true;
    }

    /**
     * Fold: full snapshot rewrite (version + 1) then journal clear. Crash
     * between the two steps leaves an orphaned journal that the replay
     * version check discards and self-heals (see {@link #replayJournal}).
     */
    private void fold(SessionPersistenceState state, AgentSession session, List<ChatMessage> current) {
        long newVersion = (state != null ? state.snapshotVersion : 0L) + 1L;
        Path sessionFile = sessionFilePath(session.getSessionId());
        writer.write(sessionFile, session, newVersion);
        try {
            Files.deleteIfExists(journalFilePath(session.getSessionId()));
        } catch (IOException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "FileBackedSessionStore.fold: failed to clear journal for session '"
                    + session.getSessionId() + "': " + e.getMessage());
        }
        SessionPersistenceState fresh = state != null ? state : new SessionPersistenceState();
        fresh.resetAfterFold(newVersion, current);
        persistedStates.put(session.getSessionId(), fresh);
    }

    /**
     * Unified load path: base snapshot + journal replay (also registers the
     * persisted state so the next save can append). Shared by
     * {@code getOrCreate}/{@code get}/{@code listAllSessions} — discovery
     * must return the same journal-extended state a later get() would.
     */
    private AgentSession loadPersisted(String sessionId) {
        Path sessionFile = sessionFilePath(sessionId);
        SessionFileReader.PersistedSnapshot snapshot = reader.readSnapshotIfExists(sessionFile);
        if (snapshot == null) {
            return null;
        }
        AgentSession session = snapshot.getSession();
        List<ChatMessage> persisted = session.getMessages();

        SessionPersistenceState state = new SessionPersistenceState();
        ReplayOutcome replay = replayJournal(session, sessionId, snapshot.getSnapshotVersion());
        if (replay.healed) {
            // Self-heal = fold the replayed state (plan 2295/WI14): the journal
            // entries applied in memory were just deleted from disk with the
            // heal, so the replayed state must be re-persisted as the new base
            // to restore the "disk == replayed state + empty journal"
            // invariant. A plain bookkeeping reset would leave the next append
            // believing the healed entries are still persisted.
            synchronized (sessionLock(sessionId)) {
                long healedVersion = snapshot.getSnapshotVersion() + 1L;
                writer.write(sessionFile, session, healedVersion);
                state.snapshotVersion = healedVersion;
                state.journalEntryCount = 0;
                persisted = session.getMessages();
                for (ChatMessage message : persisted) {
                    state.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
                }
                persistedStates.put(sessionId, state);
            }
        } else {
            state.snapshotVersion = snapshot.getSnapshotVersion();
            state.journalEntryCount = replay.applied;
            for (ChatMessage message : persisted) {
                state.prefixFingerprints.add(ChatMessageFingerprints.fingerprint(message));
            }
            persistedStates.put(sessionId, state);
        }
        return session;
    }

    /**
     * Replay journal entries onto the snapshot: contiguous {@code seq} from 0
     * with {@code baseVersion == snapshotVersion}, stopping at the first torn
     * or invalid entry — which is then self-healed by deleting the residual
     * journal (Ruling: fold-crash orphaned entries sit at the journal head, so
     * version-check-only would keep shadowing every future append until the
     * next fold; deleting resets accumulation to a clean baseline). The base
     * snapshot is self-sufficient, so the discarded entries represent no state
     * loss beyond what a crash already lost.
     *
     * @return applied count + heal flag (0 applied when absent or healed away)
     */
    private ReplayOutcome replayJournal(AgentSession snapshot, String sessionId, long snapshotVersion) {
        ReplayOutcome outcome = new ReplayOutcome();
        Path journalFile = journalFilePath(sessionId);
        if (!Files.exists(journalFile)) {
            return outcome;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(journalFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "FileBackedSessionStore.replayJournal: failed to read journal for session '"
                    + sessionId + "': " + e.getMessage());
        }
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            SessionDeltaJournal.Entry entry;
            try {
                entry = SessionDeltaJournal.decodeEntry(line);
            } catch (NopAiAgentException e) {
                // Torn tail (partial line) or structurally illegal entry.
                LOG.warn("FileBackedSessionStore.replayJournal: discarding journal from invalid entry on "
                        + "(session={}, applied={})", sessionId, outcome.applied, e);
                outcome.healed = true;
                break;
            }
            if (entry.getBaseVersion() != snapshotVersion || entry.getSeq() != outcome.applied) {
                // Stale journal (orphaned by a fold crash) or a seq gap.
                LOG.warn("FileBackedSessionStore.replayJournal: stale journal for session={} "
                                + "(entryBaseVersion={}, snapshotVersion={}, expectedSeq={}, gotSeq={}) — clearing",
                        sessionId, entry.getBaseVersion(), snapshotVersion, outcome.applied, entry.getSeq());
                outcome.healed = true;
                break;
            }
            SessionDeltaJournal.applyTo(snapshot, entry);
            outcome.applied++;
        }
        if (outcome.healed) {
            try {
                Files.deleteIfExists(journalFile);
            } catch (IOException e) {
                // The stale journal stays on disk; the version check above
                // keeps discarding it on every load. Surfaced, not silent.
                LOG.warn("FileBackedSessionStore.replayJournal: failed to delete stale journal for session={}", sessionId, e);
            }
        }
        return outcome;
    }

    /** Replay result: applied entry count plus whether a self-heal cleared the journal. */
    private static final class ReplayOutcome {
        int applied;
        boolean healed;
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        return forkSession(parentSessionId, inheritContext, props, null);
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props,
                              Predicate<ChatMessage> messageFilter) {
        AgentSession parent = get(parentSessionId);
        if (parent == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "forkSession failed: parent session not found: parentSessionId=" + parentSessionId);
        }

        String childAgentName = resolveChildAgentName(parent, props);
        String childSessionId = UUID.randomUUID().toString();
        AgentSession child = AgentSession.create(childSessionId, childAgentName);

        if (inheritContext) {
            child.appendMessages(filterMessages(parent.getMessages(), messageFilter));
            child.setPlanId(parent.getPlanId());
            child.setMetadata(parent.getMetadata());
        }

        mergeProps(child, props);

        child.setParentSessionId(parentSessionId);
        save(child);

        return childSessionId;
    }

    private static List<ChatMessage> filterMessages(List<ChatMessage> messages, Predicate<ChatMessage> filter) {
        if (filter == null) {
            return messages;
        }
        return messages.stream().filter(filter).collect(Collectors.toList());
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private AgentSession putIfAbsent(String sessionId, AgentSession session) {
        AgentSession existing = sessions.putIfAbsent(sessionId, session);
        return existing;
    }

    private Object sessionLock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new Object());
    }

    private Path journalFilePath(String sessionId) {
        return sessionDirPath(sessionId).resolve(JOURNAL_FILE_NAME);
    }

    private Path sessionDirPath(String sessionId) {
        // P0 path-traversal guard (finding [13-15]): validate the caller-
        // supplied sessionId resolves inside rootDirectory before any
        // filesystem access. This is the defense-in-depth layer that also
        // catches raw sessionIds reaching resumeSession/restoreSession/
        // cancelSession (which bypass DefaultAgentEngine.resolveSessionId).
        return SessionIds.requireContainedPath(sessionId, rootDirectory);
    }

    private Path sessionFilePath(String sessionId) {
        return sessionDirPath(sessionId).resolve(SESSION_FILE_NAME);
    }

    private static String resolveChildAgentName(AgentSession parent, Map<String, Object> props) {
        if (props != null) {
            Object agentNameValue = props.get(PROPS_KEY_AGENT_NAME);
            if (agentNameValue instanceof String && !((String) agentNameValue).isEmpty()) {
                return (String) agentNameValue;
            }
        }
        return parent.getAgentName();
    }

    private static void mergeProps(AgentSession child, Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new HashMap<>(child.getMetadata());
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!PROPS_KEY_AGENT_NAME.equals(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        child.setMetadata(merged);
    }
}
