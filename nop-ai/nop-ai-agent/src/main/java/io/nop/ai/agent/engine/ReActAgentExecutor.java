package io.nop.ai.agent.engine;

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
import io.nop.ai.agent.reliability.ICircuitBreaker;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.ISustainer;
import io.nop.ai.agent.reliability.IWaitCoordinator;
import io.nop.ai.agent.reliability.IterationSnapshot;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoOpGoalTracker;
import io.nop.ai.agent.reliability.NoOpSustainer;
import io.nop.ai.agent.reliability.NoOpWaitCoordinator;
import io.nop.ai.agent.reliability.RetryContext;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.reliability.SustainContext;
import io.nop.ai.agent.reliability.SustainDecision;
import io.nop.ai.agent.reliability.SustainStopReason;
import io.nop.ai.agent.reliability.ThresholdBreaker;
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
import io.nop.ai.agent.session.IModelSwitchedMessageWriter;
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
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.api.core.json.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private final AgentHookInvoker hookInvoker;
    private final LlmCallCoordinator llmCoordinator;
    private final AgentSecurityConsultation securityConsultation;
    private final AgentCompactionCoordinator compactionCoordinator;
    private final AgentToolPlanResolver toolPlanResolver;
    private final AgentPromptAssembly promptAssembly;
    private final AgentToolDispatcher toolDispatcher;
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
                this.securityConsultation);
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

    public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {
        AgentModel agentModel = ctx.getAgentModel();

        ctx.setStatus(AgentExecStatus.running);

        String agentName = agentModel != null ? agentModel.getName() : null;
        String sessionId = ctx.getSessionId();

        hookInvoker.publishEvent(AgentEventType.EXECUTION_STARTED, sessionId, agentName,
                Map.of("agentName", agentName != null ? agentName : ""));

        // filtering. When sessionStore is null (testing) or the session is
        // not found, pass null — resolveActiveTags falls back to the agent
        // model's declared activeTags.
        AgentSession agentSession = sessionStore != null && sessionId != null
                ? sessionStore.get(sessionId) : null;

        List<ChatToolDefinition> toolDefs = new ArrayList<>();
        ChatOptions options = promptAssembly.assembleExecutionSetup(ctx, agentModel, agentSession, toolDefs);

        // AR-06 (plan 277): reentryCounters is declared per-iteration (inside
        // the reactLoop body below), NOT here. The old per-execute declaration
        // accumulated across all iterations and was never reset, silently
        // starving legitimate re-enter hooks after DEFAULT_MAX_REENTRIES uses.

        int consecutiveContinues = 0;

        // Per-execution model-switched message tracking (plan 205 / L2-21,
        // design nop-ai-agent-usage-and-billing.md §3.5): lastModelKey holds
        // the previous iteration's model identity (provider:model composite
        // key) so a change between iterations is detected. messageSeq is the
        // per-execution monotonically increasing sequence counter for
        // nop_ai_session_message rows written by this execution. Both are
        // per-execute locals (not promoted to AgentExecutionContext) because
        // there is no fork/restore of the context within execute(), consistent
        // with the checkpointSeq precedent. (Note: reentryCounters was moved
        // to per-iteration scope inside reactLoop — see AR-06 / plan 277.)
        String lastModelKey = null;
        long[] messageSeq = {0};

        // Per-execution checkpoint sequence counter (design §5.4 / L3-4):
        // monotonically increments each time a checkpoint (TOOL_EXECUTION /
        // LLM_TURN / COMPACTION) is recorded, so checkpoints within one
        // execute() call are ordered across trigger-point types. Passed as a
        // 1-element holder so performCompaction / handleForcedStop can record
        // a COMPACTION checkpoint on the same counter (plan 187). The holder
        // stays a per-execution local (not promoted to a field), consistent
        // with the TOOL_EXECUTION-only behaviour.
        int[] checkpointSeq = {0};

        // Per-execution disambiguator embedded in checkpoint watermarks so
        // watermarks stay unique across separate execute() calls sharing the
        // same sessionId (e.g. a crash/restart restore re-execution persists
        // to the same DB-backed manager). The seq alone resets to 0 on each
        // execute(), so without this component a restored LLM_TURN(0) would
        // collide with the pre-crash LLM_TURN(0) watermark (plan 187).
        long execStartTime = ctx.getStartTimeMs();

        try {
            HookResult preCallResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_CALL, ctx, agentName, null, null);
            if (preCallResult.isVeto()) {
                ctx.setStatus(AgentExecStatus.completed);
                hookInvoker.publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName,
                        Map.of("vetoedAt", "PRE_CALL", "reason", hookInvoker.vetoReason(preCallResult)));
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
                // AR-06 (plan 277): per-iteration re-entry counter. Reset at
                // the start of each iteration so a long session is not silently
                // starved by a cumulative session-wide cap. Each re-entrant
                // hook point (BEFORE/AFTER_TOOL_RESULT_PROCESSED) has its own
                // independent count within the iteration.
                Map<AgentLifecyclePoint, Integer> reentryCounters = new HashMap<>();

                if (ctx.isCancelRequested()) {
                    handleCancellation(ctx, sessionId, agentName);
                    break;
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
                    break reactLoop;
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
                            System.currentTimeMillis(),
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
                    break reactLoop;
                }

                if (loopGuard.shouldForceStop(ctx)) {
                    loopGuard.handleForcedStop(ctx, sessionId, agentName, checkpointSeq);
                    break;
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
                    break reactLoop;
                }

                if (compactionCoordinator.shouldTriggerCompaction(ctx)) {
                    compactionCoordinator.performCompaction(ctx, agentName, checkpointSeq);
                }

                HookResult preReasoningResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_REASONING, ctx, agentName, null, null);
                if (preReasoningResult.isVeto()) {
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
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
                    continue;
                }

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
                    throw new NopAiAgentException(
                            "budgetProvider.getBudget() returned null: provider=" + budgetProvider.getClass().getName());
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

                // persist a model-switched audit message (role=80) when the
                // routed model differs from the previous iteration's model
                // (design nop-ai-agent-usage-and-billing.md §3.5). The message
                // is an audit record persisted to nop_ai_session_message — it is
                // NOT added to ctx.getMessages() and therefore never injected
                // into the LLM reasoning context.
                String currentModelKey = llmCoordinator.buildModelKey(routedOptions);
                if (lastModelKey != null && !currentModelKey.equals(lastModelKey)
                        && sessionId != null) {
                    messageSeq[0]++;
                    modelSwitchedMessageWriter.writeModelSwitched(
                            sessionId, lastModelKey, currentModelKey,
                            routingResult.getRoutingReason(),
                            routingResult.getComplexity(),
                            messageSeq[0]);
                }
                lastModelKey = currentModelKey;

                ChatRequest request = new ChatRequest(new ArrayList<>(ctx.getMessages()));
                request.setOptions(routedOptions);
                List<ChatMessage> messagesAtCallTime = request.getMessages();

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
                        request, ctx, sessionId, agentName, routedOptions);
                routedOptions = llmResult.routedOptions;

                if (!llmResult.isSuccess()) {
                    break;
                }

                ChatAssistantMessage assistantMsg = llmResult.response.getMessage();
                ctx.addMessage(assistantMsg);

                if (llmResult.response.getUsage() != null) {
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
                    usageRecord.setResponseDurationMs(System.currentTimeMillis() - llmResult.llmCallStart);
                    usageRecord.setResponseTimestamp(System.currentTimeMillis());
                    usageRecorder.record(usageRecord);

                    if (promptTokens > 0) {
                        tokenEstimator.record(request.getMessages(), promptTokens);
                    }
                }

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
                        System.currentTimeMillis(),
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

                Map<String, Object> llmPayload = new HashMap<>();
                llmPayload.put("iteration", ctx.getCurrentIteration());
                llmPayload.put("hasToolCalls", assistantMsg.hasToolCalls());
                hookInvoker.publishEvent(AgentEventType.LLM_RESPONSE_RECEIVED, sessionId, agentName, llmPayload);

                hookInvoker.executeWithMiddleware(AgentLifecyclePoint.POST_REASONING, ctx, agentName, null, null);

        if (promptAssembly.checkOutputGuardrail(ctx, assistantMsg)) {
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            continue;
        }

                // goal tracker. Called once per iteration after the LLM
                // response is finalised (assistantMsg built + output guardrail
                // applied) and before the tool-dispatch / completion-judge
                // branch (design nop-ai-agent-reliability.md §5.3). This is the
                // single call site covering both branches: the engine extracts
                // the request-level tool-call signatures from
                // assistantMsg.getToolCalls() (empty when the LLM produced no
                // tool calls — the completion-judge branch). With the shipped
                // NoOpGoalTracker default recordIteration is an explicit no-op,
                // so this is zero-regression.
                goalTracker.recordIteration(sessionId,
                        new IterationSnapshot(ctx.getCurrentIteration(),
                                buildToolCallSignatures(assistantMsg)));

                if (!assistantMsg.hasToolCalls()) {
                    CompletionDecision decision = completionJudge.decide(assistantMsg, ctx);

                    if (decision.isComplete()) {
                        ctx.setStatus(AgentExecStatus.completed);
                        break;
                    }

                    if (decision.isContinue()) {
                        if (consecutiveContinues >= DEFAULT_MAX_COMPLETION_CONTINUES) {
                            LOG.warn("Completion-judge dead-loop protection: {} consecutive Continue decisions, force-exiting loop. session={}",
                                    DEFAULT_MAX_COMPLETION_CONTINUES, sessionId);
                            ctx.setStatus(AgentExecStatus.completed);
                            break;
                        }
                        String continuationMessage = ((CompletionDecision.Continue) decision).getMessage();
                        ctx.addMessage(new ChatUserMessage(
                                continuationMessage != null ? continuationMessage : ""));
                        consecutiveContinues++;
                        ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                        continue;
                    }

                    if (decision.isEscalate()) {
                        String reason = ((CompletionDecision.Escalate) decision).getReason();
                        ctx.setStatus(AgentExecStatus.escalated);
                        ctx.setLastError(reason);
                        ctx.getMetadata().put("completion.escalateReason",
                                reason != null ? reason : "");
                        consecutiveContinues = 0;
                        break;
                    }

                    ctx.setStatus(AgentExecStatus.completed);
                    break;
                }

                consecutiveContinues = 0;

                // the provider (when wired). When the provider is null
                // (executor constructed outside the engine for testing, or
                // explicitly opted out), the store stays null and memory tools
                // fail fast at execution time with a descriptive error.
        AgentToolExecuteContext toolExecCtx = toolDispatcher.prepareDispatchContext(ctx, agentModel, sessionId, agentName);
        String fingerprintWorkDir = securityConsultation.resolveWorkDirString(agentModel);

                List<ChatToolCall> allowedCalls = new ArrayList<>();

                dispatchLoop:
                for (ChatToolCall chatToolCall : assistantMsg.getToolCalls()) {
                    chatToolCall = toolCallRepairer.repair(chatToolCall, ctx);

                    String toolName = chatToolCall.getName();

                    hookInvoker.publishEvent(AgentEventType.TOOL_CALL_STARTED, sessionId, agentName,
                            Map.of("toolName", toolName,
                                    "iteration", ctx.getCurrentIteration()));

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
                    continue reactLoop;
                }

        if (!allowedCalls.isEmpty()) {
            toolDispatcher.executeAllowedCalls(ctx, agentName, sessionId, allowedCalls,
                    toolExecCtx, execStartTime, checkpointSeq);
        }

                if (ctx.isCancelRequested()) {
                    handleCancellation(ctx, sessionId, agentName);
                    break;
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
                    LOG.info("Steering checkpoint: injected {} steering message(s) at round boundary "
                            + "(iteration={}). session={}",
                            steeringMessages.size(), ctx.getCurrentIteration(), sessionId);
                }

                ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
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
                    throw new NopAiAgentException(
                            "sustainer.onStop() returned null for stopReason="
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
            // not successfully completed).
            if (ctx.getStatus() != AgentExecStatus.cancelled
                    && ctx.getStatus() != AgentExecStatus.forced_stopped
                    && ctx.getStatus() != AgentExecStatus.escalated
                    && ctx.getStatus() != AgentExecStatus.paused
                    && ctx.getStatus() != AgentExecStatus.truncated
                    && ctx.getStatus() != AgentExecStatus.waiting) {
                hookInvoker.executeWithMiddleware(AgentLifecyclePoint.POST_CALL, ctx, agentName, null, null);

                Map<String, Object> completedPayload = new HashMap<>();
                completedPayload.put("totalIterations", ctx.getCurrentIteration());
                completedPayload.put("totalTokensUsed", ctx.getTokensUsed());
                completedPayload.put("durationMs", System.currentTimeMillis() - ctx.getStartTimeMs());
                hookInvoker.publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName, completedPayload);
            }

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

        return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
    }

    /**
     * Plan 211 (L3-3): build the stable tool-call signatures for an iteration
     * from the assistant message's requested tool calls (design
     * {@code nop-ai-agent-reliability.md} §5.3). Each signature is
     * {@code toolName:stableArgsString} where {@code stableArgsString} is the
     * args map serialised with sorted keys, so key-order differences across
     * iterations do not produce different signatures. Returns an empty list
     * when the LLM produced no tool calls (the completion-judge branch).
     */
    private static List<String> buildToolCallSignatures(ChatAssistantMessage assistantMsg) {
        if (!assistantMsg.hasToolCalls()) {
            return List.of();
        }
        List<String> signatures = new ArrayList<>();
        for (ChatToolCall call : assistantMsg.getToolCalls()) {
            String name = call.getName();
            Map<String, Object> args = call.getArguments();
            String stableArgs = args != null && !args.isEmpty()
                    ? JSON.stringify(new TreeMap<>(args))
                    : "{}";
            signatures.add(name + ":" + stableArgs);
        }
        return signatures;
    }

    private void handleCancellation(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.cancelled);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", ctx.getCancelReason() != null ? ctx.getCancelReason() : "");
        hookInvoker.publishEvent(AgentEventType.SESSION_CANCELLED, sessionId, agentName, payload);
    }
}
