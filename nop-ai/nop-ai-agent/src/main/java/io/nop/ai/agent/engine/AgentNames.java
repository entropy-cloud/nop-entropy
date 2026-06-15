package io.nop.ai.agent.engine;

import java.util.regex.Pattern;

/**
 * Fail-closed validation for caller-supplied {@code agentName} / {@code agentId}
 * values that flow into the VFS resource path concatenation in
 * {@code DefaultAgentEngine.loadAgentModel} ({@code "/" + agentName +
 * ".agent.xml"}).
 *
 * <p>An {@code agentName} sourced from the public API
 * ({@code AgentMessageRequest.agentName}) — and indirectly from LLM-supplied
 * {@code call-agent} tool args ({@code CallAgentExecutor}) — flows unvalidated
 * into that concatenation and then into
 * {@code ResourceComponentManager.instance().loadComponentModel(path)}. Without
 * validation a caller-controlled value such as {@code "../../etc/passwd"}
 * drives the VFS loader to an arbitrary resource path (P2 path-injection
 * finding [13-16]).
 *
 * <p>This helper is the sibling of {@link SessionIds}: same attack class
 * (traversal / injection into a path-shaped string), distinct surface
 * (VFS resource load vs. filesystem session directory). Both enforce the
 * strict allow-list regex {@code ^[A-Za-z0-9_-]+$} with fail-closed
 * {@link NopAiAgentException} — no silent sanitization, no truncation, no
 * fall-back (Minimum Rule #24).
 *
 * <p>Centralizing all caller-supplied identifier validation (sessionId /
 * agentName / toolName) behind a single shared validator is a non-blocking
 * follow-up tracked in plan 190; this class fixes the live agentName defect
 * without committing to that refactor.
 */
public final class AgentNames {

    /** Strict allow-list: only ASCII letters, digits, underscore, hyphen. */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_-]+$");

    private AgentNames() {
    }

    /**
     * Identifier-level validation. Rejects {@code null}, empty, and any value
     * containing a character outside {@code [A-Za-z0-9_-]}. This catches
     * {@code /}, {@code \}, {@code ..}, NUL, whitespace, and any Unicode.
     *
     * <p>Used at the {@code loadAgentModel} chokepoint, which is the single
     * private method covering all three callers ({@code doExecute},
     * {@code resumeSession}, {@code restoreSession}) and the indirect
     * {@code CallAgentExecutor} → {@code engine.execute} path.
     *
     * @param agentName the caller-supplied agent name
     * @return the validated agent name (unchanged)
     * @throws NopAiAgentException if the name is {@code null}, empty, or
     *         contains any disallowed character
     */
    public static String requireValidIdentifier(String agentName) {
        if (agentName == null || agentName.isEmpty()) {
            throw new NopAiAgentException(
                    "agentName must not be null or empty (agent-name path-injection guard)");
        }
        if (!SAFE_NAME.matcher(agentName).matches()) {
            throw new NopAiAgentException(
                    "agentName contains invalid characters; only [A-Za-z0-9_-] are allowed "
                            + "(agent-name path-injection guard): agentName=" + agentName);
        }
        return agentName;
    }

    /**
     * Non-throwing predicate form of the allow-list check, for callers whose
     * documented contract is fail-with-error-result rather than throw (e.g.
     * {@code CallAgentExecutor}, which must return a clean LLM-facing error
     * result rather than throw uncaught).
     *
     * @param agentName the caller-supplied agent name
     * @return {@code true} iff the value is non-null, non-empty, and matches
     *         {@code ^[A-Za-z0-9_-]+$}
     */
    public static boolean isValidIdentifier(String agentName) {
        return agentName != null && !agentName.isEmpty() && SAFE_NAME.matcher(agentName).matches();
    }
}
