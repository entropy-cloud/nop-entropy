package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Agent-domain {@link IToolExecuteContext} that additionally exposes the
 * {@link IAgentEngine}, {@link IAgentMessenger}, and current session metadata
 * (sessionId, agentName) to engine-aware tools such as {@code call-agent} and
 * {@code send-message}.
 *
 * <p>Legacy tools that only depend on the {@link IToolExecuteContext} interface
 * (workDir, envs, expireAt, cancelToken, fileSystem, executor) are unaffected —
 * they never access the extra fields.
 *
 * <p>Engine-aware tools access the extra fields via casting the context or via
 * direct field access. If the engine or messenger is null (e.g. the executor
 * was constructed outside the engine for testing), engine-aware tools must
 * fail fast with a descriptive error rather than silently no-op.
 */
public class AgentToolExecuteContext implements IToolExecuteContext {

    private final File workDir;
    private final Map<String, String> envs;
    private final long expireAt;
    private final ICancelToken cancelToken;
    private final IToolFileSystem fileSystem;
    private final IThreadPoolExecutor executor;
    private final IAgentEngine engine;
    private final IAgentMessenger messenger;
    private final String sessionId;
    private final String agentName;
    private final Set<String> allowedTools;

    public AgentToolExecuteContext(File workDir,
                                   Map<String, String> envs,
                                   long expireAt,
                                   ICancelToken cancelToken,
                                   IToolFileSystem fileSystem,
                                   IThreadPoolExecutor executor,
                                   IAgentEngine engine,
                                   IAgentMessenger messenger,
                                   String sessionId,
                                   String agentName) {
        this(workDir, envs, expireAt, cancelToken, fileSystem, executor,
                engine, messenger, sessionId, agentName, null);
    }

    /**
     * Extended constructor that additionally carries the current agent's
     * <b>effective (clamped)</b> allowed tool set, used by engine-aware tools
     * (e.g. {@code call-agent}) to propagate a parent permission constraint to
     * sub-agents (design §4.4).
     *
     * @param allowedTools the current agent's effective (clamped) allowed tool
     *                     names; {@code null} means "no constraint information
     *                     available" — engine-aware tools do not propagate a
     *                     parent constraint (backward-compatible top-level
     *                     behavior). A non-null set (including an empty set) is
     *                     propagated verbatim as the parent's effective tool set
     *                     to sub-agents.
     */
    public AgentToolExecuteContext(File workDir,
                                   Map<String, String> envs,
                                   long expireAt,
                                   ICancelToken cancelToken,
                                   IToolFileSystem fileSystem,
                                   IThreadPoolExecutor executor,
                                   IAgentEngine engine,
                                   IAgentMessenger messenger,
                                   String sessionId,
                                   String agentName,
                                   Set<String> allowedTools) {
        this.workDir = workDir;
        this.envs = envs != null ? envs : Collections.emptyMap();
        this.expireAt = expireAt;
        this.cancelToken = cancelToken;
        this.fileSystem = fileSystem;
        this.executor = executor;
        this.engine = engine;
        this.messenger = messenger;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.allowedTools = allowedTools;
    }

    @Override
    public File getWorkDir() {
        return workDir;
    }

    @Override
    public Map<String, String> getEnvs() {
        return envs;
    }

    @Override
    public long getExpireAt() {
        return expireAt;
    }

    @Override
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public IToolFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public IThreadPoolExecutor getExecutor() {
        return executor;
    }

    public IAgentEngine getEngine() {
        return engine;
    }

    public IAgentMessenger getMessenger() {
        return messenger;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    /**
     * Return the current agent's effective (clamped) allowed tool set, or
     * {@code null} when no constraint information is available (top-level agent
     * whose context was constructed by a caller that did not opt into the
     * constraint field). {@code call-agent} reads this set to build and
     * propagate a {@link io.nop.ai.agent.security.ParentPermissionConstraint}
     * to sub-agents.
     */
    public Set<String> getAllowedTools() {
        return allowedTools;
    }
}
