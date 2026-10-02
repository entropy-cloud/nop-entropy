package io.nop.ai.agent.engine;

import io.nop.ai.agent.engine.NopAiAgentException;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.agent.budget.BudgetSnapshot;
import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.compact.ToolResultTruncator;
import io.nop.ai.agent.completion.CompletionDecision;
import io.nop.ai.agent.completion.ICompletionJudge;
import io.nop.ai.agent.completion.NoOpCompletionJudge;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.GoalAssessment;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.ISustainer;
import io.nop.ai.agent.reliability.IWaitCoordinator;
import io.nop.ai.agent.reliability.IterationSnapshot;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoOpGoalTracker;
import io.nop.ai.agent.reliability.NoOpSustainer;
import io.nop.ai.agent.reliability.NoOpWaitCoordinator;
import io.nop.ai.agent.reliability.SustainContext;
import io.nop.ai.agent.reliability.SustainDecision;
import io.nop.ai.agent.reliability.SustainStopReason;
import io.nop.ai.agent.reliability.WaitCondition;
import io.nop.ai.agent.reliability.WaitDecision;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.repair.NoOpToolCallRepairer;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.router.RoutingResult;
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
import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.ai.agent.security.SecurityCheckpointChain;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.core.agent.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.NoOpModelSwitchedMessageWriter;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.NoOpSkillProvider;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.agent.usage.UsageRecord;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.reliability.ICircuitBreaker;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.ModelKeys;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.StandardRetryPolicy;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.api.core.json.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class ReActAgentExecutor implements IAgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ReActAgentExecutor.class);


    /**
     * AR-06 (plan 277): maximum number of re-enter requests honored per
     * re-entrant hook point (BEFORE_TOOL_RESULT_PROCESSED /
     * AFTER_TOOL_RESULT_PROCESSED) within a single ReAct iteration. The
     * counter is scoped per-iteration (reset at the start of each reactLoop
     * iteration), so a long session with legitimate per-iteration re-enter
     * hooks is not silently starved by a cumulative session-wide cap.
     * Within one iteration, after this many re-enter requests are honored
     * for a given hook point, subsequent re-enter requests for that point
     * are downgraded to PassResult with a WARN log.
     */
    public static final int DEFAULT_MAX_REENTRIES = 3;
    public static final int DEFAULT_MAX_COMPLETION_CONTINUES = 3;
    public static final double DEFAULT_TRIGGER_TOKEN_PERCENT = 0.8;
    public static final int DEFAULT_TRIGGER_MAX_MESSAGES = 30;
    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 128000;

    /**
     * W5-3 (BAIL): per-request cap on consecutive POST_REASONING middleware
     * bails. POST_REASONING re-prompt (whether triggered by BAIL or by
     * {@code checkOutputGuardrail}) is already bounded by {@code maxIterations}
     * (each {@code continue} consumes an iteration). This cap is the BAIL-
     * specific early-fail: after this many BAILs in one {@code execute()} call,
     * a {@code NopAiAgentException} is thrown (fail-loud, no silent continue,
     * no infinite loop). Modeled after
     * {@link LlmCallCoordinator#MAX_EXECUTION_VETOES}. Not shared with
     * {@code checkOutputGuardrail} counts (independent mechanisms — design
     * §5.4 裁定 D).
     */
    public static final int MAX_POST_REASONING_BAILS = 3;

    private final ITokenEstimator tokenEstimator;
    private final ICompletionJudge completionJudge;
    private final IGoalTracker goalTracker;
    private final ISustainer sustainer;
    private final ISessionStore sessionStore;
    private final ICheckpointManager checkpointManager;
    private final IBudgetProvider budgetProvider;
    private final IModelRouter modelRouter;
    private final IModelSwitchedMessageWriter modelSwitchedMessageWriter;
    private final IUsageRecorder usageRecorder;
    private final IDenialLedger denialLedger;
    private final IToolCallRepairer toolCallRepairer;
    private final io.nop.ai.agent.security.ISandboxBackend sandboxBackend;
    private final SecurityCheckpointChain checkpointChain;
    private final IToolAccessChecker toolAccessChecker;

    private final AgentHookInvoker hookInvoker;
    private final LlmCallCoordinator llmCoordinator;
    private final AgentSecurityConsultation securityConsultation;
    private final AgentCompactionCoordinator compactionCoordinator;
    private final AgentToolPlanResolver toolPlanResolver;
    private final AgentPromptAssembly promptAssembly;
    private final AgentToolDispatcher toolDispatcher;
    // Plan 2285 (WI4): heartbeat slice interval for the tool wait phase.
    private final long heartBeatIntervalMs;
    // Plan 2285 (WI4): tool execution progress/cancel seam.
    private final io.nop.ai.agent.tool.IToolProgressTracker toolProgressTracker;
    private final AgentLoopGuard loopGuard;
    private final IWaitCoordinator waitCoordinator;


    // package-private so the extracted ReActAgentExecutorBuilder can invoke it
    // (MA4.2-05 split; access relaxation whitelist).
    ReActAgentExecutor(
            IChatService chatService, IToolManager toolManager,
                               IAgentEventPublisher eventPublisher,
                               IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker,
                               IPathAccessChecker pathAccessChecker,
                               IAuditLogger auditLogger,
                               IHookRegistry hookRegistry,
                               IToolCallRepairer toolCallRepairer,
                               IContextCompactor contextCompactor,
                               IContentGuardrail contentGuardrail,
                               IModelRouter modelRouter,
                               ITokenEstimator tokenEstimator,
                               ICompletionJudge completionJudge,
                               List<ITalent> talents,
                               ISkillProvider skillProvider,
                               IAgentEngine engine,
                               IAgentMessenger messenger,
                                ISecurityLevelResolver securityLevelResolver,
                                IPermissionMatrix permissionMatrix,
                                IApprovalGate approvalGate,
                                  IDenialLedger denialLedger,
                                  IPostDenialGuard postDenialGuard,
                                    ICheckpointManager checkpointManager,
                                    ISessionStore sessionStore,
                                      IMemoryStoreProvider memoryStoreProvider,
                                      IUsageRecorder usageRecorder,
                                       IModelSwitchedMessageWriter modelSwitchedMessageWriter,
                                       IBudgetProvider budgetProvider,
                                       IRetryPolicy retryPolicy,
                                         ICircuitBreaker circuitBreaker,
                                         IGoalTracker goalTracker,
                                         ISustainer sustainer,
                                           IConflictStrategy conflictStrategy,
                                           IWriteIntentRegistry writeIntentRegistry,
                                           IContributionRegistry contributionRegistry,
                                           io.nop.ai.agent.security.ISandboxBackend sandboxBackend,
                                           ITeamManager teamManager,
                                           ITeamTaskStore teamTaskStore,
                                           ITeamAclChecker teamAclChecker,
                                            long llmTimeoutMs,
                                            long toolTimeoutMs,
                                            long heartBeatIntervalMs,
                                            java.util.Map<String, Long> toolTimeoutPolicy,
                                            io.nop.ai.agent.tool.IToolProgressTracker toolProgressTracker,
                                            Executor timeoutExecutor,
                                            IWaitCoordinator waitCoordinator) {
        this.tokenEstimator = tokenEstimator != null ? tokenEstimator : TokenEstimators.defaultEstimator();
        this.completionJudge = completionJudge != null ? completionJudge : NoOpCompletionJudge.noOp();
        this.goalTracker = goalTracker != null ? goalTracker : NoOpGoalTracker.noOp();
        this.sustainer = sustainer != null ? sustainer : NoOpSustainer.noOp();
        this.sessionStore = sessionStore;
        this.checkpointManager = checkpointManager != null
                ? checkpointManager
                : NoOpCheckpoint.noOp();
        this.budgetProvider = budgetProvider != null ? budgetProvider : NoOpBudgetProvider.noOp();
        this.modelRouter = modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough();
        this.modelSwitchedMessageWriter = modelSwitchedMessageWriter != null
                ? modelSwitchedMessageWriter
                : NoOpModelSwitchedMessageWriter.noOp();
        this.usageRecorder = usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp();
        this.denialLedger = denialLedger != null ? denialLedger : new DefaultDenialLedger();
        this.toolCallRepairer = toolCallRepairer != null ? toolCallRepairer : NoOpToolCallRepairer.INSTANCE;
        this.toolAccessChecker = toolAccessChecker;
        this.sandboxBackend = sandboxBackend != null
                ? sandboxBackend
                : io.nop.ai.agent.security.NoOpSandboxBackend.INSTANCE;

        // MA4.2-05 split: composed helper objects own the extracted concerns.
        IHookRegistry hookReg = hookRegistry != null ? hookRegistry : NoOpHookRegistry.INSTANCE;
        this.hookInvoker = new AgentHookInvoker(hookReg, eventPublisher);
        this.toolPlanResolver = new AgentToolPlanResolver(toolManager);
        this.securityConsultation = new AgentSecurityConsultation(
                postDenialGuard != null ? postDenialGuard : new DefaultPostDenialGuard(),
                auditLogger,
                toolAccessChecker,
                permissionProvider,
                pathAccessChecker,
                securityLevelResolver != null
                        ? securityLevelResolver
                        : new DefaultSecurityLevelResolver(),
                permissionMatrix != null
                        ? permissionMatrix
                        : new DefaultPermissionMatrix(),
                approvalGate != null
                        ? approvalGate
                        : new DefaultApprovalGate(),
                this.denialLedger,
                conflictStrategy != null
                        ? conflictStrategy
                        : FailFastStrategy.failFast(),
                writeIntentRegistry != null
                        ? writeIntentRegistry
                        : new InMemoryWriteIntentRegistry(),
                this.toolPlanResolver,
                this.hookInvoker);
        this.compactionCoordinator = new AgentCompactionCoordinator(
                contextCompactor != null ? contextCompactor : NoOpContextCompactor.INSTANCE,
                this.checkpointManager,
                this.sessionStore,
                this.tokenEstimator,
                this.hookInvoker);
        this.promptAssembly = new AgentPromptAssembly(
                talents != null ? talents : List.of(),
                skillProvider != null ? skillProvider : NoOpSkillProvider.noOp(),
                contributionRegistry != null
                        ? contributionRegistry
                        : NoOpContributionRegistry.noOp(),
                contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp(),
                toolManager,
                this.toolPlanResolver);
        this.llmCoordinator = new LlmCallCoordinator(
                chatService,
                retryPolicy != null ? retryPolicy : new StandardRetryPolicy(),
                circuitBreaker != null ? circuitBreaker : new ThresholdBreaker(),
                this.modelRouter,
                llmTimeoutMs,
                timeoutExecutor,
                this.hookInvoker);
        this.loopGuard = new AgentLoopGuard(
                this.denialLedger,
                this.tokenEstimator,
                this.hookInvoker,
                this.compactionCoordinator);
        this.waitCoordinator = waitCoordinator != null ? waitCoordinator : NoOpWaitCoordinator.noOp();
        this.toolDispatcher = new AgentToolDispatcher(
                toolManager,
                engine,
                messenger,
                teamManager,
                teamTaskStore,
                teamAclChecker,
                memoryStoreProvider,
                toolTimeoutMs,
                this.sessionStore,
                this.checkpointManager,
                this.hookInvoker,
                this.toolPlanResolver,
                this.securityConsultation,
                toolTimeoutPolicy);
        // Plan 2285 (WI4): heartbeat slice interval for the tool wait phase
        // (<=0 disables sliced waiting — single deadline-bounded wait).
        this.heartBeatIntervalMs = heartBeatIntervalMs;
        this.toolProgressTracker = toolProgressTracker != null
                ? toolProgressTracker
                : io.nop.ai.agent.tool.NoOpToolProgressTracker.noOp();
        this.checkpointChain = securityConsultation.buildCheckpointChain();
    }

    public static ReActAgentExecutorBuilder builder() {
        return new ReActAgentExecutorBuilder();
    }

    // Test-facing delegation (MA4.2-05 split): the tool-plan computation moved
    // to AgentToolPlanResolver; the executor keeps a package-private facade so
    // existing package-level tests keep compiling and behaving identically.
    List<ChatToolDefinition> buildToolDefinitions(AgentModel agentModel, AgentSession session) {
        return toolPlanResolver.buildToolDefinitions(agentModel, session);
    }

    /**
     * Plan 219 (L4-7): the {@link io.nop.ai.agent.security.ISandboxBackend}
     * wired into this executor. The executor holds the reference and makes
     * it available to tool executors that run inside the ReAct loop
     * (future shell-exec / code-exec IToolExecutor successors). Public so
     * wiring tests can assert the engine → executor reference chain
     * (Minimum Rules #23 Wiring Verification).
     */
    public io.nop.ai.agent.security.ISandboxBackend getSandboxBackend() {
        return sandboxBackend;
    }

    /**
     * Test-support accessor (MA4.2-05 contract fix): the effective (possibly
     * parent-constrained) tool access checker wired into this executor.
     * Package-private, mirrors {@link DefaultAgentEngine#getToolAccessCheckerForTest()};
     * not part of the public contract.
     */
    IToolAccessChecker getToolAccessCheckerForTest() {
        return toolAccessChecker;
    }

    public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {
        AgentModel agentModel = ctx.getAgentModel();

        ctx.setStatus(AgentExecStatus.running);

        String agentName = agentModel != null ? agentModel.getName() : null;
        String sessionId = ctx.getSessionId();

        hookInvoker.publishEvent(AgentEventType.EXECUTION_STARTED, sessionId, agentName,
                Map.of(AgentEventPayloads.AGENT_NAME, agentName != null ? agentName : ""));

        // filtering. When sessionStore is null (testing) or the session is
        // not found, pass null — resolveActiveTags falls back to the agent
        // model's declared activeTags.
        AgentSession agentSession = sessionStore != null && sessionId != null
                ? sessionStore.get(sessionId) : null;

        List<ChatToolDefinition> toolDefs = new ArrayList<>();
        ChatOptions options = promptAssembly.assembleExecutionSetup(ctx, agentModel, agentSession, toolDefs);

        // AR-06 (plan 277): reentryCounters is declared per-iteration (inside
        // the reactLoop iteration body — see runSingleIteration), NOT here. The
        // old per-execute declaration accumulated across all iterations and was
        // never reset, silently starving legitimate re-enter hooks after
        // DEFAULT_MAX_REENTRIES uses.

        // Per-execution mutable state (plan 355 / AI-1 extract-method
        // refactor): the original inline locals (lastModelKey / messageSeq /
        // checkpointSeq / consecutiveContinues / bailCount) are threaded
        // through the extracted phase methods below via this holder. The
        // holder stays a per-execute() local — none of this state is
        // promoted to an executor instance field. Per-field contract
        // comments preserved in ExecutionState.
        ExecutionState state = new ExecutionState();

        // Per-execution disambiguator embedded in checkpoint watermarks so
        // watermarks stay unique across separate execute() calls sharing the
        // same sessionId (e.g. a crash/restart restore re-execution persists
        // to the same DB-backed manager). The seq alone resets to 0 on each
        // execute(), so without this component a restored LLM_TURN(0) would
        // collide with the pre-crash LLM_TURN(0) watermark (plan 187).
        long execStartTime = ctx.getStartTimeMs();

        try {
            if (runPreCallPhase(ctx, sessionId, agentName)) {
                return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
            }

            // sustain-round step. Each sustain CONTINUE extends the budget by
            // this amount (giving the agent another full round of its original
            // iteration budget), so after k sustains the total budget is
            // originalMaxIterations * (1 + k). The sustainCount tracks how many
            // sustain rounds have been granted in this execution; it is passed
            // to the sustainer via SustainContext.sustainCountSoFar so a
            // stateless sustainer can enforce its maxSustainCount ceiling.
            int originalMaxIterations = ctx.getMaxIterations();
            int sustainCount = 0;

            // reactLoop exits naturally (status still running = MAX_ITERATIONS
            // truncation), the engine consults the sustainer. CONTINUE extends
            // the budget and re-enters the reactLoop from the top; STOP (or a
            // terminal status set inside the loop) breaks out to the
            // terminal-state change. See the sustainer field comment + the
            // post-reactLoop consult block for the full adjudication.
            sustainLoop:
            while (true) {
                reactLoop:
                while (ctx.getCurrentIteration() < ctx.getMaxIterations()) {
                    // AI-1 (plan 355): one reactLoop iteration (governance
                    // guards → reasoning → response adjudication → tool
                    // fan-out). Returns false to break the reactLoop (was the
                    // inline break / break reactLoop statements), true to
                    // start the next iteration (was continue / end-of-body).
                    if (!runSingleIteration(ctx, agentModel, sessionId, agentName,
                            options, execStartTime, state)) {
                        break reactLoop;
                    }
                }

            // The reactLoop just exited. If the status is still running, the
            // exit was a MAX_ITERATIONS truncation (the only sustainable exit
            // point in this version) — the iteration budget was exhausted
            // without the completion judge declaring completion, without
            // escalating, and without a force-stop / cancel / pause. Other
            // exit points set a terminal status (completed / escalated /
            // forced_stopped / cancelled / paused) inside the loop and skip
            // this consult entirely. Position rationale: the consult happens
            // BEFORE the post-loop terminal-state change (running → completed)
            // and BEFORE EXECUTION_COMPLETED / POST_CALL event publication,
            // because CONTINUE means the execution is not complete —
            // publishing "completed" then reviving it would corrupt the
            // event/status semantics. A CONTINUE decision skips the
            // terminal-state change + event publication, extends the budget
            // by one sustain-round step (originalMaxIterations), and re-enters
            // the reactLoop from the top. The full top-of-loop check chain
            // (cancel / denial-ledger pause / force-stop / assessGoal) is
            // re-evaluated on every sustain round, so sustaining never
            // bypasses governance. With the shipped NoOpSustainer default
            // onStop unconditionally returns STOP, so this path always falls
            // through to the terminal-state change (zero-regression).
            if (ctx.getStatus() == AgentExecStatus.running) {
                SustainContext sustainCtx = new SustainContext(
                        sessionId,
                        SustainStopReason.MAX_ITERATIONS,
                        ctx.getCurrentIteration(),
                        sustainCount);
                SustainDecision sustainDecision = sustainer.onStop(sustainCtx);
                if (sustainDecision == null) {
                    // Contract defence: sustainer must never return null.
                    throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "sustainer.onStop() returned null for stopReason="
                                    + SustainStopReason.MAX_ITERATIONS
                                    + ", sustainCountSoFar=" + sustainCount);
                }
                if (sustainDecision == SustainDecision.CONTINUE) {
                    int previousMax = ctx.getMaxIterations();
                    ctx.setMaxIterations(previousMax + originalMaxIterations);
                    sustainCount++;
                    LOG.info("Sustainer forced continuation (sustain round {}): "
                                    + "extending maxIterations {} -> {}. session={}",
                            sustainCount, previousMax, ctx.getMaxIterations(), sessionId);
                    continue sustainLoop;
                }
                // STOP: fall through to the terminal-state change.
            }
            break sustainLoop;
            } // end sustainLoop

            adjudicateTerminal(ctx, sessionId, agentName);

        } catch (Exception e) {
            if (ctx.isCancelRequested()) {
                Thread.currentThread().interrupt();
                handleCancellation(ctx, sessionId, agentName);
            } else {
                ctx.setStatus(AgentExecStatus.failed);
                ctx.setLastError(e.toString());

                hookInvoker.invokeOnError(ctx, agentName);
                hookInvoker.publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
            }
        }

        // Plan 2289 (WI8) terminal residual drain: injections arriving during
        // the final LLM call (after the last boundary drain) still reach the
        // result snapshot and the engine's terminal save — never lost
        for (ChatMessage residualMsg : ctx.drainSteering()) {
            ctx.addMessage(residualMsg);
        }

        return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
    }

    /**
     * Plan 211 (L3-3): build the stable tool-call signatures for an iteration
     * from the iteration's requested tool calls (design
     * {@code nop-ai-agent-reliability.md} §5.3). Each signature is
     * {@code toolName:stableArgsString} where {@code stableArgsString} is the
     * args map serialised with sorted keys, so key-order differences across
     * iterations do not produce different signatures. Returns an empty list
     * when the LLM produced no tool calls (the completion-judge branch).
     *
     * <p>Plan 327: the source switched from
     * {@code assistantMsg.getToolCalls()} (legacy folded field) to the
     * {@link ChatToolCallMessage} items extracted from
     * {@code response.getMessages()} (see {@link #extractToolCalls}). The
     * signature computation itself is unchanged.
     */
    private static List<String> buildToolCallSignatures(List<ChatToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        List<String> signatures = new ArrayList<>();
        for (ChatToolCall call : toolCalls) {
            String name = call.getName();
            Map<String, Object> args = call.getArguments();
            String stableArgs = args != null && !args.isEmpty()
                    ? JSON.stringify(new TreeMap<>(args))
                    : "{}";
            signatures.add(name + ":" + stableArgs);
        }
        return signatures;
    }

    /**
     * Plan 327: extract the requested tool calls from a response's canonical
     * message sequence (design conclusion #9). The agent engine reads the
     * discrete {@link ChatToolCallMessage} items from
     * {@code response.getMessages()} — this is the post-migration canonical
     * source, replacing the legacy folded
     * {@code ChatAssistantMessage.getToolCalls()} field. Each
     * {@link ChatToolCallMessage} carries {@code callId/name/arguments}; we
     * reconstruct a {@link ChatToolCall} (setting {@code id = callId}) so the
     * downstream fan-out ({@link AgentToolDispatcher#executeAllowedCalls}) and
     * callId-pairing logic operate unchanged.
     *
     * <p>Returns an empty list when the response carries no tool calls (the
     * completion-judge branch) or when {@code response.getMessages()} is null
     * (defensive: a pre-326 response with only the legacy {@code message}
     * field populated and no {@code messages} sequence is treated as
     * tool-call-free).
     */
    private static List<ChatToolCall> extractToolCalls(io.nop.ai.api.chat.ChatResponse response) {
        List<ChatMessage> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatToolCall> toolCalls = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatToolCallMessage) {
                ChatToolCallMessage tcm = (ChatToolCallMessage) msg;
                ChatToolCall call = new ChatToolCall();
                call.setId(tcm.getCallId());
                call.setName(tcm.getName());
                call.setArguments(tcm.getArguments());
                toolCalls.add(call);
            }
        }
        return toolCalls;
    }

    /**
     * Plan 329：从响应的规范消息序列中取首个 {@link ChatAssistantMessage}。
     * 单一拆分模型下这是唯一的 assistant 文本来源（旧 {@code getMessage()} 已删除）。
     */
    private static ChatAssistantMessage extractAssistantMessage(io.nop.ai.api.chat.ChatResponse response) {
        List<ChatMessage> messages = response.getMessages();
        if (messages != null) {
            for (ChatMessage msg : messages) {
                if (msg instanceof ChatAssistantMessage) {
                    return (ChatAssistantMessage) msg;
                }
            }
        }
        return null;
    }

    /**
     * Plan 329：将响应中的 {@link ChatToolCallMessage} 项追加到上下文（寄居字段已删除，
     * 工具调用请求必须以独立消息进入会话历史）。
     */
    private static void appendToolCallMessages(AgentExecutionContext ctx,
                                               io.nop.ai.api.chat.ChatResponse response) {
        List<ChatMessage> messages = response.getMessages();
        if (messages == null) {
            return;
        }
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatToolCallMessage) {
                ctx.addMessage(msg);
            }
        }
    }

    private void handleCancellation(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.cancelled);
        Map<String, Object> payload = new HashMap<>();
        payload.put(AgentEventPayloads.REASON, ctx.getCancelReason() != null ? ctx.getCancelReason() : "");
        hookInvoker.publishEvent(AgentEventType.SESSION_CANCELLED, sessionId, agentName, payload);
    }

    /**
     * AI-1 (plan 355): extracted from execute() — the PRE_CALL hook phase.
     * Returns true when the middleware vetoed the execution; execute() then
     * short-circuits to a completed result (status + EXECUTION_COMPLETED
     * event with vetoedAt=PRE_CALL), exactly as the original inline code.
     */
    private boolean runPreCallPhase(AgentExecutionContext ctx, String sessionId, String agentName) {
        HookResult preCallResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_CALL, ctx, agentName, null, null);
        if (preCallResult.isVeto()) {
            ctx.setStatus(AgentExecStatus.completed);
            hookInvoker.publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName,
                    Map.of(AgentEventPayloads.VETOED_AT, "PRE_CALL",
                            AgentEventPayloads.REASON, hookInvoker.vetoReason(preCallResult)));
            return true;
        }
        return false;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — one reactLoop iteration
     * (iteration-start governance guards → reasoning turn → response
     * adjudication → tool fan-out). Returns true to start the next iteration
     * (was: continue / end-of-loop-body), false to break the reactLoop (was:
     * break / break reactLoop). Statement order and side effects are
     * unchanged from the original inline loop body.
     */
    private boolean runSingleIteration(AgentExecutionContext ctx, AgentModel agentModel,
                                       String sessionId, String agentName, ChatOptions options,
                                       long execStartTime, ExecutionState state) {
        // AR-06 (plan 277): per-iteration re-entry counter. Reset at
        // the start of each iteration so a long session is not silently
        // starved by a cumulative session-wide cap. Each re-entrant
        // hook point (BEFORE/AFTER_TOOL_RESULT_PROCESSED) has its own
        // independent count within the iteration.
        Map<AgentLifecyclePoint, Integer> reentryCounters = new HashMap<>();

        if (!checkIterationStartGuards(ctx, sessionId, agentName, execStartTime, state.checkpointSeq)) {
            return false;
        }

        IterationFlow flow = runPreReasoningGate(ctx, agentName);
        if (flow != IterationFlow.PROCEED) {
            return flow == IterationFlow.CONTINUE_LOOP;
        }

        RoutedRequest routed = prepareRoutedRequest(ctx, options, sessionId, state);
        ChatRequest request = routed.request();

        // usage recorder can persist the actual call duration. The end
        // time is computed when the UsageRecord is built (after a
        // successful response), so a failed call leaves duration unset.
        //
        // retry loop (design nop-ai-agent-llm-layer.md §7). On a thrown
        // exception the loop classifies the error, builds a
        // RetryContext, and consults retryPolicy: RETRY → sleep the
        // policy-computed backoff then reissue the same request;
        // STOP → rethrow the original error (fail fast); FALLBACK →
        // fail loud (no fallback model chain is wired in this plan —
        // Non-Goal; Minimum Rules #24: no silent skip). With the
        // shipped NoRetryPolicy default the loop runs exactly one
        // attempt and propagates any exception as-is, so the engine's
        // pre-plan-207 zero-retry behaviour is preserved (zero
        // regression). llmCallStart is reset per attempt so the usage
        // recorder captures the duration of the final (successful)
        // attempt only.
        //
        // retry loop's OUTER layer (design nop-ai-agent-reliability.md
        // §3.3 / §5.1). Before entering the retry loop the breaker is
        // asked whether the PRIMARY model (the routedOptions at this
        // point, before any intra-loop FALLBACK switch) may be called.
        // A false return means the circuit is OPEN and the loop fails
        // fast with a NopAiAgentException (no silent skip — Minimum
        // Rules #24). Circuit-breaking and retry are orthogonal: retry
        // handles transient failures within a single call cycle; the
        // breaker handles consecutive-failure patterns that span call
        // cycles, so the check is layered OUTSIDE the retry loop. The
        // check covers only the primary model — a FALLBACK-switched
        // model is intentionally not checked (FALLBACK is itself a
        // response to failure; checking it would add complexity with
        // no clear benefit). With the shipped AlwaysClosed default the
        // check always passes (zero-regression). The primary model key
        // is captured here (before the retry block) because
        // routedOptions can be reassigned inside the loop by a
        // FALLBACK switch.
        //
        // resolveCircuitAware(...) step already guarantees
        // routedOptions is circuit-cleared (it scanned the router's
        // fallback chain for a circuit-allowed model before reaching
        // here). This check therefore now functions as a SAFETY-NET
        // for the rare concurrent-circuit-trip race: a model that was
        // circuit-cleared by the resolution tripping OPEN between the
        // resolution and this check (e.g. a parallel caller's failures
        // pushed the model over threshold). The safety-net preserves
        // fail-fast in that race; under normal single-threaded
        // execution it never rejects (the resolution already selected
        // an allowed model). The cost is one allowCall invocation —
        // negligible. See resolveCircuitAware(...) javadoc.
        LlmCallCoordinator.LlmCallResult llmResult = llmCoordinator.doLlmCallWithRetry(
                request, ctx, sessionId, agentName, routed.routedOptions());

        if (!llmResult.isSuccess()) {
            return false;
        }

        LlmTurn turn = recordLlmTurn(ctx, sessionId, agentName, request, llmResult,
                execStartTime, state.checkpointSeq);

        flow = adjudicateResponse(ctx, sessionId, agentName, turn, state);
        if (flow != IterationFlow.PROCEED) {
            return flow == IterationFlow.CONTINUE_LOOP;
        }

        return runToolFanout(ctx, agentModel, sessionId, agentName, turn.responseToolCalls(),
                execStartTime, state.checkpointSeq, options, state) == IterationFlow.CONTINUE_LOOP;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — the iteration-start
     * governance chain (cancel / denial-ledger pause / WAIT_FOR suspend /
     * force-stop / goal-stuck / compaction trigger). Returns false when the
     * reactLoop must break, true to proceed with this iteration.
     */
    private boolean checkIterationStartGuards(AgentExecutionContext ctx, String sessionId,
                                              String agentName, long execStartTime, int[] checkpointSeq) {
        if (ctx.isCancelRequested()) {
            handleCancellation(ctx, sessionId, agentName);
            return false;
        }

        // Layer 3 denial-ledger pause check (design §6.2): before any
        // further LLM call, verify the session has not been paused by
        // the denial ledger (threshold exceeded during a prior
        // dispatch-path deny). Position rationale: cancelRequested takes
        // the highest priority (user-initiated), pause is checked before
        // shouldForceStop (governance decision before system decision).
        // This is the sole reactLoop-breaking mechanism for the pause
        // state — session A's deny threshold reached last iteration
        // surfaces here on the next iteration start.
        if (denialLedger.isPaused(sessionId)) {
            loopGuard.handleSessionPaused(ctx, sessionId, agentName);
            return false;
        }

        // WAIT_FOR condition check (design §13.1 Decision B/H): the
        // 4th checkpoint producer. checkWait returns NONE (no wait
        // request — zero-regression path for NoOpWaitCoordinator),
        // SUSPEND (condition not yet satisfied — produce WAIT_FOR
        // checkpoint + set waiting status + break), or PROCEED
        // (condition already satisfied via deliverWake or timeout —
        // skip suspend and continue, anti-re-suspend on wake re-entry).
        WaitDecision waitDecision = waitCoordinator.checkWait(sessionId);
        if (waitDecision.getAction() == WaitDecision.Action.SUSPEND) {
            WaitCondition wc = waitDecision.getCondition();
            checkpointManager.saveCheckpoint(Checkpoint.of(
                    sessionId,
                    sessionId != null
                            ? sessionId + ":wait:" + execStartTime + ":" + checkpointSeq[0]
                            : "anon:wait:" + execStartTime + ":" + checkpointSeq[0],
                    checkpointSeq[0],
                    CoreMetrics.currentTimeMillis(),
                    CheckpointType.WAIT_FOR,
                    null,
                    null,
                    null,
                    null,
                    ctx.getMessages().size(),
                    ctx.getTokensUsed(),
                    null,
                    wc.toJsonString()));
            checkpointSeq[0]++;
            if (sessionStore != null && sessionId != null) {
                AgentSession waitSession = sessionStore.get(sessionId);
                if (waitSession != null) {
                    waitSession.replaceMessages(ctx.getMessages());
                    sessionStore.save(waitSession);
                }
            }
            ctx.setStatus(AgentExecStatus.waiting);
            LOG.info("ReAct loop suspended (WAIT_FOR): session={} condition={}",
                    sessionId, wc.getType());
            return false;
        }

        if (loopGuard.shouldForceStop(ctx)) {
            loopGuard.handleForcedStop(ctx, sessionId, agentName, checkpointSeq);
            return false;
        }

        // consulted at the iteration start, after the force-stop
        // (context-overflow) hard guard and before compaction /
        // PRE_REASONING hook (design nop-ai-agent-reliability.md §5.3).
        // Position rationale: (1) force-stop is a context-safety hard
        // guard with higher priority than stuck detection; (2) aborting
        // before the PRE_REASONING hook avoids hook side effects; (3)
        // this sits at the same governance-abort tier as the
        // denial-ledger pause check. A STUCK assessment aborts the loop
        // with status=escalated (no silent skip — Minimum Rules #24).
        // With the shipped NoOpGoalTracker default assessGoal always
        // returns PROGRESSING, so this path is never taken (zero
        // regression).
        GoalAssessment goalAssessment = goalTracker.assessGoal(sessionId);
        if (goalAssessment == GoalAssessment.STUCK) {
            loopGuard.handleGoalStuck(ctx, sessionId, agentName);
            return false;
        }

        if (compactionCoordinator.shouldTriggerCompaction(ctx)) {
            compactionCoordinator.performCompaction(ctx, agentName, checkpointSeq);
        }
        return true;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — PRE_REASONING hook veto +
     * input guardrail gate. Both gate outcomes consume an iteration and
     * re-enter the reactLoop (was: continue).
     */
    private IterationFlow runPreReasoningGate(AgentExecutionContext ctx, String agentName) {
        HookResult preReasoningResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_REASONING, ctx, agentName, null, null);
        if (preReasoningResult.isVeto()) {
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            return IterationFlow.CONTINUE_LOOP;
        }

        GuardrailResult inputGuardrailResult = promptAssembly.checkInputGuardrail(ctx);
        if (inputGuardrailResult.isBlock()) {
            String blockReason = ((GuardrailResult.BlockResult) inputGuardrailResult).getReason();
            // AR-11 (plan 277): inject an assistant text message
            // describing the block instead of an orphan role:"tool"
            // message whose id ("guardrail-block-input") matches no
            // assistant tool_call. At this checkpoint no LLM call has
            // been made this iteration, so there is no assistant
            // tool_call to pair a tool response with — injecting a
            // role:"tool" message would break the tool_call_id pairing
            // invariant and cause an HTTP 400 on the next LLM call.
            ctx.addMessage(new ChatAssistantMessage(
                    "Input blocked by content guardrail: "
                            + (blockReason != null ? blockReason : "unspecified")));
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            return IterationFlow.CONTINUE_LOOP;
        }
        return IterationFlow.PROCEED;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — budget snapshot, model
     * routing, circuit-aware resolution, model-switched audit message, and
     * ChatRequest construction for this iteration's LLM call.
     */
    private RoutedRequest prepareRoutedRequest(AgentExecutionContext ctx, ChatOptions options,
                                               String sessionId, ExecutionState state) {
        ChatOptions routedOptions = resolveRoutedOptions(ctx, options, sessionId, state);
        ChatRequest request = new ChatRequest(new ArrayList<>(ctx.getMessages()));
        request.setOptions(routedOptions);
        return new RoutedRequest(request, routedOptions);
    }

    /**
     * Plan 2285 (WI4): routing core shared by the regular iteration request
     * and wait-phase wakeup requests — budget snapshot, model route,
     * circuit-aware resolution, model-switched audit (skippable: wakeup turns
     * skip the audit write and leave {@code state.lastModelKey} untouched,
     * design nop-ai-agent-async-tool-execution.md §三.3 实现补充裁定).
     */
    private ChatOptions resolveRoutedOptions(AgentExecutionContext ctx, ChatOptions options,
                                             String sessionId, ExecutionState state) {
        return resolveRoutedOptions(ctx, options, sessionId, state, true);
    }

    private ChatOptions resolveRoutedOptions(AgentExecutionContext ctx, ChatOptions options,
                                             String sessionId, ExecutionState state,
                                             boolean writeModelSwitchAudit) {
        // before routing so a functional IModelRouter can read
        // ctx.getBudgetSnapshot() and downgrade the model on budget
        // exhaustion (design nop-ai-agent-usage-and-billing.md §3.6).
        // Position rationale: this is immediately before route() AND
        // after the previous iteration's token/cost accumulation
        // (tokens are accumulated at the end of each iteration after
        // the LLM responds), so the snapshot reflects all usage up to
        // this routing decision. With the shipped NoOpBudgetProvider
        // default the snapshot is always an unlimited pass-through
        // (exceeded=false), so a functional router is the only
        // consumer — combined with PassThroughModelRouter the shipped
        // behaviour is zero-change. The provider must return a non-null
        // snapshot (IBudgetProvider contract); null-defence is the
        // fail-loud guard against a broken provider.
        BudgetSnapshot snapshot = budgetProvider.getBudget(ctx);
        if (snapshot == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "budgetProvider.getBudget() returned null: provider=" + budgetProvider.getClass().getName());
        }
        ctx.setBudgetSnapshot(snapshot);

        RoutingResult routingResult = modelRouter.route(ctx.getMessages(), options, ctx);
        ChatOptions routedOptions = routingResult.getOptions();

        // against the circuit breaker BEFORE the model-switched audit
        // detection below. This upgrades the engine's handling of a
        // circuit-OPEN primary model from "reject → terminate the
        // whole agent execution" (plan 210) to "reject → proactively
        // scan the router's fallback chain for a circuit-allowed model
        // → switch routedOptions and continue" (design
        // nop-ai-agent-reliability.md §3.3 / §5.2). With the shipped
        // AlwaysClosed default allowCall always returns true, so the
        // resolution is a zero-overhead pass-through (zero-regression).
        // Positioning BEFORE the model-switched detection (plan 205,
        // role=80) is deliberate: the resolution may change
        // routedOptions, so the detection must observe the
        // post-resolution final model to correctly emit the audit
        // message. See resolveCircuitAware(...) javadoc for the full
        // algorithm. The routingReason is intentionally NOT mutated
        // (RoutingResult is an immutable value object); the
        // circuit-induced switch is recorded via LOG.warn (inside the
        // resolver) and naturally reflected in the model-switched
        // audit message's fromModel/toModel below.
        routedOptions = llmCoordinator.resolveCircuitAware(
                routedOptions, sessionId);

        if (writeModelSwitchAudit) {
            // persist a model-switched audit message (role=80) when the
            // routed model differs from the previous iteration's model
            // (design nop-ai-agent-usage-and-billing.md §3.5). The message
            // is an audit record persisted to nop_ai_session_message — it is
            // NOT added to ctx.getMessages() and therefore never injected
            // into the LLM reasoning context.
            String currentModelKey = ModelKeys.buildModelKey(routedOptions);
            if (state.lastModelKey != null && !currentModelKey.equals(state.lastModelKey)
                    && sessionId != null) {
                state.messageSeq[0]++;
                modelSwitchedMessageWriter.writeModelSwitched(
                        sessionId, state.lastModelKey, currentModelKey,
                        routingResult.getRoutingReason(),
                        routingResult.getComplexity(),
                        state.messageSeq[0]);
            }
            state.lastModelKey = currentModelKey;
        }

        return routedOptions;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — post-call bookkeeping for
     * a successful LLM turn: response messages appended to ctx, usage
     * accounting, LLM_TURN checkpoint + persisted-session sync,
     * LLM_RESPONSE_RECEIVED event, POST_REASONING hook. Returns the values
     * the response-adjudication phase consumes.
     */
    private LlmTurn recordLlmTurn(AgentExecutionContext ctx, String sessionId, String agentName,
                                  ChatRequest request, LlmCallCoordinator.LlmCallResult llmResult,
                                  long execStartTime, int[] checkpointSeq) {
        // a FALLBACK switch inside the retry loop may have replaced the
        // routed options — the usage record below must observe the final
        // (post-FALLBACK) options (the original inline code reassigned the
        // routedOptions local from llmResult at this point).
        ChatOptions routedOptions = llmResult.routedOptions;

        ChatAssistantMessage assistantMsg = extractAssistantMessage(llmResult.response);
        if (assistantMsg == null) {
            // P2 (tool-call-only responses, real provider form): the response
            // carries only ChatToolCallMessage items. Record an empty
            // assistant message so the checkpoint summary, output guardrail
            // and completion judge all receive a non-null message (no NPE);
            // the tool-call messages themselves are appended below via
            // appendToolCallMessages. Observable: logged, not silently
            // swallowed (Minimum Rules #24).
            LOG.info("LLM response carried no ChatAssistantMessage (tool-call-only); recording empty assistant message. session={}", sessionId);
            assistantMsg = new ChatAssistantMessage("");
        }
        // Plan 327: tool calls are extracted from the canonical
        // response.getMessages() sequence (ChatToolCallMessage items),
        // replacing the legacy assistantMsg.getToolCalls() folded field.
        List<ChatToolCall> responseToolCalls = extractToolCalls(llmResult.response);
        ctx.addMessage(assistantMsg);
        // Plan 329: 工具调用请求以独立 ChatToolCallMessage 承载（寄居字段已删除）。
        // 将其追加到上下文，使会话历史完整携带 assistant 的工具调用（供下一轮请求构建
        // 与 tool_call_id 配对校验），与设计 §3.2「上下文.append(response.messages)」一致。
        appendToolCallMessages(ctx, llmResult.response);

        if (llmResult.response.getUsage() != null) {
            recordLlmTurnUsage(ctx, sessionId, agentName, request, routedOptions, llmResult);
        }

        saveLlmTurnCheckpoint(ctx, sessionId, execStartTime, checkpointSeq, assistantMsg);

        Map<String, Object> llmPayload = new HashMap<>();
        llmPayload.put(AgentEventPayloads.ITERATION, ctx.getCurrentIteration());
        llmPayload.put(AgentEventPayloads.HAS_TOOL_CALLS, !responseToolCalls.isEmpty());
        hookInvoker.publishEvent(AgentEventType.LLM_RESPONSE_RECEIVED, sessionId, agentName, llmPayload);

        HookResult postReasoningResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.POST_REASONING, ctx, agentName, null, null);

        return new LlmTurn(assistantMsg, responseToolCalls, postReasoningResult);
    }

    /**
     * AI-1 (plan 355): extracted from execute() — token/cost accounting and
     * usage recording for a successful LLM turn.
     */
    private void recordLlmTurnUsage(AgentExecutionContext ctx, String sessionId, String agentName,
                                    ChatRequest request, ChatOptions routedOptions,
                                    LlmCallCoordinator.LlmCallResult llmResult) {
        int promptTokens = llmResult.response.getPromptTokens() != null
                ? llmResult.response.getPromptTokens() : 0;
        int completionTokens = llmResult.response.getCompletionTokens() != null
                ? llmResult.response.getCompletionTokens() : 0;
        ctx.setTokensUsed(ctx.getTokensUsed() + promptTokens + completionTokens);

        UsageRecord usageRecord = new UsageRecord();
        usageRecord.setSessionId(sessionId);
        usageRecord.setAgentName(agentName);
        usageRecord.setRequestId(llmResult.response.getRequestId());
        usageRecord.setAiProvider(routedOptions.getProvider());
        usageRecord.setAiModel(routedOptions.getModel());
        usageRecord.setPromptTokens(promptTokens);
        usageRecord.setCompletionTokens(completionTokens);
        usageRecord.setResponseDurationMs(CoreMetrics.currentTimeMillis() - llmResult.llmCallStart);
        usageRecord.setResponseTimestamp(CoreMetrics.currentTimeMillis());
        usageRecorder.record(usageRecord);

        if (promptTokens > 0) {
            tokenEstimator.record(request.getMessages(), promptTokens);
        }
    }

    /**
     * AI-1 (plan 355): extracted from execute() — the LLM_TURN checkpoint
     * ("each LLM turn completes" trigger point) + persisted-session message
     * sync that upholds the restore invariant.
     */
    private void saveLlmTurnCheckpoint(AgentExecutionContext ctx, String sessionId,
                                       long execStartTime, int[] checkpointSeq,
                                       ChatAssistantMessage assistantMsg) {
        // each LLM turn completes" trigger point): now that the
        // assistant response has been added to the context and token
        // accounting is done, record an LLM_TURN checkpoint. This
        // provides a finer-grained recovery point than TOOL_EXECUTION
        // — a crash after the LLM responds but before a tool executes
        // resumes from this turn instead of the previous tool call.
        // Emitted before the completion judge and the output guardrail
        // so the checkpoint captures the original LLM response for
        // every successful turn regardless of the judge/guardrail
        // outcome. With the shipped NoOpCheckpoint default this is a
        // no-op.
        String llmOutputSummary = assistantMsg.getContent() != null ? assistantMsg.getContent() : "";
        llmOutputSummary = ToolResultTruncator.truncateIfAllowed(
                llmOutputSummary,
                ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                null);
        checkpointManager.saveCheckpoint(Checkpoint.of(
                sessionId,
                sessionId != null
                        ? sessionId + ":llm:" + execStartTime + ":" + checkpointSeq[0]
                        : "anon:llm:" + execStartTime + ":" + checkpointSeq[0],
                checkpointSeq[0],
                CoreMetrics.currentTimeMillis(),
                CheckpointType.LLM_TURN,
                null,
                null,
                null,
                llmOutputSummary,
                ctx.getMessages().size(),
                ctx.getTokensUsed()));
        checkpointSeq[0]++;

        // TOOL_EXECUTION pattern): after the LLM_TURN checkpoint is
        // written, synchronize the persisted session's message list so
        // the restore invariant checkpoint.messageCount <=
        // session.messageCount holds for LLM_TURN checkpoints too.
        if (sessionStore != null) {
            AgentSession persistedLlm = sessionStore.get(sessionId);
            if (persistedLlm != null) {
                persistedLlm.replaceMessages(ctx.getMessages());
                sessionStore.save(persistedLlm);
            }
        }
    }

    /**
     * AI-1 (plan 355): extracted from execute() — response adjudication:
     * POST_REASONING bail handling (W5-3), output guardrail, goal-tracker
     * iteration recording, and the completion-judge branch. PROCEED means
     * tool calls are present and the fan-out phase runs.
     */
    private IterationFlow adjudicateResponse(AgentExecutionContext ctx, String sessionId,
                                             String agentName, LlmTurn turn, ExecutionState state) {
        // W5-3 (BAIL): POST_REASONING middleware returned BailResult →
        // discard this round's response (skip checkOutputGuardrail, skip
        // tool_calls execution, not treated as final answer) + re-prompt.
        // BailResult is only valid at POST points; AgentHookInvoker enforces
        // fail-loud for non-POST points. Per-request bail cap fails loud
        // before maxIterations is exhausted (design §5.4 裁定 A/B/D).
        if (turn.postReasoningResult().isBail()) {
            String bailReason = ((HookResult.BailResult) turn.postReasoningResult()).getReason();
            state.bailCount++;
            if (state.bailCount > MAX_POST_REASONING_BAILS) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "POST_REASONING middleware bail cap (" + MAX_POST_REASONING_BAILS
                                + ") exceeded; last bail reason: " + bailReason);
            }
            LOG.warn("POST_REASONING middleware bailed (count={}/{}, reason={}); "
                            + "discarding response and re-prompting. session={}",
                    state.bailCount, MAX_POST_REASONING_BAILS, bailReason, sessionId);
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            return IterationFlow.CONTINUE_LOOP;
        }

        if (promptAssembly.checkOutputGuardrail(ctx, turn.assistantMsg(), turn.responseToolCalls())) {
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            return IterationFlow.CONTINUE_LOOP;
        }

        // goal tracker. Called once per iteration after the LLM
        // response is finalised (assistantMsg built + output guardrail
        // applied) and before the tool-dispatch / completion-judge
        // branch (design nop-ai-agent-reliability.md §5.3). This is the
        // single call site covering both branches: the engine extracts
        // the request-level tool-call signatures from the
        // responseToolCalls extracted via ChatToolCallMessage (empty
        // when the LLM produced no tool calls — the completion-judge
        // branch). With the shipped NoOpGoalTracker default
        // recordIteration is an explicit no-op, so this is
        // zero-regression.
        goalTracker.recordIteration(sessionId,
                new IterationSnapshot(ctx.getCurrentIteration(),
                        buildToolCallSignatures(turn.responseToolCalls())));

        if (turn.responseToolCalls().isEmpty()) {
            CompletionDecision decision = completionJudge.decide(turn.assistantMsg(), ctx);

            if (decision.isComplete()) {
                ctx.setStatus(AgentExecStatus.completed);
                return IterationFlow.BREAK_LOOP;
            }

            if (decision.isContinue()) {
                if (state.consecutiveContinues >= DEFAULT_MAX_COMPLETION_CONTINUES) {
                    LOG.warn("Completion-judge dead-loop protection: {} consecutive Continue decisions, force-exiting loop. session={}",
                            DEFAULT_MAX_COMPLETION_CONTINUES, sessionId);
                    ctx.setStatus(AgentExecStatus.completed);
                    return IterationFlow.BREAK_LOOP;
                }
                String continuationMessage = ((CompletionDecision.Continue) decision).getMessage();
                ctx.addMessage(new ChatUserMessage(
                        continuationMessage != null ? continuationMessage : ""));
                state.consecutiveContinues++;
                ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                return IterationFlow.CONTINUE_LOOP;
            }

            if (decision.isEscalate()) {
                String reason = ((CompletionDecision.Escalate) decision).getReason();
                ctx.setStatus(AgentExecStatus.escalated);
                ctx.setLastError(reason);
                ctx.getMetadata().put("completion.escalateReason",
                        reason != null ? reason : "");
                state.consecutiveContinues = 0;
                return IterationFlow.BREAK_LOOP;
            }

            ctx.setStatus(AgentExecStatus.completed);
            return IterationFlow.BREAK_LOOP;
        }

        state.consecutiveContinues = 0;
        return IterationFlow.PROCEED;
    }

    // ---- Plan 2285 (WI4): heartbeat wait phase (design nop-ai-agent-async-tool-execution.md §三.1/§三.3/§三.6) ----

    /** Heartbeat wakeup decision protocol (design §三.3 响应能力): reply text tokens. */
    private static final String WAKEUP_DECISION_WAIT = "WAIT";
    private static final String WAKEUP_DECISION_ABANDON = "ABANDON";

    /**
     * The tool wait phase with heartbeat wakeup turns: sliced waiting on the
     * tool-phase session; each expiry triggers a transient wakeup LLM request
     * (ctx snapshot + placeholders + heartbeat evidence) whose assistant
     * reply adjudicates continue/abandon/parallel. Wakeup turns are NOT
     * iterations: no iteration budget, no ISustainer, no LLM_TURN checkpoint,
     * no session-level lifecycle hooks, no EXECUTION_COMPLETED — only the
     * content guardrail fast path applies (design §三.3 语义表 + 实现补充裁定).
     */
    private void runHeartbeatToolPhase(AgentExecutionContext ctx, String sessionId, String agentName,
                                       List<ChatToolCall> allowedCalls, AgentToolExecuteContext toolExecCtx,
                                       long execStartTime, int[] checkpointSeq,
                                       ChatOptions options, ExecutionState state) {
        AgentToolDispatcher.ToolPhaseSession session = toolDispatcher.startToolPhase(
                ctx, agentName, sessionId, allowedCalls, toolExecCtx, execStartTime, checkpointSeq);
        Set<String> executingStarted = new HashSet<>();
        while (true) {
            AgentToolDispatcher.PhaseWaitEvent event = session.awaitWakeup(
                    nextWaitSlice(session),
                    () -> ctx.hasQueuedInjection(InjectionSemantics.STEER));
            if (event == AgentToolDispatcher.PhaseWaitEvent.COMPLETED) {
                break;
            }
            // wakeup-turn gate set (design §三.3): cancel + pause + forceStop.
            // No WAIT_FOR/assessGoal/compaction — no iteration semantics here.
            if (ctx.isCancelRequested()) {
                break;
            }
            if (ctx.getStatus() == AgentExecStatus.paused) {
                break;
            }
            if (loopGuard.shouldForceStop(ctx)) {
                loopGuard.handleForcedStop(ctx, sessionId, agentName, checkpointSeq);
                break;
            }

            if (event == AgentToolDispatcher.PhaseWaitEvent.STEERING_PENDING) {
                boolean consumed = runSteeringWakeupTurn(ctx, sessionId, agentName, session,
                        toolExecCtx, execStartTime, checkpointSeq, options, state);
                if (!consumed) {
                    // failed steering turn: throttle the next probe by one
                    // full slice (circuit-open + queued steering must not
                    // hot-loop the fail-soft path)
                    session.awaitCompletionSlice(nextWaitSlice(session));
                }
            } else {
                runHeartbeatWakeupTurn(ctx, sessionId, agentName, session, toolExecCtx,
                        execStartTime, checkpointSeq, options, state);
            }

            // Plan 2287 (WI6): EXECUTING phase via polling (first sight of an
            // in-flight sandbox execution per callId, executor thread, at most
            // one EXECUTING checkpoint per call; NoOp tracker → dormant).
            // The facade path (heartbeat disabled) does not poll — DISPATCHED
            // already covers its conservative ruling.
            for (String callId : session.pendingCallIds()) {
                if (executingStarted.contains(callId)) {
                    continue;
                }
                if (toolProgressTracker.progressSnapshot(callId).isInFlight()) {
                    executingStarted.add(callId);
                    ChatToolCall call = session.callByCallId(callId);
                    toolDispatcherSaveExecuting(ctx, sessionId, execStartTime, checkpointSeq,
                            callId, call != null ? call.getName() : "unknown");
                }
            }
        }
        session.commitResults();
    }

    /**
     * Plan 2286 (WI5 Phase A): steering wakeup turn — the model is idle
     * (blocked on tools, no in-flight request), so queued steering input is
     * processed through a transient request carrying placeholders for the
     * running tools, then the wait continues; late tool results still commit
     * in-place (WI3 invariant 2).
     *
     * <p>Queue discipline (plan 2286 review P1): the request is built from a
     * NON-destructive snapshot of the steering queue (CLQ weakly-consistent
     * toArray); the destructive drain-commit (messages into ctx, in enqueue
     * order, BEFORE the assistant reply) runs only after a SUCCESSFUL LLM
     * call. Failure paths (fail-soft / guardrail-less early return) leave the
     * steering in the queue for the next probe — no re-enqueue reorder risk.
     * The guardrail-block branch is the exception: the reply is discarded but
     * the steering messages MUST enter history (real user input), so that
     * branch drains and commits the messages alone, adjudicating as WAIT.
     * The reply adjudicates with the same three-option protocol as the
     * heartbeat turn (WAIT / ABANDON / tool_calls = parallel work).
     */
    private boolean runSteeringWakeupTurn(AgentExecutionContext ctx, String sessionId, String agentName,
                                          AgentToolDispatcher.ToolPhaseSession session,
                                          AgentToolExecuteContext toolExecCtx,
                                          long execStartTime, int[] checkpointSeq,
                                          ChatOptions options, ExecutionState state) {
        List<String> pendingCallIds = session.pendingCallIds();
        if (pendingCallIds.isEmpty()) {
            // all futures completed in the race window — the steering stays
            // queued for the boundary drain (loss-free fallback chain)
            return false;
        }

        // non-destructive snapshot: STEER entries only — FOLLOWUP/INJECT stay
        // queued for their round-boundary consumer (plan 2289 consumption rules)
        List<InjectionEntry> steerEntries = ctx.snapshotSteeringBySemantics(InjectionSemantics.STEER);
        if (steerEntries.isEmpty()) {
            return false;
        }
        List<ChatMessage> steeringSnapshot = new ArrayList<>();
        for (InjectionEntry entry : steerEntries) {
            steeringSnapshot.add(entry.getMessage());
        }

        // transient request copy: placeholders + steering messages (copy only)
        List<ChatMessage> requestMessages = new ArrayList<>(ctx.getMessages());
        for (String callId : pendingCallIds) {
            ChatToolCall call = session.callByCallId(callId);
            if (call == null) {
                continue;
            }
            ChatToolResponseMessage placeholder = ToolCallPlaceholderRenderer.renderPlaceholder(
                    new ChatToolCallMessage(callId, call.getName(), java.util.Map.of()));
            AgentToolDispatcher.insertToolResultInList(requestMessages, placeholder);
        }
        requestMessages.addAll(steeringSnapshot);

        ChatOptions routedOptions = resolveRoutedOptions(ctx, options, sessionId, state, false);
        ChatRequest wakeupRequest = new ChatRequest(requestMessages);
        wakeupRequest.setOptions(routedOptions);

        LlmCallCoordinator.LlmCallResult llmResult = llmCoordinator.doWakeupCallWithRetry(
                wakeupRequest, ctx, sessionId, agentName, routedOptions);
        if (llmResult == null || !llmResult.isSuccess()) {
            // fail-soft: steering stays queued; the caller throttles the next
            // probe by one slice so a queued steering + open circuit cannot
            // hot-loop the wakeup path (closure-audit edge-case finding)
            return false;
        }

        ChatAssistantMessage assistantMsg = extractAssistantMessage(llmResult.response);
        List<ChatToolCall> derivedCalls = extractToolCalls(llmResult.response);

        if (llmResult.response.getUsage() != null) {
            recordLlmTurnUsage(ctx, sessionId, agentName, wakeupRequest, llmResult.routedOptions, llmResult);
        }

        // content guardrail fast path — exception branch: discard the reply
        // but commit the steering messages (real user input must reach
        // history); adjudicated as WAIT
        if (assistantMsg != null && promptAssembly.checkOutputGuardrailBlockedForWakeup(ctx, assistantMsg)) {
            LOG.warn("steering wakeup reply blocked by content guardrail; committing steering messages "
                    + "and adjudicating as WAIT. session={}", sessionId);
            drainCommitSteering(ctx, sessionId);
            return true;
        }

        // drain-commit: steering messages first, reply after (session order)
        drainCommitSteering(ctx, sessionId);
        if (assistantMsg != null) {
            ctx.addMessage(assistantMsg);
        }
        appendToolCallMessages(ctx, llmResult.response);

        adjudicateHeartbeatReply(ctx, sessionId, agentName, session, toolExecCtx,
                execStartTime, checkpointSeq, derivedCalls, assistantMsg);
        return true;
    }

    /**
     * Destructive drain of the steering queue into ctx (enqueue order).
     * Single-consumer discipline: only the ReAct thread polls the queue
     * (boundary drain + wakeup turns).
     */
    private void drainCommitSteering(AgentExecutionContext ctx, String sessionId) {
        for (ChatMessage msg : ctx.drainSteering()) {
            ctx.addMessage(msg);
        }
        // Plan 2289 (WI8): persist immediately after commit — closes the
        // "drained but not checkpointed" crash window for committed injections
        persistInjectedMessages(ctx, sessionId);
    }

    /**
     * Plan 2289 (WI8): syncs the ctx message list into the persisted session
     * (same two-step shape as the LLM_TURN sync, without the checkpoint).
     * Single-writer safe: runs on the executor worker thread only.
     */
    private void persistInjectedMessages(AgentExecutionContext ctx, String sessionId) {
        if (sessionId == null || sessionStore == null) {
            return;
        }
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            return;
        }
        session.replaceMessages(ctx.getMessages());
        sessionStore.save(session);
    }

    /**
     * Plan 2287 (WI6): bridge to the dispatcher's phase-checkpoint writer for
     * the EXECUTING polling path (same thread, same checkpointSeq discipline).
     */
    private void toolDispatcherSaveExecuting(AgentExecutionContext ctx, String sessionId,
                                             long execStartTime, int[] checkpointSeq,
                                             String callId, String toolName) {
        toolDispatcher.saveExecutingPhaseCheckpoint(ctx, sessionId, execStartTime,
                checkpointSeq, callId, toolName);
    }

    /**
     * Slice budget: never sleep past a mechanical cap (the cap expiry
     * completes a future exceptionally and shrinks the pending set).
     */
    private long nextWaitSlice(AgentToolDispatcher.ToolPhaseSession session) {
        long remainingCap = session.earliestDeadlineRemainingMs();
        long slice = heartBeatIntervalMs;
        if (remainingCap < slice) {
            slice = remainingCap;
        }
        return slice;
    }

    /**
     * One heartbeat wakeup turn: build the transient request (ctx snapshot +
     * per-own-callId anchored placeholders + heartbeat evidence message),
     * call the LLM via the fail-soft wakeup entry, apply the content
     * guardrail fast path, commit the heartbeat message and the assistant
     * reply to ctx (heartbeat first, reply after — session order), record
     * usage, and adjudicate the reply (WAIT / ABANDON [callId] / tool_calls
     * = parallel work).
     */
    private void runHeartbeatWakeupTurn(AgentExecutionContext ctx, String sessionId, String agentName,
                                        AgentToolDispatcher.ToolPhaseSession session,
                                        AgentToolExecuteContext toolExecCtx,
                                        long execStartTime, int[] checkpointSeq,
                                        ChatOptions options, ExecutionState state) {
        List<String> pendingCallIds = session.pendingCallIds();
        if (pendingCallIds.isEmpty()) {
            return;
        }

        // transient request copy: placeholders are inserted into the COPY
        // only (WI3 invariant 1 — derived state never enters session history)
        List<ChatMessage> requestMessages = new ArrayList<>(ctx.getMessages());
        for (String callId : pendingCallIds) {
            ChatToolCall call = session.callByCallId(callId);
            if (call == null) {
                continue;
            }
            ChatToolResponseMessage placeholder = ToolCallPlaceholderRenderer.renderPlaceholder(
                    new ChatToolCallMessage(callId, call.getName(), java.util.Map.of()));
            AgentToolDispatcher.insertToolResultInList(requestMessages, placeholder);
        }
        ChatUserMessage heartbeatMessage = buildHeartbeatMessage(ctx, sessionId, agentName, session);
        requestMessages.add(heartbeatMessage);

        ChatOptions routedOptions = resolveRoutedOptions(ctx, options, sessionId, state, false);
        ChatRequest wakeupRequest = new ChatRequest(requestMessages);
        wakeupRequest.setOptions(routedOptions);

        LlmCallCoordinator.LlmCallResult llmResult = llmCoordinator.doWakeupCallWithRetry(
                wakeupRequest, ctx, sessionId, agentName, routedOptions);
        if (llmResult == null || !llmResult.isSuccess()) {
            // fail-soft (design §三.3 实现补充裁定): stay in the wait phase;
            // the next slice retries the wakeup
            return;
        }

        ChatAssistantMessage assistantMsg = extractAssistantMessage(llmResult.response);
        List<ChatToolCall> derivedCalls = extractToolCalls(llmResult.response);

        // usage first: the tokens were consumed whether or not the reply
        // survives the guardrail check (closure-audit note a)
        if (llmResult.response.getUsage() != null) {
            recordLlmTurnUsage(ctx, sessionId, agentName, wakeupRequest, llmResult.routedOptions, llmResult);
        }

        // content guardrail fast path (design §三.3: checkOutputGuardrail
        // mechanism applies to wakeup replies; BAIL → discard the reply,
        // adjudicate as WAIT). The reply is NOT yet in ctx — a block simply
        // drops it (nothing to unwind), which preserves the pairing
        // invariant trivially.
        if (assistantMsg != null && promptAssembly.checkOutputGuardrailBlockedForWakeup(ctx, assistantMsg)) {
            LOG.warn("heartbeat wakeup reply blocked by content guardrail; adjudicated as WAIT. session={}", sessionId);
            ctx.addMessage(heartbeatMessage);
            return;
        }

        // heartbeat message first, reply after (session order, design §三.3)
        ctx.addMessage(heartbeatMessage);
        if (assistantMsg != null) {
            ctx.addMessage(assistantMsg);
        }
        // parallel-work derived calls: append their tool_call messages to ctx
        // (same discipline as the regular turn — appendToolCallMessages before
        // dispatch — so deny results pair up and committed results anchor
        // in-place by their own callId)
        appendToolCallMessages(ctx, llmResult.response);

        adjudicateHeartbeatReply(ctx, sessionId, agentName, session, toolExecCtx,
                execStartTime, checkpointSeq, derivedCalls, assistantMsg);
    }

    /**
     * Adjudicates the wakeup reply: tool_calls = parallel work (dispatched
     * through the full security chain, joined into the pending set);
     * "ABANDON [callId]" = abandon (one or all) with synthetic cancel
     * results; anything else = keep waiting.
     */
    private void adjudicateHeartbeatReply(AgentExecutionContext ctx, String sessionId, String agentName,
                                          AgentToolDispatcher.ToolPhaseSession session,
                                          AgentToolExecuteContext toolExecCtx,
                                          long execStartTime, int[] checkpointSeq,
                                          List<ChatToolCall> derivedCalls,
                                          ChatAssistantMessage assistantMsg) {
        if (derivedCalls != null && !derivedCalls.isEmpty()) {
            // parallel independent work (design §三.3 响应能力): full security
            // chain per call — no wakeup-turn exemption
            List<ChatToolCall> allowed = new ArrayList<>();
            String fingerprintWorkDir = securityConsultation.resolveWorkDirString(ctx.getAgentModel());
            for (ChatToolCall call : derivedCalls) {
                SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                        sessionId, agentName, call, ctx, fingerprintWorkDir, ctx.getAgentModel());
                SecurityCheckpoint.Decision decision = checkpointChain.evaluate(checkCtx);
                if (decision == SecurityCheckpoint.Decision.DENY_AND_BREAK) {
                    break;
                }
                if (decision == SecurityCheckpoint.Decision.DENY) {
                    continue;
                }
                allowed.add(call);
            }
            if (!allowed.isEmpty()) {
                toolDispatcher.addCallsToPhase(session, ctx, agentName, sessionId,
                        execStartTime, checkpointSeq, allowed, toolExecCtx);
                LOG.info("heartbeat wakeup spawned {} parallel tool call(s). session={}", allowed.size(), sessionId);
            }
            return;
        }

        String text = assistantMsg != null && assistantMsg.getContent() != null
                ? assistantMsg.getContent().trim() : "";
        if (text.regionMatches(true, 0, WAKEUP_DECISION_ABANDON, 0, WAKEUP_DECISION_ABANDON.length())) {
            String rest = text.substring(WAKEUP_DECISION_ABANDON.length()).trim();
            List<String> targets = new ArrayList<>();
            if (rest.isEmpty()) {
                targets.addAll(session.pendingCallIds());
            } else {
                targets.add(rest.split("\\s+")[0]);
            }
            for (String callId : targets) {
                ChatToolCall call = session.callByCallId(callId);
                ChatToolResponseMessage cancelResult = ChatToolResponseMessage.error(callId,
                        call != null ? call.getName() : "unknown",
                        "abandoned by model heartbeat decision before completion");
                // process-level kill channel first (no-op when no tracker is
                // wired); future cancel + synthetic result follow in abandon()
                toolProgressTracker.cancelExecution(callId);
                if (session.abandon(callId, cancelResult)) {
                    LOG.info("heartbeat abandon: callId={} cancelled with synthetic result. session={}",
                            callId, sessionId);
                }
            }
            return;
        }
        // WAIT (explicit token or any other reply): fall through — the wait
        // loop's next slice continues
    }

    /**
     * Heartbeat evidence message (ChatUserMessage, design §三.3): running
     * tool list + elapsed per call + sandbox incremental output tail — the
     * progress evidence the model needs to distinguish slow from hung.
     */
    private ChatUserMessage buildHeartbeatMessage(AgentExecutionContext ctx, String sessionId,
                                                  String agentName,
                                                  AgentToolDispatcher.ToolPhaseSession session) {
        StringBuilder text = new StringBuilder();
        text.append("[heartbeat] Tools still running (").append(session.pendingCallIds().size())
                .append("). For each: elapsed ms and latest output tail. ")
                .append("Reply ").append(WAKEUP_DECISION_WAIT).append(" to keep waiting, ")
                .append(WAKEUP_DECISION_ABANDON).append(" <callId> to abandon one (or ")
                .append(WAKEUP_DECISION_ABANDON).append(" for all), or issue other tool calls ")
                .append("to do independent work in parallel.\n");
        for (String callId : session.pendingCallIds()) {
            ChatToolCall call = session.callByCallId(callId);
            io.nop.ai.agent.tool.ToolProgressSnapshot snapshot = toolProgressTracker.progressSnapshot(callId);
            text.append("- callId=").append(callId)
                    .append(" tool=").append(call != null ? call.getName() : "unknown")
                    .append(" elapsedMs=").append(session.elapsedMs(callId))
                    .append(" inFlight=").append(snapshot.isInFlight())
                    .append(" outputBytes=").append(snapshot.getOutputBytes())
                    .append("\n");
            if (!snapshot.getOutputTail().isEmpty()) {
                text.append("  tail: ").append(snapshot.getOutputTail().replace("\n", " | ")).append("\n");
            }
        }
        return new ChatUserMessage(text.toString());
    }

    /**
     * AI-1 (plan 355): extracted from execute() — the tool fan-out phase:
     * dispatch-context preparation, the security-checkpoint dispatch loop,
     * allowed-calls execution, post-dispatch cancel check, steering drain,
     * and the end-of-iteration increment.
     */
    private IterationFlow runToolFanout(AgentExecutionContext ctx, AgentModel agentModel,
                                        String sessionId, String agentName,
                                        List<ChatToolCall> responseToolCalls,
                                        long execStartTime, int[] checkpointSeq,
                                        ChatOptions options, ExecutionState state) {
        // the provider (when wired). When the provider is null
        // (executor constructed outside the engine for testing, or
        // explicitly opted out), the store stays null and memory tools
        // fail fast at execution time with a descriptive error.
        AgentToolExecuteContext toolExecCtx = toolDispatcher.prepareDispatchContext(ctx, agentModel, sessionId, agentName);
        String fingerprintWorkDir = securityConsultation.resolveWorkDirString(agentModel);

        List<ChatToolCall> allowedCalls = new ArrayList<>();

        dispatchLoop:
        for (ChatToolCall chatToolCall : responseToolCalls) {
            chatToolCall = toolCallRepairer.repair(chatToolCall, ctx);

            String toolName = chatToolCall.getName();

            hookInvoker.publishEvent(AgentEventType.TOOL_CALL_STARTED, sessionId, agentName,
                    Map.of(AgentEventPayloads.TOOL_NAME, toolName,
                            AgentEventPayloads.ITERATION, ctx.getCurrentIteration()));

            // Each checkpoint implements one deny path from the original
            // inline if-else chain. The chain replaces all 7 deny paths
            // and their associated audit/event/error-response boilerplate.
            SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                    sessionId, agentName, chatToolCall, ctx, fingerprintWorkDir, agentModel);
            SecurityCheckpoint.Decision decision = checkpointChain.evaluate(checkCtx);
            if (decision == SecurityCheckpoint.Decision.DENY_AND_BREAK) {
                break dispatchLoop;
            }
            if (decision == SecurityCheckpoint.Decision.DENY) {
                continue;
            }

            allowedCalls.add(chatToolCall);
        }

        // Dispatch-loop pause handling (design §6.2): if the ledger
        // marked the session as paused during this iteration's deny
        // recording (threshold exceeded), skip the allowedCalls
        // execution but do NOT break reactLoop here. The reactLoop
        // break is the exclusive responsibility of the
        // denialLedger.isPaused check at the next iteration start.
        // This separation keeps the two mechanisms disjoint:
        //   * Mechanism 1 (here): skip remaining execution this iteration.
        //   * Mechanism 2 (iteration start): abort the ReAct loop.
        if (ctx.getStatus() == AgentExecStatus.paused) {
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            return IterationFlow.CONTINUE_LOOP;
        }

        if (!allowedCalls.isEmpty()) {
            if (heartBeatIntervalMs > 0) {
                // Plan 2285 (WI4): sliced wait with heartbeat wakeup turns —
                // the model adjudicates continue/abandon/parallel while the
                // tools run; mechanical caps stay the fallback.
                runHeartbeatToolPhase(ctx, sessionId, agentName, allowedCalls, toolExecCtx,
                        execStartTime, checkpointSeq, options, state);
            } else {
                toolDispatcher.executeAllowedCalls(ctx, agentName, sessionId, allowedCalls,
                        toolExecCtx, execStartTime, checkpointSeq);
            }
        }

        if (ctx.isCancelRequested()) {
            handleCancellation(ctx, sessionId, agentName);
            return IterationFlow.BREAK_LOOP;
        }

        // boundary. After all tool calls in this round completed and
        // their results written back to the ctx message list (above),
        // drain the steering queue and append any queued steering
        // messages to ctx before the next LLM call. The drain runs on
        // the ReAct thread; the Actor's consumption thread enqueues via
        // ConcurrentLinkedQueue (lock-free coordination). With the
        // shipped NoOpActorRuntime default the queue is always empty,
        // so drainSteering() returns an empty list (one poll that
        // finds null) — zero-regression no-op. When steering messages
        // are present, they are appended as new messages (裁定 4:
        // append, not modify history) and the next iteration's LLM
        // call sees them via ctx.getMessages() (裁定 3: round boundary).
        List<ChatMessage> steeringMessages = ctx.drainSteering();
        if (!steeringMessages.isEmpty()) {
            for (ChatMessage steeringMsg : steeringMessages) {
                ctx.addMessage(steeringMsg);
            }
            // Plan 2289 (WI8): persist committed injections immediately
            persistInjectedMessages(ctx, sessionId);
            LOG.info("Steering checkpoint: injected {} steering message(s) at round boundary "
                            + "(iteration={}). session={}",
                    steeringMessages.size(), ctx.getCurrentIteration(), sessionId);
        }

        ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
        return IterationFlow.CONTINUE_LOOP;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — post-loop terminal-state
     * adjudication: MAX_ITERATIONS truncation marking + conditional POST_CALL
     * hook / EXECUTION_COMPLETED publication.
     */
    private void adjudicateTerminal(AgentExecutionContext ctx, String sessionId, String agentName) {
        // AR-14-a (plan 277): if the reactLoop exited because
        // currentIteration >= maxIterations and the sustainer declined to
        // continue (STOP), the status is still "running" — meaning the
        // agent hit its iteration budget without the completion judge
        // declaring completion. Report this as "truncated" (not
        // "completed"), so downstream consumers can distinguish a
        // successful completion from a budget-truncated session.
        if (ctx.getStatus() == AgentExecStatus.running) {
            ctx.setStatus(AgentExecStatus.truncated);
        }

        // Post-loop bookkeeping (design §6.2): a paused / cancelled /
        // forced_stopped / escalated session must NOT publish
        // EXECUTION_COMPLETED or run POST_CALL hooks — the session is
        // suspended or aborted, not finished. AR-14-b (plan 277):
        // "truncated" is also excluded — a truncated session should not
        // publish an "execution completed" event (it was budget-limited,
        // not successfully completed). M6-P1 (round-2 audit): "failed"
        // is excluded too — a retry-exhausted / non-retryable failure
        // terminates with status=failed and already publishes
        // EXECUTION_FAILED; publishing "completed" on top would corrupt
        // the event/status semantics (its EXECUTION_FAILED is the
        // terminal notification, published by the failure path itself).
        if (canPublishExecutionCompleted(ctx)) {
            publishExecutionCompleted(ctx, sessionId, agentName);
        }
    }

    /**
     * AI-1 (plan 355): the original inline multi-enum comparison chain,
     * moved verbatim — the terminal-status gate for POST_CALL hooks and the
     * EXECUTION_COMPLETED event.
     *
     * <p>M6-P1 (round-2 audit): {@link AgentExecStatus#failed} is excluded
     * too. A retry-exhausted / non-retryable LLM failure sets
     * {@code status=failed} in {@code LlmCallCoordinator.finalizeLlmCallResult}
     * (which also publishes EXECUTION_FAILED), the reactLoop then breaks and
     * reaches {@code adjudicateTerminal}. Without this exclusion a failed
     * execution published EXECUTION_COMPLETED and ran POST_CALL hooks,
     * contradicting the "aborted/suspended sessions must not publish" contract
     * documented above. Full exclusion surface (every terminal enum value):
     * cancelled / forced_stopped / escalated / paused / truncated / waiting /
     * failed. The remaining non-excluded terminal value is completed (the only
     * status that legitimately publishes EXECUTION_COMPLETED); pending and
     * running never survive to this point (running is first converted to
     * truncated at the top of {@code adjudicateTerminal}).
     */
    private boolean canPublishExecutionCompleted(AgentExecutionContext ctx) {
        return ctx.getStatus() != AgentExecStatus.cancelled
                && ctx.getStatus() != AgentExecStatus.forced_stopped
                && ctx.getStatus() != AgentExecStatus.escalated
                && ctx.getStatus() != AgentExecStatus.paused
                && ctx.getStatus() != AgentExecStatus.truncated
                && ctx.getStatus() != AgentExecStatus.waiting
                && ctx.getStatus() != AgentExecStatus.failed;
    }

    /**
     * AI-1 (plan 355): extracted from execute() — POST_CALL hook execution
     * (with W5-3 bail marking) and EXECUTION_COMPLETED event publication.
     */
    private void publishExecutionCompleted(AgentExecutionContext ctx, String sessionId, String agentName) {
        HookResult postCallResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.POST_CALL, ctx, agentName, null, null);

        // W5-3 (BAIL): POST_CALL middleware returned BailResult →
        // mark the final result as guardrail-blocked. Execution has
        // already finished (cannot be revoked); BAIL here only marks
        // the structured result for audit/caller decision. ctx.bailReason
        // flows to AgentExecutionResult via fromContext (design §5.4 裁定 E).
        boolean guardrailBlocked = postCallResult.isBail();
        if (guardrailBlocked) {
            String bailReason = ((HookResult.BailResult) postCallResult).getReason();
            ctx.setBailReason(bailReason);
            LOG.warn("POST_CALL middleware bailed (reason={}); "
                            + "marking final result as guardrail-blocked. session={}",
                    bailReason, sessionId);
        }

        Map<String, Object> completedPayload = new HashMap<>();
        completedPayload.put(AgentEventPayloads.TOTAL_ITERATIONS, ctx.getCurrentIteration());
        completedPayload.put(AgentEventPayloads.TOTAL_TOKENS_USED, ctx.getTokensUsed());
        completedPayload.put(AgentEventPayloads.DURATION_MS, CoreMetrics.currentTimeMillis() - ctx.getStartTimeMs());
        // W5-3: additive guardrailBlocked marker so downstream
        // consumers can distinguish "completed" from "completed but
        // final response guardrail-blocked" (design §5.4 裁定 E).
        completedPayload.put(AgentEventPayloads.GUARDRAIL_BLOCKED, guardrailBlocked);
        if (guardrailBlocked) {
            completedPayload.put(AgentEventPayloads.BAIL_REASON,
                    ((HookResult.BailResult) postCallResult).getReason());
        }
        hookInvoker.publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName, completedPayload);
    }

    /**
     * AI-1 (plan 355): tri-state control-flow signal for the extracted
     * iteration phase methods — replaces the original inline
     * continue / break / break reactLoop statements of the execute()
     * loop body.
     */
    private enum IterationFlow {
        /** proceed to the next phase within this iteration */
        PROCEED,
        /** re-enter the reactLoop (start the next iteration) */
        CONTINUE_LOOP,
        /** break the reactLoop */
        BREAK_LOOP
    }

    /**
     * AI-1 (plan 355): per-execution mutable state threaded through the
     * extracted phase methods of execute(). The fields replace execute()'s
     * original inline locals; the holder itself stays a per-execute()
     * local — none of this state is promoted to an executor instance
     * field (each execute() call owns an independent copy, same scoping
     * contract as the original declarations).
     */
    private static final class ExecutionState {
        // Per-execution model-switched message tracking (plan 205 / L2-21,
        // design nop-ai-agent-usage-and-billing.md §3.5): lastModelKey holds
        // the previous iteration's model identity (provider:model composite
        // key) so a change between iterations is detected. messageSeq is the
        // per-execution monotonically increasing sequence counter for
        // nop_ai_session_message rows written by this execution. Both are
        // per-execute state (not promoted to AgentExecutionContext) because
        // there is no fork/restore of the context within execute(), consistent
        // with the checkpointSeq precedent. (Note: reentryCounters was moved
        // to per-iteration scope inside reactLoop — see AR-06 / plan 277.)
        String lastModelKey = null;
        final long[] messageSeq = {0};

        // Per-execution checkpoint sequence counter (design §5.4 / L3-4):
        // monotonically increments each time a checkpoint (TOOL_EXECUTION /
        // LLM_TURN / COMPACTION) is recorded, so checkpoints within one
        // execute() call are ordered across trigger-point types. Passed as a
        // 1-element holder so performCompaction / handleForcedStop can record
        // a COMPACTION checkpoint on the same counter (plan 187). The holder
        // stays per-execution state (not promoted to an executor field),
        // consistent with the TOOL_EXECUTION-only behaviour.
        final int[] checkpointSeq = {0};

        // completion-judge Continue-decision counter (dead-loop protection,
        // see adjudicateResponse).
        int consecutiveContinues = 0;

        // W5-3 (BAIL): per-request POST_REASONING bail counter. Persists
        // across reactLoop iterations AND sustain rounds (the cap is
        // per-execute() call, not per-iteration). Exceeding
        // MAX_POST_REASONING_BAILS fails loud. Not reset on sustain — a
        // session that bailed 3 times then sustained should not get 3 more.
        int bailCount = 0;
    }

    /** AI-1 (plan 355): the routed options + built ChatRequest for one LLM turn. */
    private record RoutedRequest(ChatRequest request, ChatOptions routedOptions) {
    }

    /**
     * AI-1 (plan 355): values produced by one recorded LLM turn and consumed
     * by the response-adjudication and tool fan-out phases.
     */
    private record LlmTurn(ChatAssistantMessage assistantMsg, List<ChatToolCall> responseToolCalls,
                           HookResult postReasoningResult) {
    }
}
