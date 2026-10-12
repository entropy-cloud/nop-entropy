package io.nop.ai.agent.engine;

import io.nop.ai.agent.NopAiAgentException;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.completion.ICompletionJudge;
import io.nop.ai.agent.completion.NoOpCompletionJudge;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.contribution.ContributionType;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.ISustainer;
import io.nop.ai.agent.reliability.IWaitCoordinator;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoOpGoalTracker;
import io.nop.ai.agent.reliability.NoOpSustainer;
import io.nop.ai.agent.reliability.NoOpWaitCoordinator;
import io.nop.ai.agent.repair.ChainRepairer;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.repair.NoOpToolCallRepairer;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.IApprovalGate;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IPostDenialGuard;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.core.agent.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.NoOpModelSwitchedMessageWriter;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.reliability.ICircuitBreaker;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.StandardRetryPolicy;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.toolkit.api.IToolManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fluent construction DSL for {@link ReActAgentExecutor}. Mirrors the
 * original nested builder (MA4.2-05 engine-class
 * split): every optional dependency defaults to its NoOp / pass-through
 * implementation inside {@link #build()} when not explicitly wired, so
 * building an executor is zero-regression versus the pre-split behaviour.
 */
    public class ReActAgentExecutorBuilder {
        private IChatService chatService;
        private IToolManager toolManager;
        private IAgentEventPublisher eventPublisher;
        // WI11/plan 2443 (A3): single config carrier replacing the 38 mirrored
        // fields. Parity init restores the former builder-field defaults for
        // the primitive timeouts (0 = disabled — Config setters accept 0 since
        // the A3b validation relaxation); heartbeat 60s matches both.
        private final DefaultAgentEngineConfig config = new DefaultAgentEngineConfig();
        {
            config.setLlmTimeoutMs(0L);
            config.setToolTimeoutMs(0L);
            config.setHeartBeatIntervalMs(60_000L);
        }
        private IHookRegistry hookRegistry;
        private ICompletionJudge completionJudge;
        private IAgentEngine engine;
        private IAgentMessenger messenger;
        private ISessionStore sessionStore;
        // the executor used to wrap the synchronous chatService.call.
        private Executor timeoutExecutor;

        public ReActAgentExecutorBuilder chatService(IChatService chatService) {
            this.chatService = chatService;
            return this;
        }

        public ReActAgentExecutorBuilder toolManager(IToolManager toolManager) {
            this.toolManager = toolManager;
            return this;
        }

        public ReActAgentExecutorBuilder eventPublisher(IAgentEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public ReActAgentExecutorBuilder permissionProvider(IPermissionProvider permissionProvider) {
            config.setPermissionProvider(permissionProvider);
            return this;
        }

        public ReActAgentExecutorBuilder toolAccessChecker(IToolAccessChecker toolAccessChecker) {
            config.setToolAccessChecker(toolAccessChecker);
            return this;
        }

        public ReActAgentExecutorBuilder pathAccessChecker(IPathAccessChecker pathAccessChecker) {
            config.setPathAccessChecker(pathAccessChecker);
            return this;
        }

        public ReActAgentExecutorBuilder auditLogger(IAuditLogger auditLogger) {
            config.setAuditLogger(auditLogger);
            return this;
        }

        public ReActAgentExecutorBuilder hookRegistry(IHookRegistry hookRegistry) {
            this.hookRegistry = hookRegistry;
            return this;
        }

        public ReActAgentExecutorBuilder toolCallRepairer(IToolCallRepairer toolCallRepairer) {
            config.setToolCallRepairer(toolCallRepairer);
            return this;
        }

        /**
         * Opt in to the 4-stage {@link ChainRepairer}, wired with this
         * Builder's {@code toolManager}. The {@code toolManager} must be set
         * before calling this method. The default remains
         * {@code NoOpToolCallRepairer.INSTANCE} when this method is not called.
         */
        public ReActAgentExecutorBuilder enableChainRepairer() {
            if (toolManager == null) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "toolManager must be set before enabling ChainRepairer");
            }
            config.setToolCallRepairer(ChainRepairer.withDefaults(toolManager));
            return this;
        }

        public ReActAgentExecutorBuilder contextCompactor(IContextCompactor contextCompactor) {
            config.setContextCompactor(contextCompactor);
            return this;
        }

        public ReActAgentExecutorBuilder contentGuardrail(IContentGuardrail contentGuardrail) {
            config.setContentGuardrail(contentGuardrail);
            return this;
        }

        public ReActAgentExecutorBuilder modelRouter(IModelRouter modelRouter) {
            config.setModelRouter(modelRouter);
            return this;
        }

        public ReActAgentExecutorBuilder tokenEstimator(ITokenEstimator tokenEstimator) {
            config.setTokenEstimator(tokenEstimator);
            return this;
        }

        public ReActAgentExecutorBuilder completionJudge(ICompletionJudge completionJudge) {
            this.completionJudge = completionJudge;
            return this;
        }

        public ReActAgentExecutorBuilder talents(List<ITalent> talents) {
            config.setTalents(talents);
            return this;
        }

        public ReActAgentExecutorBuilder skillProvider(ISkillProvider skillProvider) {
            config.setSkillProvider(skillProvider);
            return this;
        }

        /**
         * Wire the {@link IAgentEngine} self-reference so that engine-aware
         * tools (call-agent) can execute sub-agents. Optional: when null
         * (e.g. executor constructed outside the engine for testing),
         * engine-aware tools fail fast at execution time. The engine is the
         * only production caller of the Builder and always passes itself.
         */
        public ReActAgentExecutorBuilder engine(IAgentEngine engine) {
            this.engine = engine;
            return this;
        }

        /**
         * Wire the {@link IAgentMessenger} so that engine-aware tools
         * (send-message) can deliver inter-agent messages. Optional: when null,
         * messenger-aware tools fail fast at execution time.
         */
        public ReActAgentExecutorBuilder messenger(IAgentMessenger messenger) {
            this.messenger = messenger;
            return this;
        }

        /**
         * Wire the {@link ISecurityLevelResolver} consulted in the Layer 2
         * dispatch-path step (design §5.1). Optional: when null, defaults to
         * {@link DefaultSecurityLevelResolver} (trusted-by-default variant).
         */
        public ReActAgentExecutorBuilder securityLevelResolver(ISecurityLevelResolver securityLevelResolver) {
            config.setSecurityLevelResolver(securityLevelResolver);
            return this;
        }

        /**
         * Wire the {@link IPermissionMatrix} consulted in the Layer 2
         * dispatch-path step (design §5.3). Optional: when null, defaults to
         * {@link DefaultPermissionMatrix} (§5.3 channel × level matrix with
         * usability-safe null channel).
         */
        public ReActAgentExecutorBuilder permissionMatrix(IPermissionMatrix permissionMatrix) {
            config.setPermissionMatrix(permissionMatrix);
            return this;
        }

        /**
         * Wire the {@link IApprovalGate} consulted in the Layer 3
         * dispatch-path step (design §6.1 / §4.8) after the Layer 2 permission matrix
         * allows a tool call. Optional: when null, defaults to
         * {@link DefaultApprovalGate} (STANDARD/ELEVATED auto-approved,
         * RESTRICTED defense-in-depth denied — plan 199).
         */
        public ReActAgentExecutorBuilder approvalGate(IApprovalGate approvalGate) {
            config.setApprovalGate(approvalGate);
            return this;
        }

        /**
         * Wire the {@link IDenialLedger} consulted in the Layer 3 dispatch-path
         * step (design §6.2) at every deny checkpoint (Layer 1 / 2 / 3).
         * Optional: when null, defaults to {@link DefaultDenialLedger} (in-memory
         * threshold-based counting, threshold = 3).
         */
        public ReActAgentExecutorBuilder denialLedger(IDenialLedger denialLedger) {
            config.setDenialLedger(denialLedger);
            return this;
        }

        /**
         * Wire the {@link IPostDenialGuard} consulted in the dispatch loop
         * (design §6.3 / L3-7) before the Layer 1 {@code IToolAccessChecker}
         * check for each tool call (blind-retry detection), and recorded to
         * after every Layer 1/2/3 deny. Optional: when null, defaults to
         * {@link DefaultPostDenialGuard} (fingerprint-based blind-retry blocking).
         */
        public ReActAgentExecutorBuilder postDenialGuard(IPostDenialGuard postDenialGuard) {
            config.setPostDenialGuard(postDenialGuard);
            return this;
        }

        /**
         * Wire the {@link ICheckpointManager} consulted in the dispatch loop
         * (design §5.4 / L3-4) after every tool execution completes: a
         * {@link CheckpointType#TOOL_EXECUTION} checkpoint is recorded
         * capturing the tool-call payload and context-size snapshot. Optional:
         * when null, defaults to {@link NoOpCheckpoint} (no checkpoints
         * recorded — backward compatible).
         */
        public ReActAgentExecutorBuilder checkpointManager(ICheckpointManager checkpointManager) {
            config.setCheckpointManager(checkpointManager);
            return this;
        }

        /**
         * Wire the {@link ISessionStore} consulted in the dispatch loop
         * (plan 183 Phase 1) after every {@code saveCheckpoint} call: the
         * session's message list is synchronized to the latest
         * {@code ctx.getMessages()} (via {@code replaceMessages}) and the
         * session is persisted via {@code save}. This is the
         * <b>intra-execution</b> persistence path that makes crash/restart
         * restore viable — a crash mid-execution leaves a session file with
         * all messages produced up to the last completed tool call. With the
         * {@link io.nop.ai.agent.session.InMemorySessionStore} default
         * {@code save} is a no-op (in-memory readers share the live
         * reference), so wiring is transparent to existing behaviour
         * (backward compatible). When {@code sessionStore} is null (executor
         * constructed outside the engine for testing), the intra-execution
         * persistence is skipped.
         */
        public ReActAgentExecutorBuilder sessionStore(ISessionStore sessionStore) {
            this.sessionStore = sessionStore;
            return this;
        }

        /**
         * Wire the {@link IMemoryStoreProvider} consulted by working-memory
         * tools (read-memory / write-memory / search-memory) to resolve the
         * per-session {@link IAiMemoryStore} from the current
         * {@code sessionId} (plan 189 Phase 1). Optional: when null, the
         * dispatch loop skips memory-store resolution (context's
         * {@code memoryStore} stays null) and memory tools fail fast at
         * execution time. When non-null, the executor does NOT inherit a
         * default — it stays null until explicitly set.
         *
         * <p>The shipped default in {@link io.nop.ai.agent.engine.DefaultAgentEngine}
         * is an {@link io.nop.ai.agent.memory.InMemoryMemoryStoreProvider}
         * instance, so working-memory tools work out-of-the-box.
         */
        public ReActAgentExecutorBuilder memoryStoreProvider(IMemoryStoreProvider memoryStoreProvider) {
            config.setMemoryStoreProvider(memoryStoreProvider);
            return this;
        }

        /**
         * Wire the {@link IUsageRecorder} consulted at the ReAct loop's token
         * accumulation point (plan 201 / design
         * {@code nop-ai-agent-usage-and-billing.md} §3.1). Optional: when
         * null, defaults to {@link NoOpUsageRecorder} (usage data discarded —
         * pass-through, backward compatible).
         */
        public ReActAgentExecutorBuilder usageRecorder(IUsageRecorder usageRecorder) {
            config.setUsageRecorder(usageRecorder);
            return this;
        }

        /**
         * Wire the {@link IModelSwitchedMessageWriter} consulted after
         * {@code IModelRouter.route()} returns in the ReAct loop (plan 205 /
         * design {@code nop-ai-agent-usage-and-billing.md} §3.5). When the
         * routed model differs from the previous iteration's model, the writer
         * persists a {@code model-switched} audit message (role=80) to
         * {@code nop_ai_session_message}. Optional: when null, defaults to
         * {@link NoOpModelSwitchedMessageWriter} (pass-through, backward
         * compatible).
         */
        public ReActAgentExecutorBuilder modelSwitchedMessageWriter(IModelSwitchedMessageWriter modelSwitchedMessageWriter) {
            config.setModelSwitchedMessageWriter(modelSwitchedMessageWriter);
            return this;
        }

        /**
         * Wire the {@link IBudgetProvider} consulted once per ReAct iteration,
         * immediately before {@code IModelRouter.route()} (plan 206 / L2-22 /
         * design {@code nop-ai-agent-usage-and-billing.md} §3.6). The returned
         * snapshot is stored into {@code ctx.setBudgetSnapshot(...)} so a
         * functional router can read it and downgrade the model on budget
         * exhaustion. Optional: when null, defaults to
         * {@link NoOpBudgetProvider} (unlimited pass-through, backward
         * compatible).
         */
        public ReActAgentExecutorBuilder budgetProvider(IBudgetProvider budgetProvider) {
            config.setBudgetProvider(budgetProvider);
            return this;
        }

        /**
         * Wire the {@link IRetryPolicy} consulted by the single-LLM-call retry
         * loop (plan 207 / L3-2 / design {@code nop-ai-agent-llm-layer.md}
         * §7). When {@code chatService.call(...)} throws, the loop classifies
         * the error, builds a {@link RetryContext}, and asks the policy RETRY
         * / STOP / FALLBACK. Optional: when null, defaults to
         * {@link NoRetryPolicy} (unconditional STOP — fail fast, backward
         * compatible, zero-regression).
         */
        public ReActAgentExecutorBuilder retryPolicy(IRetryPolicy retryPolicy) {
            config.setRetryPolicy(retryPolicy);
            return this;
        }

        /**
         * Wire the {@link ICircuitBreaker} consulted at the single-LLM-call
         * retry loop's outer layer (plan 210 / L3-1 / design
         * {@code nop-ai-agent-reliability.md} §3.3 / §5.1). Before entering
         * the retry loop the breaker is asked whether the primary model may
         * be called; a false return fails fast with a {@code NopAiAgentException}.
         * Per-attempt failures and the eventual success are recorded back so
         * a functional breaker can track consecutive-failure patterns across
         * call cycles. Optional: when null, defaults to {@link AlwaysClosed}
         * (unconditional allow + explicit no-op recording — fail fast,
         * backward compatible, zero-regression).
         */
        public ReActAgentExecutorBuilder circuitBreaker(ICircuitBreaker circuitBreaker) {
            config.setCircuitBreaker(circuitBreaker);
            return this;
        }

        /**
         * Wire the {@link IGoalTracker} consulted at the ReAct loop's
         * per-iteration boundary (plan 211 / L3-3 / design
         * {@code nop-ai-agent-reliability.md} §5.3). {@code recordIteration} is
         * called once per iteration after the LLM response so the tracker can
         * update its per-session progress state; {@code assessGoal} is called at
         * the next iteration's start and a STUCK return aborts the loop with
         * status=escalated. Optional: when null, defaults to
         * {@link NoOpGoalTracker} (unconditional PROGRESSING + explicit no-op
         * recording — backward compatible, zero-regression).
         */
        public ReActAgentExecutorBuilder goalTracker(IGoalTracker goalTracker) {
            config.setGoalTracker(goalTracker);
            return this;
        }

        /**
         * Wire the {@link ISustainer} consulted at the ReAct loop's exit
         * decision point (plan 212 / L3-8 / design
         * {@code nop-ai-agent-reliability.md} §5.1a Sisyphean model). When the
         * reactLoop exits naturally because the iteration budget was exhausted
         * (MAX_ITERATIONS) while the status is still running, the engine asks
         * the sustainer CONTINUE (extend the budget and re-enter the loop) or
         * STOP (proceed to the terminal-state change). Optional: when null,
         * defaults to {@link NoOpSustainer} (unconditional STOP — backward
         * compatible, zero-regression).
         */
        public ReActAgentExecutorBuilder sustainer(ISustainer sustainer) {
            config.setSustainer(sustainer);
            return this;
        }

        /**
         * Plan 214 (L2-13a): wire the {@link IConflictStrategy} consulted
         * in the dispatch loop after the Layer 3 approval gate and before
         * {@code allowedCalls.add(...)} (design
         * {@code nop-ai-agent-multi-agent.md} §4.4). When the write-intent
         * registry reports a cross-session conflict on a file targeted by
         * the current tool call, the strategy decides ALLOW or DENY.
         * Optional: when null, defaults to {@link FailFastStrategy}
         * (fail-fast on cross-session conflicts — backward compatible,
         * zero-regression for single-session executions).
         */
        public ReActAgentExecutorBuilder conflictStrategy(IConflictStrategy conflictStrategy) {
            config.setConflictStrategy(conflictStrategy);
            return this;
        }

        /**
         * Plan 214 (L2-13a): wire the {@link IWriteIntentRegistry}
         * consulted in the dispatch loop to register write intents and
         * detect cross-session conflicts (design
         * {@code nop-ai-agent-multi-agent.md} §3.1). Optional: when null,
         * defaults to {@link InMemoryWriteIntentRegistry} (in-process
         * registry — backward compatible).
         */
        public ReActAgentExecutorBuilder writeIntentRegistry(IWriteIntentRegistry writeIntentRegistry) {
            config.setWriteIntentRegistry(writeIntentRegistry);
            return this;
        }

        /**
         * Plan 217 (L4-6): wire the {@link IContributionRegistry} consulted
         * at execution setup time for {@link ContributionType#PROMPT}
         * contribution resolution. PROMPT contributions' String fragments are
         * concatenated in ascending priority order and injected into the
         * system prompt via {@code injectSystemInstruction} (additive, same
         * mechanism as skill instructions). HOOK contributions are resolved
         * engine-side in {@code DefaultAgentEngine.resolveExecutor}. Optional:
         * when null, defaults to {@link NoOpContributionRegistry} (every
         * query returns empty, so no PROMPT fragment is injected — zero
         * regression).
         */
        public ReActAgentExecutorBuilder contributionRegistry(IContributionRegistry contributionRegistry) {
            config.setContributionRegistry(contributionRegistry);
            return this;
        }

        /**
         * Plan 219 (L4-7): wire the {@link io.nop.ai.agent.security.ISandboxBackend}
         * — the Layer 4 defense-in-depth chain tail (design §7.1 / §8).
         * The wired backend is held by the executor and made available to
         * tool executors that run inside the ReAct loop (future
         * shell-exec / code-exec IToolExecutor successors). The executor
         * itself does not call the backend on the dispatch path. Optional:
         * when null, defaults to
         * {@link io.nop.ai.agent.security.NoOpSandboxBackend} (host
         * ProcessBuilder execution — Layer 1 designable baseline, design
         * §7.1).
         */
        public ReActAgentExecutorBuilder sandboxBackend(io.nop.ai.agent.security.ISandboxBackend sandboxBackend) {
            config.setSandboxBackend(sandboxBackend);
            return this;
        }

        /**
         * Plan 225 (L4-8-team-tools): wire the {@link ITeamManager} made
         * available to team-aware tools (team-send-message / team-status /
         * team-task-create) via the dispatch loop's
         * {@link AgentToolExecuteContext}. The executor itself does not call
         * the teamManager on the dispatch path — team tools consume it at
         * execution time. Optional: when null, team tools honestly report
         * that the operation was not executed (backward compatible).
         */
        public ReActAgentExecutorBuilder teamManager(ITeamManager teamManager) {
            config.setTeamManager(teamManager);
            return this;
        }

        /**
         * Plan 225 (L4-8-team-tools): wire the {@link ITeamTaskStore} made
         * available to team task tools (team-task-create / team-status task
         * count) via the dispatch loop's
         * {@link AgentToolExecuteContext}. Optional: when null, team task
         * tools honestly report that the operation was not executed
         * (backward compatible).
         */
        public ReActAgentExecutorBuilder teamTaskStore(ITeamTaskStore teamTaskStore) {
            config.setTeamTaskStore(teamTaskStore);
            return this;
        }

        /**
         * Plan 228 (L4-team-acl-enforcement): wire the
         * {@link ITeamAclChecker} made available to the 4 team tool
         * executors (team-send-message / team-status / team-task-create /
         * team-task-update) via the dispatch loop's
         * {@link AgentToolExecuteContext}. The executor itself does not call
         * the checker on the dispatch path — team tools consume it at
         * execution time. Optional: when null, the shipped
         * {@link io.nop.ai.agent.team.NoOpTeamAclChecker} default applies
         * (allow(null) — zero behaviour regression).
         */
        public ReActAgentExecutorBuilder teamAclChecker(ITeamAclChecker teamAclChecker) {
            config.setTeamAclChecker(teamAclChecker);
            return this;
        }

        /**
         * Plan 271 (finding 14-03): wall-clock timeout (ms) for a single LLM
         * call inside the ReAct loop. A value {@code <= 0} disables the timeout
         * (backward-compatible escape hatch). The engine wires the shipped
         * default (120s) via {@code DefaultAgentEngine.resolveExecutor}.
         */
        public ReActAgentExecutorBuilder llmTimeoutMs(long llmTimeoutMs) {
            config.setLlmTimeoutMs(llmTimeoutMs);
            return this;
        }

        /**
         * Plan 271 (finding 14-03): wall-clock timeout (ms) for a single tool
         * call in the dispatch fanout. A value {@code <= 0} disables the timeout
         * (backward-compatible escape hatch). The engine wires the shipped
         * default (300s) via {@code DefaultAgentEngine.resolveExecutor}.
         */
        public ReActAgentExecutorBuilder heartBeatIntervalMs(long heartBeatIntervalMs) {
            config.setHeartBeatIntervalMs(heartBeatIntervalMs);
            return this;
        }

        public ReActAgentExecutorBuilder toolTimeoutPolicy(java.util.Map<String, Long> toolTimeoutPolicy) {
            config.setToolTimeoutPolicy(toolTimeoutPolicy != null ? toolTimeoutPolicy : java.util.Collections.emptyMap());
            return this;
        }

        public ReActAgentExecutorBuilder toolProgressTracker(io.nop.ai.agent.tool.IToolProgressTracker toolProgressTracker) {
            config.setToolProgressTracker(toolProgressTracker);
            return this;
        }

        public ReActAgentExecutorBuilder toolTimeoutMs(long toolTimeoutMs) {
            config.setToolTimeoutMs(toolTimeoutMs);
            return this;
        }

        /**
         * Plan 271 (finding 14-03 / 14-04): the executor used to run the
         * CompletableFuture that wraps the synchronous chatService.call for
         * wall-clock timeout enforcement. Should be the engine's dedicated
         * agent executor (virtual threads by default) so the LLM-call wrapper
         * does not contend with other {@code ForkJoinPool.commonPool()} users.
         */
        public ReActAgentExecutorBuilder timeoutExecutor(Executor timeoutExecutor) {
            this.timeoutExecutor = timeoutExecutor;
            return this;
        }

        /**
         * Wire the {@link IWaitCoordinator} consulted at the ReAct loop's
         * iteration top (design §13.1 WAIT_FOR primitive). When a wait
         * condition is registered and not yet satisfied, the loop produces a
         * WAIT_FOR checkpoint, sets status=waiting, and breaks — releasing the
         * thread while the session stays resident. Optional: when null,
         * defaults to {@link NoOpWaitCoordinator} (checkWait always returns
         * NONE — no wait ever registered, backward compatible, zero-regression).
         */
        public ReActAgentExecutorBuilder waitCoordinator(IWaitCoordinator waitCoordinator) {
            config.setWaitCoordinator(waitCoordinator);
            return this;
        }

        public ReActAgentExecutor build() {
            if (chatService == null) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "chatService must not be null");
            }
            if (toolManager == null) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "toolManager must not be null");
            }
            // WI11/plan 2443 (A3): config is the single carrier for the former
            // 38 config-backed parameters (builder-set values included).
            // The former build()-level per-parameter defaulting layer is
            // preserved verbatim below — the executor ctor null-defaults only
            // cover part of the surface; without these prefill defaults,
            // direct-builder callers would wire null checkers (behavior
            // regression caught by the In-ReAct-Loop test family).
            if (config.getPermissionProvider() == null) config.setPermissionProvider(new AllowAllPermissionProvider());
            if (config.getToolAccessChecker() == null) config.setToolAccessChecker(new DefaultToolAccessChecker());
            if (config.getPathAccessChecker() == null) config.setPathAccessChecker(new DefaultPathAccessChecker());
            if (config.getAuditLogger() == null) config.setAuditLogger(new Slf4jAuditLogger());
            if (config.getToolCallRepairer() == null) config.setToolCallRepairer(NoOpToolCallRepairer.INSTANCE);
            if (config.getContextCompactor() == null) config.setContextCompactor(NoOpContextCompactor.INSTANCE);
            if (config.getContentGuardrail() == null) config.setContentGuardrail(NoOpContentGuardrail.noOp());
            if (config.getModelRouter() == null) config.setModelRouter(PassThroughModelRouter.passThrough());
            if (config.getTokenEstimator() == null) config.setTokenEstimator(TokenEstimators.defaultEstimator());
            if (config.getUsageRecorder() == null) config.setUsageRecorder(NoOpUsageRecorder.noOp());
            if (config.getModelSwitchedMessageWriter() == null) config.setModelSwitchedMessageWriter(NoOpModelSwitchedMessageWriter.noOp());
            if (config.getBudgetProvider() == null) config.setBudgetProvider(NoOpBudgetProvider.noOp());
            if (config.getRetryPolicy() == null) config.setRetryPolicy(new StandardRetryPolicy());
            if (config.getCircuitBreaker() == null) config.setCircuitBreaker(new ThresholdBreaker());
            if (config.getGoalTracker() == null) config.setGoalTracker(NoOpGoalTracker.noOp());
            if (config.getSustainer() == null) config.setSustainer(NoOpSustainer.noOp());
            if (config.getConflictStrategy() == null) config.setConflictStrategy(FailFastStrategy.failFast());
            if (config.getWriteIntentRegistry() == null) config.setWriteIntentRegistry(new InMemoryWriteIntentRegistry());
            if (config.getContributionRegistry() == null) config.setContributionRegistry(NoOpContributionRegistry.noOp());
            if (config.getSandboxBackend() == null)
                config.setSandboxBackend(io.nop.ai.agent.security.NoOpSandboxBackend.INSTANCE);
            if (config.getToolProgressTracker() == null)
                config.setToolProgressTracker(io.nop.ai.agent.tool.NoOpToolProgressTracker.noOp());
            if (config.getWaitCoordinator() == null) config.setWaitCoordinator(NoOpWaitCoordinator.noOp());
            return new ReActAgentExecutor(
                    config,
                    chatService,
                    toolManager,
                    eventPublisher,
                    engine,
                    messenger,
                    hookRegistry,
                    sessionStore,
                    completionJudge,
                    timeoutExecutor);
        }
    }
