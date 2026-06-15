package io.nop.ai.agent.engine;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Fail-closed validation for caller-supplied {@code sessionId} values.
 *
 * <p>A {@code sessionId} sourced from the public API
 * ({@code AgentMessageRequest.sessionId}) flows into
 * {@code Path.resolve(sessionId)} calls in {@code FileBackedSessionStore} and
 * {@code FileBackedCheckpointManager}. Without validation a caller-controlled
 * value such as {@code "../../etc/cron.d/exploit"} escapes the configured root
 * directory, enabling arbitrary file write/delete (P0 path-traversal finding
 * [13-15]).
 *
 * <p>This helper provides two layers, each of which throws
 * {@link NopAiAgentException} on any violation (fail-closed — no silent
 * sanitization, no truncation, no fall-back):
 * <ol>
 *   <li><b>Identifier-level check</b> ({@link #requireValidIdentifier}):
 *       rejects {@code null}/empty and enforces the strict allow-list regex
 *       {@code ^[A-Za-z0-9_-]+$}. This is the only layer runnable where no
 *       {@code rootDirectory} is in scope (e.g. at the engine layer in
 *       {@code DefaultAgentEngine.resolveSessionId}).</li>
 *   <li><b>Containment check</b> ({@link #requireContainedPath}): runs the
 *       identifier check, then asserts that
 *       {@code rootDirectory.resolve(id).normalize()} stays within
 *       {@code rootDirectory.normalize()} ({@code startsWith}). This is the
 *       defense-in-depth layer runnable at the store/checkpoint layer, where
 *       {@code rootDirectory} is in scope, and is what catches the raw caller
 *       sessionIds that reach {@code resumeSession}/{@code restoreSession}/
 *       {@code cancelSession} (which bypass the engine-level
 *       {@code resolveSessionId}).</li>
 * </ol>
 *
 * <p>Placed in the {@code engine} package (alongside
 * {@link NopAiAgentException}) so the {@code session} and {@code reliability}
 * packages can reach it via the same dependency direction they already use for
 * {@link NopAiAgentException} — no new circular dependency is introduced.
 */
public final class SessionIds {

    /** Strict allow-list: only ASCII letters, digits, underscore, hyphen. */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    private SessionIds() {
    }

    /**
     * Identifier-level validation. Rejects {@code null}, empty, and any value
     * containing a character outside {@code [A-Za-z0-9_-]}. This catches
     * {@code /}, {@code \}, {@code ..}, NUL, whitespace, and any Unicode.
     *
     * @param sessionId the caller-supplied session id
     * @return the validated session id (unchanged)
     * @throws NopAiAgentException if the id is {@code null}, empty, or contains
     *         any disallowed character
     */
    public static String requireValidIdentifier(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopAiAgentException(
                    "sessionId must not be null or empty (path-traversal guard)");
        }
        if (!SAFE_ID.matcher(sessionId).matches()) {
            throw new NopAiAgentException(
                    "sessionId contains invalid characters; only [A-Za-z0-9_-] are allowed "
                            + "(path-traversal guard): sessionId=" + sessionId);
        }
        return sessionId;
    }

    /**
     * Filesystem containment validation. Runs the identifier check, then
     * resolves {@code sessionId} against {@code rootDirectory} and asserts the
     * normalized result stays inside the normalized root. This guarantees the
     * resolved path cannot escape the root even for allow-list edge cases.
     *
     * @param sessionId     the caller-supplied session id
     * @param rootDirectory the root directory sessions live under
     * @return the normalized, contained session directory path
     * @throws NopAiAgentException if the id fails the identifier check, if
     *         {@code rootDirectory} is {@code null}, or if the resolved path
     *         escapes the root
     */
    public static Path requireContainedPath(String sessionId, Path rootDirectory) {
        requireValidIdentifier(sessionId);
        if (rootDirectory == null) {
            throw new NopAiAgentException(
                    "rootDirectory must not be null for sessionId containment check "
                            + "(path-traversal guard)");
        }
        Path normalizedRoot = rootDirectory.normalize();
        Path resolved = rootDirectory.resolve(sessionId).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new NopAiAgentException(
                    "sessionId resolves outside the root directory (path-traversal guard): "
                            + "sessionId=" + sessionId + ", rootDirectory=" + rootDirectory
                            + ", resolved=" + resolved);
        }
        return resolved;
    }
}
