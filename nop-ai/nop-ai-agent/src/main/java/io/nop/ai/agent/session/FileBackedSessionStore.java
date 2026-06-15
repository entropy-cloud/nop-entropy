package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.SessionIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p><b>Retention</b>: the session file is overwritten on each save (full
 * rewrite, not append-only). At test scale this is safe; a
 * retention/rotation policy is a non-blocking follow-up (plan 183 Non-Goal).
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

    static final String PROPS_KEY_AGENT_NAME = "agentName";

    private final Path rootDirectory;
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
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
            throw new NopAiAgentException("FileBackedSessionStore: rootDirectory must not be null");
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
        AgentSession loaded = loadSessionFile(sessionId);
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
        AgentSession loaded = loadSessionFile(sessionId);
        if (loaded != null) {
            AgentSession raced = putIfAbsent(sessionId, loaded);
            return raced != null ? raced : loaded;
        }
        return null;
    }

    @Override
    public void remove(String sessionId) {
        sessions.remove(sessionId);
        Path sessionFile = sessionFilePath(sessionId);
        try {
            Files.deleteIfExists(sessionFile);
            // Best-effort cleanup of the now-empty per-session directory.
            Path sessionDir = sessionDirPath(sessionId);
            try (Stream<Path> entries = Files.list(sessionDir)) {
                if (!entries.findAny().isPresent()) {
                    Files.delete(sessionDir);
                }
            }
        } catch (IOException e) {
            throw new NopAiAgentException(
                    "FileBackedSessionStore.remove: failed to delete " + sessionFile
                            + ": " + e.getMessage(), e);
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
                    AgentSession loaded = reader.readIfExists(sessionFile);
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
                            sessionFile, e);
                }
            });
        } catch (IOException e) {
            throw new NopAiAgentException(
                    "FileBackedSessionStore.listAllSessions: failed to list root directory "
                            + rootDirectory + ": " + e.getMessage(), e);
        }
        return discovered;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Full persistence</b>: serialize the session to
     * {@code {rootDirectory}/{sessionId}/session.json}, overwriting the
     * previous state. Also updates the in-memory cache so subsequent
     * {@code get} calls return the latest state without a disk read.
     */
    @Override
    public void save(AgentSession session) {
        if (session == null) {
            throw new NopAiAgentException("FileBackedSessionStore.save: session must not be null");
        }
        Path sessionFile = sessionFilePath(session.getSessionId());
        writer.write(sessionFile, session);
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
        AgentSession parent = get(parentSessionId);
        if (parent == null) {
            throw new NopAiAgentException(
                    "forkSession failed: parent session not found: parentSessionId=" + parentSessionId);
        }

        String childAgentName = resolveChildAgentName(parent, props);
        String childSessionId = UUID.randomUUID().toString();
        AgentSession child = AgentSession.create(childSessionId, childAgentName);

        if (inheritContext) {
            child.appendMessages(parent.getMessages());
            child.setPlanId(parent.getPlanId());
            child.setMetadata(parent.getMetadata());
        }

        mergeProps(child, props);

        child.setParentSessionId(parentSessionId);
        save(child);

        return childSessionId;
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private AgentSession putIfAbsent(String sessionId, AgentSession session) {
        AgentSession existing = sessions.putIfAbsent(sessionId, session);
        return existing;
    }

    private AgentSession loadSessionFile(String sessionId) {
        Path sessionFile = sessionFilePath(sessionId);
        return reader.readIfExists(sessionFile);
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
