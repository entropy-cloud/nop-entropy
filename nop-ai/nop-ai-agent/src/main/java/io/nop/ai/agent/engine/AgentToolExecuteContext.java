package io.nop.ai.agent.engine;

import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.model.PathRuleModel;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;

import java.io.File;
import java.util.Collections;
import java.util.List;
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
    private final Set<String> allowedPathRoots;
    private final List<PathRuleModel> allowedPathRules;
    private final IAiMemoryStore memoryStore;

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
        this(workDir, envs, expireAt, cancelToken, fileSystem, executor,
                engine, messenger, sessionId, agentName, allowedTools, null);
    }

    /**
     * Full constructor carrying both the current agent's effective (clamped)
     * allowed tool set AND its effective (clamped) allowed path roots, used by
     * engine-aware tools (e.g. {@code call-agent}) to propagate a parent
     * permission constraint to sub-agents (design §4.4:
     * 工具权限 = 父权限 ∩ 子配置, 文件权限 = 父权限 ∩ 子配置).
     *
     * @param allowedTools     the current agent's effective (clamped) allowed
     *                         tool names; {@code null} means "no constraint
     *                         information available" (backward-compatible
     *                         top-level behavior). A non-null set (including
     *                         empty) is propagated as the parent's effective
     *                         tool set.
     * @param allowedPathRoots the current agent's effective (clamped) allowed
     *                         path roots (normalized absolute directory roots);
     *                         {@code null} means ABSENT (no declared path scope
     *                         → no path confinement, backward compatible). A
     *                         non-null set (including empty) is propagated as
     *                         the parent's effective path roots. PRESENT({}) =
     *                         deny all paths (maximum restriction).
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
                                   Set<String> allowedTools,
                                   Set<String> allowedPathRoots) {
        this(workDir, envs, expireAt, cancelToken, fileSystem, executor,
                engine, messenger, sessionId, agentName, allowedTools, allowedPathRoots, null);
    }

    /**
     * Full constructor carrying the current agent's effective (clamped) allowed
     * tool set, effective (clamped) allowed path roots, AND effective (clamped)
     * allowed path rules, used by engine-aware tools (e.g. {@code call-agent})
     * to propagate a parent permission constraint to sub-agents (design §4.4:
     * 文件权限 = 父权限 ∩ 子配置).
     *
     * @param allowedPathRules the current agent's effective (clamped) accumulated
     *                         path-rule chain; {@code null} means ABSENT (no
     *                         declared path-rules → no rule confinement). A
     *                         non-null List (including empty) is propagated as
     *                         the parent's effective path rules.
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
                                   Set<String> allowedTools,
                                   Set<String> allowedPathRoots,
                                   List<PathRuleModel> allowedPathRules) {
        this(workDir, envs, expireAt, cancelToken, fileSystem, executor,
                engine, messenger, sessionId, agentName, allowedTools, allowedPathRoots,
                allowedPathRules, null);
    }

    /**
     * Full constructor additionally carrying the per-session
     * {@link IAiMemoryStore} resolved by the dispatch loop from the
     * engine's {@link io.nop.ai.agent.memory.IMemoryStoreProvider}. Working-memory
     * tools (read-memory / write-memory / search-memory) read the store from
     * here.
     *
     * <p>When {@code memoryStore} is {@code null} (the engine has not been
     * wired with a provider, or the caller is testing the executor outside the
     * engine), memory tools fail fast at execution time with a descriptive
     * error rather than silently no-op.
     *
     * @param memoryStore the per-session memory store; {@code null} is a
     *                    legitimate value (memory tools fail fast when null)
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
                                   Set<String> allowedTools,
                                   Set<String> allowedPathRoots,
                                   List<PathRuleModel> allowedPathRules,
                                   IAiMemoryStore memoryStore) {
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
        this.allowedPathRoots = allowedPathRoots;
        this.allowedPathRules = allowedPathRules;
        this.memoryStore = memoryStore;
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

    /**
     * Return the current agent's effective (clamped) allowed path roots
     * (normalized absolute directory roots), or {@code null} when no path-scope
     * information is available (ABSENT — no declared path scope, backward
     * compatible). {@code call-agent} reads this set to include the parent's
     * path roots in the propagated
     * {@link io.nop.ai.agent.security.ParentPermissionConstraint}.
     *
     * @return {@code null} (ABSENT) or a non-null Set (PRESENT, possibly empty)
     */
    public Set<String> getAllowedPathRoots() {
        return allowedPathRoots;
    }

    /**
     * Return the current agent's effective (clamped) accumulated path-rule
     * chain, or {@code null} when no path-rule information is available
     * (ABSENT — no declared path-rules, backward compatible).
     * {@code call-agent} reads this list to include the parent's effective
     * path rules in the propagated
     * {@link io.nop.ai.agent.security.ParentPermissionConstraint}.
     *
     * @return {@code null} (ABSENT) or a non-null List (PRESENT)
     */
    public List<PathRuleModel> getAllowedPathRules() {
        return allowedPathRules;
    }

    /**
     * Return the per-session {@link IAiMemoryStore} resolved from the
     * engine's {@link io.nop.ai.agent.memory.IMemoryStoreProvider}, or
     * {@code null} when no provider is wired (memory tools fail fast at
     * execution time with a descriptive error). Working-memory tools
     * (read-memory / write-memory / search-memory) read the store from here.
     *
     * @return {@code null} (no provider wired) or a non-null per-session store
     */
    public IAiMemoryStore getMemoryStore() {
        return memoryStore;
    }
}
