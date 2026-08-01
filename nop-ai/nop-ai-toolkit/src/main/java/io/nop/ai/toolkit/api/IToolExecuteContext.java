package io.nop.ai.toolkit.api;

import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import java.io.File;
import java.util.Map;

/**
 * Execution context passed to tool executors and interceptors for a single tool call.
 * <p>
 * Carries the sandboxed work directory, environment variables, expiry deadline,
 * cancellation token, the sandboxed file system, and the executor used for
 * asynchronous work. The default implementation ({@code ToolExecuteContext} in
 * nop-ai-toolkit) exposes immutable values (envs are unmodifiable).
 */
public interface IToolExecuteContext {
    /**
     * @return the sandboxed working directory of the tool call
     */
    File getWorkDir();

    /**
     * @return the environment variables available to the tool call (may be empty, never null)
     */
    Map<String, String> getEnvs();

    /**
     * @return the epoch-millis deadline after which the tool call is considered expired
     */
    long getExpireAt();

    /**
     * @return the cancellation token for the tool call (may be null when cancellation is not supported)
     */
    ICancelToken getCancelToken();

    /**
     * @return the sandboxed file system available to the tool call
     */
    IToolFileSystem getFileSystem();

    /**
     * @return the executor used for asynchronous work within the tool call
     */
    IThreadPoolExecutor getExecutor();

    /**
     * Read-only view of the per-session compaction archive, exposed so the
     * {@code read-ref} tool can read back original content that was replaced
     * by a {@code shortRef} pointer during reference-style compaction (design
     * {@code nop-ai-agent-context-model.md} §8.2 Decision C).
     * <p>
     * <b>Default UOE bridge</b>: only the agent engine's
     * {@code AgentToolExecuteContext} overrides this to return the
     * {@code AgentSession}'s archive. The other {@code IToolExecuteContext}
     * implementations (toolkit {@code ToolExecuteContext}, test mocks) inherit
     * this default — when {@code read-ref} is invoked outside an agent engine
     * (no archive available) it fails fast with a descriptive error rather
     * than silently returning empty content (Minimum Rules #24). This mirrors
     * the {@code ISessionStore.save}/{@code listAllSessions} default-UOE
     * precedent; toolkit uses the JDK {@link UnsupportedOperationException}
     * because it cannot import the agent module's
     * {@code NopAiAgentException}.
     *
     * @return the archive read-only view; never null when overridden by the
     *         agent engine
     * @throws UnsupportedOperationException when no archive is available
     *         (default implementation — callers surface this as "read-ref
     *         not available outside an agent session")
     */
    default ICompactionArchiveReader getCompactionArchiveReader() {
        throw new UnsupportedOperationException(
                "getCompactionArchiveReader is not available on this IToolExecuteContext implementation "
                        + "(read-ref requires the agent engine's AgentToolExecuteContext). "
                        + "Class: " + getClass().getName());
    }
}
