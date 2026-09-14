package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtSession;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.ai.tools.utils.AiToolsHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static io.nop.ai.core.NopAiCoreErrors.ARG_FILE_PATH;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID;

/**
 * JSON file-backed storage for sequential-thinking session thoughts (P3-MA1-013 ruling).
 * <p>
 * <b>Ruling (2026-08-01): keep file persistence — do not migrate to ORM.</b> See
 * {@code ai-dev/design/nop-ai/03-sequential-thinking-storage.md} for the full decision
 * record. Summary:
 * <ul>
 * <li>Session-scoped tool: {@code SequentialThinkingBizModel} keys all data by the chat
 * session id ({@code AiToolsHelper.makeChatSessionId}); each session's thoughts are an
 * independent, ephemeral conversation aid — not business data with cross-entity
 * consistency requirements.</li>
 * <li>Data volume is tiny (tens of thoughts per session, ~1KB each); full read/write per
 * operation is acceptable.</li>
 * <li>Concurrency: a single {@code ReentrantLock} guards all operations; the storage is
 * single-JVM (framework module), no cluster semantics.</li>
 * <li>ORM migration was rejected: nop-ai-tools has no ORM/DAO dependency and nop-ai-core
 * intentionally dropped {@code nop-dao} (P2-MA3-001); a session-scoped conversation aid
 * does not justify DB schema + DAO + transaction wiring.</li>
 * </ul>
 * <p>
 * <b>Storage directory resolution:</b> the config default
 * {@code nop.ai.sequential-thinking-tool.storage-dir-path} is {@code ./_tmp/ai/sequential-thinking/store}
 * — resolved relative to the JVM working directory ({@link FileHelper#resolveFile}),
 * writable by default. An empty/null path falls back to {@code ~/.mcp_sequential_thinking}
 * (user home). The previous default {@code /nop/ai/sequential-thinking/store} was an
 * absolute file-system path that ordinary users cannot write (live defect, fixed).
 * <p>
 * <b>Limitation (documented, not a defect):</b> file persistence is per-JVM. Multi-instance
 * deployments must configure a shared path, or a future migration trigger applies
 * (see the design doc).
 */
public class ThoughtStorage {
    private static final Logger LOG = LoggerFactory.getLogger(ThoughtStorage.class);

    private final Lock lock = new ReentrantLock();
    private final File storageDir;

    public ThoughtStorage(String storageDirPath) {
        if (storageDirPath == null || storageDirPath.isEmpty()) {
            this.storageDir = new File(System.getProperty("user.home"), ".mcp_sequential_thinking");
        } else {
            File dir = new File(storageDirPath);
            this.storageDir = dir.isAbsolute() ? dir : FileHelper.resolveFile(storageDirPath);
        }
    }

    private File getSessionFile(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        // path-traversal guard (audit ai-toolkit-skills P0): sessionId is
        // client-controlled (chat session header) and must never carry a path
        AiToolsHelper.requireValidSessionId(sessionId);
        return new File(storageDir, sessionId + ".json");
    }

    private List<ThoughtData> loadSession(String sessionId) {
        File sessionFile = getSessionFile(sessionId);

        if (!sessionFile.exists()) {
            return new ArrayList<>();
        }

        String json = FileHelper.readText(sessionFile, null);
        ThoughtSession session = fromJson(json, ThoughtSession.class);
        return session.getThoughts();
    }

    private void saveSession(String sessionId, List<ThoughtData> thoughts) {
        File sessionFile = getSessionFile(sessionId);
        ThoughtSession session = new ThoughtSession(thoughts);
        String json = toJson(session);
        atomicWriteText(sessionFile, json);
    }

    public void addThought(String sessionId, ThoughtData thought) {
        Objects.requireNonNull(thought, "thought cannot be null");

        lock.lock();
        try {
            List<ThoughtData> thoughts = loadSession(sessionId);
            thoughts.add(thought);
            saveSession(sessionId, thoughts);
        } finally {
            lock.unlock();
        }
    }

    public List<ThoughtData> getAllThoughts(String sessionId) {
        lock.lock();
        try {
            return new ArrayList<>(loadSession(sessionId));
        } finally {
            lock.unlock();
        }
    }

    public List<ThoughtData> getThoughtsByStage(String sessionId, ThoughtStage stage) {
        Objects.requireNonNull(stage, "stage cannot be null");

        lock.lock();
        try {
            return loadSession(sessionId).stream()
                    .filter(t -> t.getStage() == stage)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public void clearHistory(String sessionId) {
        lock.lock();
        try {
            saveSession(sessionId, new ArrayList<>());
        } finally {
            lock.unlock();
        }
    }

    public void exportSession(String sessionId, String filePath) {
        Objects.requireNonNull(filePath, "filePath cannot be null");

        lock.lock();
        try {
            List<ThoughtData> thoughts = loadSession(sessionId);
            ThoughtSession session = new ThoughtSession(thoughts);
            String json = toJson(session);
            FileHelper.writeText(resolveExportFile(filePath), json, null);
        } finally {
            lock.unlock();
        }
    }

    public void importSession(String sessionId, String filePath) {
        Objects.requireNonNull(filePath, "filePath cannot be null");

        lock.lock();
        try {
            String json = FileHelper.readText(resolveExportFile(filePath), null);
            ThoughtSession session = fromJson(json, ThoughtSession.class);
            saveSession(sessionId, session.getThoughts());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Path-containment guard for export/import (plan 2026-09-14-1937-1 Phase 1,
     * ruling: keep + constrain): the caller-supplied filePath is used verbatim
     * for read/write, so it must resolve inside {@link #storageDir}. Canonical
     * resolution covers {@code ..} segments and symlinks even when the target
     * file does not exist yet. Fail-closed: reject with
     * {@link ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID}, never silently rewrite or
     * truncate the path.
     */
    private File resolveExportFile(String filePath) {
        if (StringHelper.isEmpty(filePath)) {
            throw new NopException(ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID)
                    .param(ARG_FILE_PATH, filePath);
        }
        try {
            File canonicalFile = new File(filePath).getCanonicalFile();
            File canonicalDir = storageDir.getCanonicalFile();
            String canonicalPath = StringHelper.normalizePath(canonicalFile.getPath());
            String canonicalStorageDir = StringHelper.normalizePath(canonicalDir.getPath());
            if (!StringHelper.pathStartsWith(canonicalPath, canonicalStorageDir)) {
                throw new NopException(ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID)
                        .param(ARG_FILE_PATH, filePath);
            }
            return new File(filePath);
        } catch (IOException e) {
            throw new NopException(ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID, e)
                    .param(ARG_FILE_PATH, filePath);
        }
    }

    /**
     * Atomic replace (plan 2026-09-14-1937-1 Phase 2): write to a temp file in
     * the same directory, then rename over the target, so a partial write or
     * crash never truncates the previous session content. {@code ATOMIC_MOVE}
     * is attempted first; plain {@code Files.move} with {@code REPLACE_EXISTING}
     * is the explicit fallback for filesystems without atomic rename support.
     * The temp file is always created in the target's directory, so no
     * cross-filesystem move can occur.
     */
    private static void atomicWriteText(File target, String text) {
        FileHelper.assureParent(target);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp" + UUID.randomUUID());
        try {
            Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                LOG.debug("Failed to delete temp file after write: {}", temp, e);
            }
        }
    }

    private String toJson(Object obj) {
        return JsonTool.stringify(obj);
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        return JsonTool.parseBeanFromText(json, clazz);
    }
}