package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.CompactionContext;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.compact.ToolResultTruncator;
import io.nop.ai.agent.budget.BudgetSnapshot;
import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.completion.CompletionDecision;
import io.nop.ai.agent.completion.ICompletionJudge;
import io.nop.ai.agent.completion.NoOpCompletionJudge;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.hook.NoOpHookRegistry;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.repair.ChainRepairer;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.repair.NoOpToolCallRepairer;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ErrorClassification;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.LlmErrorClassifier;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoRetryPolicy;
import io.nop.ai.agent.reliability.RetryContext;
import io.nop.ai.agent.reliability.RetryOutcome;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.ApprovalDecision;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultLevelHintsProducer;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.DenialResult;
import io.nop.ai.agent.security.FingerprintPostDenialGuard;
import io.nop.ai.agent.security.IApprovalGate;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.IPostDenialGuard;
import io.nop.ai.agent.security.ILevelHintsProducer;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.MatrixDecision;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.PathAccessResult;
import io.nop.ai.agent.security.Permission;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityLevel;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.ToolPathArgKeys;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.DbModelSwitchedMessageWriter;
import io.nop.ai.agent.session.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.NoOpModelSwitchedMessageWriter;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.NoOpSkillProvider;
import io.nop.ai.agent.skill.SkillAssemblyResult;
import io.nop.ai.agent.skill.SkillResolver;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.agent.usage.UsageRecord;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ReActAgentExecutor implements IAgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ReActAgentExecutor.class);

    public static final int DEFAULT_MAX_REENTRIES = 3;
    public static final int DEFAULT_MAX_COMPLETION_CONTINUES = 3;
    public static final double DEFAULT_TRIGGER_TOKEN_PERCENT = 0.8;
    public static final int DEFAULT_TRIGGER_MAX_MESSAGES = 30;
    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 128000;

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final IAgentEventPublisher eventPublisher;
    private final IPermissionProvider permissionProvider;
    private final IToolAccessChecker toolAccessChecker;
    private final IPathAccessChecker pathAccessChecker;
    private final IAuditLogger auditLogger;
    private final IHookRegistry hookRegistry;
    private final IToolCallRepairer toolCallRepairer;
    private final IContextCompactor contextCompactor;
    private final IContentGuardrail contentGuardrail;
    private final IModelRouter modelRouter;
    private final ITokenEstimator tokenEstimator;
    private final ICompletionJudge completionJudge;
    private final List<ITalent> talents;
    private final ISkillProvider skillProvider;
    private final IAgentEngine engine;
    private final IAgentMessenger messenger;
    private final ISecurityLevelResolver securityLevelResolver;
    private final IPermissionMatrix permissionMatrix;
    private final ILevelHintsProducer levelHintsProducer;
    private final IApprovalGate approvalGate;
    private final IDenialLedger denialLedger;
    private final IPostDenialGuard postDenialGuard;
    private final ICheckpointManager checkpointManager;
    private final ISessionStore sessionStore;
    private final IMemoryStoreProvider memoryStoreProvider;
    private final IUsageRecorder usageRecorder;
    private final IModelSwitchedMessageWriter modelSwitchedMessageWriter;
    // Plan 206 (L2-22): budget provider consulted once per ReAct iteration,
    // immediately before IModelRouter.route(), to refresh the budget snapshot
    // stored in ctx. A functional router reads ctx.getBudgetSnapshot() to
    // decide whether to downgrade the model on budget exhaustion.
    private final IBudgetProvider budgetProvider;
    // Plan 207 (L3-2): retry policy consulted by the single-LLM-call retry
    // loop (design nop-ai-agent-llm-layer.md §7 / nop-ai-agent-reliability.md
    // §3.1). When chatService.call(...) throws, the loop classifies the error,
    // builds a RetryContext, and asks the policy RETRY / STOP / FALLBACK. The
    // shipped NoRetryPolicy default unconditionally returns STOP, so the loop
    // executes the call exactly once and propagates the exception as-is —
    // zero-regression versus the pre-plan-207 behaviour. A functional policy
    // (StandardRetryPolicy) is registered via DefaultAgentEngine.setRetryPolicy.
    private final IRetryPolicy retryPolicy;

    private ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
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
                                 ILevelHintsProducer levelHintsProducer,
                                  IApprovalGate approvalGate,
                                  IDenialLedger denialLedger,
                                  IPostDenialGuard postDenialGuard,
                                    ICheckpointManager checkpointManager,
                                    ISessionStore sessionStore,
                                      IMemoryStoreProvider memoryStoreProvider,
                                      IUsageRecorder usageRecorder,
                                       IModelSwitchedMessageWriter modelSwitchedMessageWriter,
                                       IBudgetProvider budgetProvider,
                                       IRetryPolicy retryPolicy) {
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.eventPublisher = eventPublisher;
        this.permissionProvider = permissionProvider;
        this.toolAccessChecker = toolAccessChecker;
        this.pathAccessChecker = pathAccessChecker;
        this.auditLogger = auditLogger;
        this.hookRegistry = hookRegistry != null ? hookRegistry : NoOpHookRegistry.INSTANCE;
        this.toolCallRepairer = toolCallRepairer != null ? toolCallRepairer : NoOpToolCallRepairer.INSTANCE;
        this.contextCompactor = contextCompactor != null ? contextCompactor : NoOpContextCompactor.INSTANCE;
        this.contentGuardrail = contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp();
        this.modelRouter = modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough();
        this.tokenEstimator = tokenEstimator != null ? tokenEstimator : CalibratedTokenEstimator.defaultInstance();
        this.completionJudge = completionJudge != null ? completionJudge : NoOpCompletionJudge.noOp();
        this.talents = talents != null ? List.copyOf(talents) : List.of();
        this.skillProvider = skillProvider != null ? skillProvider : NoOpSkillProvider.noOp();
        this.engine = engine;
        this.messenger = messenger;
        this.securityLevelResolver = securityLevelResolver != null
                ? securityLevelResolver
                : new DefaultSecurityLevelResolver();
        this.permissionMatrix = permissionMatrix != null
                ? permissionMatrix
                : new DefaultPermissionMatrix();
        this.levelHintsProducer = levelHintsProducer != null
                ? levelHintsProducer
                : new DefaultLevelHintsProducer();
        this.approvalGate = approvalGate != null
                ? approvalGate
                : new DefaultApprovalGate();
        this.denialLedger = denialLedger != null
                ? denialLedger
                : new DefaultDenialLedger();
        this.postDenialGuard = postDenialGuard != null
                ? postDenialGuard
                : new DefaultPostDenialGuard();
        this.checkpointManager = checkpointManager != null
                ? checkpointManager
                : NoOpCheckpoint.noOp();
        this.sessionStore = sessionStore;
        this.memoryStoreProvider = memoryStoreProvider;
        this.usageRecorder = usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp();
        this.modelSwitchedMessageWriter = modelSwitchedMessageWriter != null
                ? modelSwitchedMessageWriter
                : NoOpModelSwitchedMessageWriter.noOp();
        this.budgetProvider = budgetProvider != null ? budgetProvider : NoOpBudgetProvider.noOp();
        this.retryPolicy = retryPolicy != null ? retryPolicy : NoRetryPolicy.noRetry();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private IChatService chatService;
        private IToolManager toolManager;
        private IAgentEventPublisher eventPublisher;
        private IPermissionProvider permissionProvider;
        private IToolAccessChecker toolAccessChecker;
        private IPathAccessChecker pathAccessChecker;
        private IAuditLogger auditLogger;
        private IHookRegistry hookRegistry;
        private IToolCallRepairer toolCallRepairer;
        private IContextCompactor contextCompactor;
        private IContentGuardrail contentGuardrail;
        private IModelRouter modelRouter;
        private ITokenEstimator tokenEstimator;
        private ICompletionJudge completionJudge;
        private List<ITalent> talents;
        private ISkillProvider skillProvider;
        private IAgentEngine engine;
        private IAgentMessenger messenger;
        private ISecurityLevelResolver securityLevelResolver;
        private IPermissionMatrix permissionMatrix;
        private ILevelHintsProducer levelHintsProducer;
        private IApprovalGate approvalGate;
        private IDenialLedger denialLedger;
        private IPostDenialGuard postDenialGuard;
        private ICheckpointManager checkpointManager;
        private ISessionStore sessionStore;
        private IMemoryStoreProvider memoryStoreProvider;
        private IUsageRecorder usageRecorder;
        private IModelSwitchedMessageWriter modelSwitchedMessageWriter;
        private IBudgetProvider budgetProvider;
        private IRetryPolicy retryPolicy;

        public Builder chatService(IChatService chatService) {
            this.chatService = chatService;
            return this;
        }

        public Builder toolManager(IToolManager toolManager) {
            this.toolManager = toolManager;
            return this;
        }

        public Builder eventPublisher(IAgentEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public Builder permissionProvider(IPermissionProvider permissionProvider) {
            this.permissionProvider = permissionProvider;
            return this;
        }

        public Builder toolAccessChecker(IToolAccessChecker toolAccessChecker) {
            this.toolAccessChecker = toolAccessChecker;
            return this;
        }

        public Builder pathAccessChecker(IPathAccessChecker pathAccessChecker) {
            this.pathAccessChecker = pathAccessChecker;
            return this;
        }

        public Builder auditLogger(IAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
            return this;
        }

        public Builder hookRegistry(IHookRegistry hookRegistry) {
            this.hookRegistry = hookRegistry;
            return this;
        }

        public Builder toolCallRepairer(IToolCallRepairer toolCallRepairer) {
            this.toolCallRepairer = toolCallRepairer;
            return this;
        }

        /**
         * Opt in to the 4-stage {@link ChainRepairer}, wired with this
         * Builder's {@code toolManager}. The {@code toolManager} must be set
         * before calling this method. The default remains
         * {@code NoOpToolCallRepairer.INSTANCE} when this method is not called.
         */
        public Builder enableChainRepairer() {
            if (toolManager == null) {
                throw new NopAiAgentException("toolManager must be set before enabling ChainRepairer");
            }
            this.toolCallRepairer = ChainRepairer.withDefaults(toolManager);
            return this;
        }

        public Builder contextCompactor(IContextCompactor contextCompactor) {
            this.contextCompactor = contextCompactor;
            return this;
        }

        public Builder contentGuardrail(IContentGuardrail contentGuardrail) {
            this.contentGuardrail = contentGuardrail;
            return this;
        }

        public Builder modelRouter(IModelRouter modelRouter) {
            this.modelRouter = modelRouter;
            return this;
        }

        public Builder tokenEstimator(ITokenEstimator tokenEstimator) {
            this.tokenEstimator = tokenEstimator;
            return this;
        }

        public Builder completionJudge(ICompletionJudge completionJudge) {
            this.completionJudge = completionJudge;
            return this;
        }

        public Builder talents(List<ITalent> talents) {
            this.talents = talents;
            return this;
        }

        public Builder skillProvider(ISkillProvider skillProvider) {
            this.skillProvider = skillProvider;
            return this;
        }

        /**
         * Wire the {@link IAgentEngine} self-reference so that engine-aware
         * tools (call-agent) can execute sub-agents. Optional: when null
         * (e.g. executor constructed outside the engine for testing),
         * engine-aware tools fail fast at execution time. The engine is the
         * only production caller of the Builder and always passes itself.
         */
        public Builder engine(IAgentEngine engine) {
            this.engine = engine;
            return this;
        }

        /**
         * Wire the {@link IAgentMessenger} so that engine-aware tools
         * (send-message) can deliver inter-agent messages. Optional: when null,
         * messenger-aware tools fail fast at execution time.
         */
        public Builder messenger(IAgentMessenger messenger) {
            this.messenger = messenger;
            return this;
        }

        /**
         * Wire the {@link ISecurityLevelResolver} consulted in the Layer 2
         * dispatch-path step (design §5.1). Optional: when null, defaults to
         * {@link DefaultSecurityLevelResolver} (trusted-by-default variant).
         */
        public Builder securityLevelResolver(ISecurityLevelResolver securityLevelResolver) {
            this.securityLevelResolver = securityLevelResolver;
            return this;
        }

        /**
         * Wire the {@link IPermissionMatrix} consulted in the Layer 2
         * dispatch-path step (design §5.3). Optional: when null, defaults to
         * {@link DefaultPermissionMatrix} (§5.3 channel × level matrix with
         * usability-safe null channel).
         */
        public Builder permissionMatrix(IPermissionMatrix permissionMatrix) {
            this.permissionMatrix = permissionMatrix;
            return this;
        }

        /**
         * Wire the {@link ILevelHintsProducer} that derives the
         * {@link LevelHints} fed to the {@link ISecurityLevelResolver} in the
         * Layer 2 dispatch-path step. Optional: when null, defaults to
         * {@link DefaultLevelHintsProducer}.
         */
        public Builder levelHintsProducer(ILevelHintsProducer levelHintsProducer) {
            this.levelHintsProducer = levelHintsProducer;
            return this;
        }

        /**
         * Wire the {@link IApprovalGate} consulted in the Layer 3
         * dispatch-path step (design §6.1 / §4.8) after the Layer 2 permission matrix
         * allows a tool call. Optional: when null, defaults to
         * {@link DefaultApprovalGate} (STANDARD/ELEVATED auto-approved,
         * RESTRICTED defense-in-depth denied — plan 199).
         */
        public Builder approvalGate(IApprovalGate approvalGate) {
            this.approvalGate = approvalGate;
            return this;
        }

        /**
         * Wire the {@link IDenialLedger} consulted in the Layer 3 dispatch-path
         * step (design §6.2) at every deny checkpoint (Layer 1 / 2 / 3).
         * Optional: when null, defaults to {@link DefaultDenialLedger} (in-memory
         * threshold-based counting, threshold = 3).
         */
        public Builder denialLedger(IDenialLedger denialLedger) {
            this.denialLedger = denialLedger;
            return this;
        }

        /**
         * Wire the {@link IPostDenialGuard} consulted in the dispatch loop
         * (design §6.3 / L3-7) before the Layer 1 {@code IToolAccessChecker}
         * check for each tool call (blind-retry detection), and recorded to
         * after every Layer 1/2/3 deny. Optional: when null, defaults to
         * {@link DefaultPostDenialGuard} (fingerprint-based blind-retry blocking).
         */
        public Builder postDenialGuard(IPostDenialGuard postDenialGuard) {
            this.postDenialGuard = postDenialGuard;
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
        public Builder checkpointManager(ICheckpointManager checkpointManager) {
            this.checkpointManager = checkpointManager;
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
        public Builder sessionStore(ISessionStore sessionStore) {
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
        public Builder memoryStoreProvider(IMemoryStoreProvider memoryStoreProvider) {
            this.memoryStoreProvider = memoryStoreProvider;
            return this;
        }

        /**
         * Wire the {@link IUsageRecorder} consulted at the ReAct loop's token
         * accumulation point (plan 201 / design
         * {@code nop-ai-agent-usage-and-billing.md} §3.1). Optional: when
         * null, defaults to {@link NoOpUsageRecorder} (usage data discarded —
         * pass-through, backward compatible).
         */
        public Builder usageRecorder(IUsageRecorder usageRecorder) {
            this.usageRecorder = usageRecorder;
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
        public Builder modelSwitchedMessageWriter(IModelSwitchedMessageWriter modelSwitchedMessageWriter) {
            this.modelSwitchedMessageWriter = modelSwitchedMessageWriter;
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
        public Builder budgetProvider(IBudgetProvider budgetProvider) {
            this.budgetProvider = budgetProvider;
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
        public Builder retryPolicy(IRetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public ReActAgentExecutor build() {
            if (chatService == null) {
                throw new NopAiAgentException("chatService must not be null");
            }
            if (toolManager == null) {
                throw new NopAiAgentException("toolManager must not be null");
            }
            return new ReActAgentExecutor(
                    chatService,
                    toolManager,
                    eventPublisher,
                    permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider(),
                    toolAccessChecker != null ? toolAccessChecker : new DefaultToolAccessChecker(),
                    pathAccessChecker != null ? pathAccessChecker : new DefaultPathAccessChecker(),
                    auditLogger != null ? auditLogger : new Slf4jAuditLogger(),
                    hookRegistry != null ? hookRegistry : NoOpHookRegistry.INSTANCE,
                    toolCallRepairer != null ? toolCallRepairer : NoOpToolCallRepairer.INSTANCE,
                    contextCompactor != null ? contextCompactor : NoOpContextCompactor.INSTANCE,
                    contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp(),
                    modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough(),
                    tokenEstimator != null ? tokenEstimator : CalibratedTokenEstimator.defaultInstance(),
                    completionJudge != null ? completionJudge : NoOpCompletionJudge.noOp(),
                    talents,
                    skillProvider,
                    engine,
                    messenger,
                    securityLevelResolver,
                    permissionMatrix,
                    levelHintsProducer,
                    approvalGate,
                    denialLedger,
                    postDenialGuard,
                    checkpointManager,
                    sessionStore,
                    memoryStoreProvider,
                    usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp(),
                    modelSwitchedMessageWriter != null
                            ? modelSwitchedMessageWriter
                            : NoOpModelSwitchedMessageWriter.noOp(),
                    budgetProvider != null ? budgetProvider : NoOpBudgetProvider.noOp(),
                    retryPolicy != null ? retryPolicy : NoRetryPolicy.noRetry()
            );
        }
    }

    @Override
    public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {
        AgentModel agentModel = ctx.getAgentModel();

        ctx.setStatus(AgentExecStatus.running);

        String agentName = agentModel != null ? agentModel.getName() : null;
        String sessionId = ctx.getSessionId();

        publishEvent(AgentEventType.EXECUTION_STARTED, sessionId, agentName,
                Map.of("agentName", agentName != null ? agentName : ""));

        List<ChatToolDefinition> toolDefs = buildToolDefinitions(agentModel);
        consultTalents(ctx, toolDefs);
        consultSkills(ctx, agentModel, toolDefs);
        ChatOptions options = buildChatOptions(agentModel.getChatOptions(), toolDefs);

        Map<AgentLifecyclePoint, Integer> reentryCounters = new HashMap<>();

        int consecutiveContinues = 0;

        // Per-execution model-switched message tracking (plan 205 / L2-21,
        // design nop-ai-agent-usage-and-billing.md §3.5): lastModelKey holds
        // the previous iteration's model identity (provider:model composite
        // key) so a change between iterations is detected. messageSeq is the
        // per-execution monotonically increasing sequence counter for
        // nop_ai_session_message rows written by this execution. Both are
        // loop-local (not promoted to AgentExecutionContext) because there is
        // no fork/restore of the context within execute(), consistent with
        // the checkpointSeq precedent.
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
            HookResult preCallResult = invokeHooks(AgentLifecyclePoint.PRE_CALL, ctx, agentName, null, null);
            if (preCallResult.isVeto()) {
                ctx.setStatus(AgentExecStatus.completed);
                publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName,
                        Map.of("vetoedAt", "PRE_CALL", "reason", vetoReason(preCallResult)));
                return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
            }

            reactLoop:
            while (ctx.getCurrentIteration() < ctx.getMaxIterations()) {
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
                    handleSessionPaused(ctx, sessionId, agentName);
                    break reactLoop;
                }

                if (shouldForceStop(ctx)) {
                    handleForcedStop(ctx, sessionId, agentName, checkpointSeq);
                    break;
                }

                if (shouldTriggerCompaction(ctx)) {
                    performCompaction(ctx, agentName, checkpointSeq);
                }

                HookResult preReasoningResult = invokeHooks(AgentLifecyclePoint.PRE_REASONING, ctx, agentName, null, null);
                if (preReasoningResult.isVeto()) {
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
                }

                GuardrailResult inputGuardrailResult = checkInputGuardrail(ctx);
                if (inputGuardrailResult.isBlock()) {
                    String blockReason = ((GuardrailResult.BlockResult) inputGuardrailResult).getReason();
                    ctx.addMessage(ChatToolResponseMessage.error(
                            "guardrail-block-input", "guardrail",
                            "Input blocked by content guardrail: " + (blockReason != null ? blockReason : "unspecified")));
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
                }

                // Plan 206 (L2-22): refresh the session-level budget snapshot
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

                // Plan 205 (L2-21): detect model switches between iterations and
                // persist a model-switched audit message (role=80) when the
                // routed model differs from the previous iteration's model
                // (design nop-ai-agent-usage-and-billing.md §3.5). The message
                // is an audit record persisted to nop_ai_session_message — it is
                // NOT added to ctx.getMessages() and therefore never injected
                // into the LLM reasoning context.
                String currentModelKey = buildModelKey(routedOptions);
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

                // Plan 202 (L2-18): capture the LLM call start time so the
                // usage recorder can persist the actual call duration. The end
                // time is computed when the UsageRecord is built (after a
                // successful response), so a failed call leaves duration unset.
                //
                // Plan 207 (L3-2): the single LLM call point is wrapped in a
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
                long llmCallStart = System.currentTimeMillis();
                ChatResponse response;
                {
                    int attempt = 0;
                    Throwable lastError = null;
                    ChatResponse attemptResponse = null;
                    while (true) {
                        try {
                            llmCallStart = System.currentTimeMillis();
                            attemptResponse = chatService.call(request, null);
                            break;
                        } catch (RuntimeException | Error ex) {
                            lastError = ex;
                            ErrorClassification classification = LlmErrorClassifier.classify(ex);
                            // Current call path is non-streaming → hasStreamedContent
                            // is always false (Non-Goal: actual streaming wiring is
                            // an independent successor).
                            RetryContext retryCtx = new RetryContext(
                                    attempt, ex, classification, false);
                            RetryOutcome outcome = retryPolicy.shouldRetry(retryCtx);
                            if (outcome == null) {
                                // Contract defence: policy must never return null.
                                throw new NopAiAgentException(
                                        "retryPolicy.shouldRetry() returned null for classification="
                                                + classification + ", attempt=" + attempt, ex);
                            }
                            if (outcome.isRetry()) {
                                LOG.warn("LLM call failed (classification={}, attempt={}), "
                                                + "retrying after {} ms: {}",
                                        classification, attempt, outcome.getDelayMs(),
                                        ex.toString());
                                attempt++;
                                sleepBackoff(outcome.getDelayMs());
                                continue;
                            }
                            if (outcome.isFallback()) {
                                // Plan 209: consult the model router for a
                                // fallback model. A functional router
                                // (SmartModelRouter) returns the next model in
                                // its configured fallback chain; the shipped
                                // PassThroughModelRouter default and an
                                // exhausted chain return null → fail loud
                                // (Minimum Rules #24 — no silent skip).
                                ChatOptions fallbackOptions = modelRouter.getFallback(routedOptions);
                                if (fallbackOptions == null) {
                                    LOG.error("LLM call retry policy returned FALLBACK at "
                                            + "attempt={} (classification={}), but the model "
                                            + "router provided no fallback model — stopping "
                                            + "execution. Last error: {}",
                                            attempt, classification, ex.toString());
                                    throw new NopAiAgentException(
                                            "LLM call retry policy returned FALLBACK but no "
                                                    + "fallback model is available from the model "
                                                    + "router (classification=" + classification
                                                    + ", attempt=" + attempt + ")", ex);
                                }
                                // Apply the fallback model for the next attempt.
                                // routedOptions is updated so the downstream usage
                                // record attributes tokens to the fallback model that
                                // actually executed the call (plan 209 usage-attribution
                                // requirement), not the original primary model. Note:
                                // the model-switched audit message (plan 205, role=80)
                                // is written BEFORE the retry loop based on inter-
                                // iteration model change detection; an intra-iteration
                                // fallback switch is intentionally not recorded as a
                                // separate role=80 message (Non-Goal).
                                int failedAttempt = attempt;
                                String prevModelKey = buildModelKey(routedOptions);
                                routedOptions = fallbackOptions;
                                request.setOptions(routedOptions);
                                // Reset the attempt counter: the fallback model is a
                                // fresh call cycle that deserves its own retry budget
                                // (plan 209 adjudication).
                                attempt = 0;
                                lastError = null;
                                LOG.warn("LLM call FALLBACK after attempt={} "
                                                + "(classification={}): switching model {} -> {} "
                                                + "(attempt reset to 0) and retrying",
                                        failedAttempt, classification, prevModelKey,
                                        buildModelKey(routedOptions));
                                continue;
                            }
                            // STOP: propagate the original error as-is.
                            if (lastError instanceof RuntimeException) {
                                throw (RuntimeException) lastError;
                            }
                            throw (Error) lastError;
                        }
                    }
                    response = attemptResponse;
                }

                if (!response.isSuccess()) {
                    ctx.setStatus(AgentExecStatus.failed);
                    ctx.setLastError(response.getError());

                    invokeOnError(ctx, agentName);
                    publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName,
                            response.getError());

                    break;
                }

                ChatAssistantMessage assistantMsg = response.getMessage();
                ctx.addMessage(assistantMsg);

                if (response.getUsage() != null) {
                    int promptTokens = response.getPromptTokens() != null ? response.getPromptTokens() : 0;
                    int completionTokens = response.getCompletionTokens() != null ? response.getCompletionTokens() : 0;
                    ctx.setTokensUsed(ctx.getTokensUsed() + promptTokens + completionTokens);

                    // Plan 201 (L2-17): record per-LLM-call usage via the
                    // IUsageRecorder extension point (design §3.1). The
                    // shipped NoOpUsageRecorder default discards the record
                    // (explicit pass-through); a functional recorder persists
                    // it. modelId is null here — the NopAiModel entity key is
                    // resolved by the L2-18 recorder at persistence time
                    // (agent runtime has no DB lookup). responseDurationMs is
                    // measured from llmCallStart (plan 202 L2-18) — the
                    // wall-clock span of the LLM call captured just before
                    // chatService.call().
                    UsageRecord usageRecord = new UsageRecord();
                    usageRecord.setSessionId(sessionId);
                    usageRecord.setAgentName(agentName);
                    usageRecord.setRequestId(response.getRequestId());
                    usageRecord.setAiProvider(routedOptions.getProvider());
                    usageRecord.setAiModel(routedOptions.getModel());
                    usageRecord.setPromptTokens(promptTokens);
                    usageRecord.setCompletionTokens(completionTokens);
                    usageRecord.setResponseDurationMs(System.currentTimeMillis() - llmCallStart);
                    usageRecord.setResponseTimestamp(System.currentTimeMillis());
                    usageRecorder.record(usageRecord);

                    if (promptTokens > 0) {
                        tokenEstimator.record(messagesAtCallTime, promptTokens);
                    }
                }

                // Plan 187 Phase 1 LLM-turn checkpoint (design §5.4a "after
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

                // Plan 187 intra-execution persistence (same plan 183
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
                publishEvent(AgentEventType.LLM_RESPONSE_RECEIVED, sessionId, agentName, llmPayload);

                invokeHooks(AgentLifecyclePoint.POST_REASONING, ctx, agentName, null, null);

                String outputContent = assistantMsg.getContent() != null ? assistantMsg.getContent() : "";
                GuardrailResult outputGuardrailResult = contentGuardrail.check(GuardrailDirection.OUTPUT, outputContent, ctx);
                if (outputGuardrailResult.isBlock()) {
                    String blockReason = ((GuardrailResult.BlockResult) outputGuardrailResult).getReason();
                    ctx.addMessage(ChatToolResponseMessage.error(
                            "guardrail-block-output", "guardrail",
                            "Output blocked by content guardrail: " + (blockReason != null ? blockReason : "unspecified")));
                    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
                    continue;
                }
                if (outputGuardrailResult.isModify()) {
                    String modifiedContent = ((GuardrailResult.ModifyResult) outputGuardrailResult).getContent();
                    assistantMsg.setContent(modifiedContent);
                }

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

                // Plan 189 Phase 1: resolve the per-session memory store from
                // the provider (when wired). When the provider is null
                // (executor constructed outside the engine for testing, or
                // explicitly opted out), the store stays null and memory tools
                // fail fast at execution time with a descriptive error.
                IAiMemoryStore memoryStore = memoryStoreProvider != null && sessionId != null
                        ? memoryStoreProvider.getOrCreate(sessionId)
                        : null;

                AgentToolExecuteContext toolExecCtx = new AgentToolExecuteContext(
                        resolveWorkDir(agentModel),
                        Collections.emptyMap(),
                        0L,
                        null,
                        null,
                        null,
                        engine,
                        messenger,
                        sessionId,
                        agentName,
                        computeEffectiveAllowedTools(agentModel, ctx),
                        computeEffectivePathRoots(agentModel, ctx),
                        computeEffectivePathRules(agentModel, ctx),
                        memoryStore);

                // The workDir string used for action-fingerprint computation
                // (design §6.3). Resolved once per iteration so all dispatch-loop
                // consultations/recordings within this iteration share the same
                // value.
                String fingerprintWorkDir = resolveWorkDirString(agentModel);

                List<ChatToolCall> allowedCalls = new ArrayList<>();

                dispatchLoop:
                for (ChatToolCall chatToolCall : assistantMsg.getToolCalls()) {
                    chatToolCall = toolCallRepairer.repair(chatToolCall, ctx);

                    String toolName = chatToolCall.getName();

                    publishEvent(AgentEventType.TOOL_CALL_STARTED, sessionId, agentName,
                            Map.of("toolName", toolName,
                                    "iteration", ctx.getCurrentIteration()));

                    // Layer 3 post-denial-guard consultation (design §6.3 / L3-7):
                    // before the Layer 1 checks, consult the guard to detect
                    // blind retries. If the action's fingerprint is already in
                    // the session's denied set, deny here and skip all Layer
                    // 1/2/3 checks (saving token budget and preventing repeated
                    // denials from inflating the ledger count). With the shipped
                    // PassThroughPostDenialGuard default this always returns
                    // null (backward compatible — no spurious denials).
                    DenialResult postDenialResult = postDenialGuard.checkBeforeDispatch(
                            sessionId, toolName, chatToolCall.getArguments(), fingerprintWorkDir);
                    if (postDenialResult != null) {
                        String denyMessage = postDenialResult.getMessage() != null
                                ? postDenialResult.getMessage()
                                : "Repeated same denied action";
                        auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                                AuditDecision.DENY, denyMessage, "layer3_post_denial_guard",
                                postDenialResult.getActionFingerprint(), System.currentTimeMillis()));
                        publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                                Map.of("toolName", toolName != null ? toolName : "",
                                        "reason", denyMessage,
                                        "denialReason", postDenialResult.getReason().name(),
                                        "suggestedNextStep", postDenialResult.getSuggestedNextStep().name()));

                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                denyMessage + " (suggested: " + postDenialResult.getSuggestedNextStep() + ")");
                        ctx.addMessage(toolResponse);
                        if (handleDenialAndCheckThreshold(sessionId, toolName,
                                DenialLayerSource.LAYER3_POST_DENIAL_GUARD, denyMessage,
                                "layer3_post_denial_guard", ctx, agentName,
                                chatToolCall, fingerprintWorkDir)) {
                            break dispatchLoop;
                        }
                        continue;
                    }

                    ToolAccessResult accessResult = toolAccessChecker.checkAccess(toolName, ctx);
                    auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                            accessResult.isAllowed() ? AuditDecision.ALLOW : AuditDecision.DENY,
                            accessResult.getReason(), accessResult.getMatchedRule(), null,
                            System.currentTimeMillis()));
                    if (!accessResult.isAllowed()) {
                        publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                                Map.of("toolName", toolName,
                                        "reason", accessResult.getReason() != null ? accessResult.getReason() : ""));

                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Access denied: " + (accessResult.getReason() != null ? accessResult.getReason() : "hardcoded deny"));
                        ctx.addMessage(toolResponse);
                        if (handleDenialAndCheckThreshold(sessionId, toolName,
                                DenialLayerSource.LAYER1_TOOL_ACCESS, accessResult.getReason(),
                                accessResult.getMatchedRule(), ctx, agentName,
                                chatToolCall, fingerprintWorkDir)) {
                            break dispatchLoop;
                        }
                        continue;
                    }

                    Permission perm = permissionProvider.resolve(toolName, agentName, sessionId);
                    auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                            perm.isAllowed() ? AuditDecision.ALLOW : AuditDecision.DENY,
                            perm.getReason(), perm.getMatchedRuleId(), null,
                            System.currentTimeMillis()));
                    if (!perm.isAllowed()) {
                        publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                                Map.of("toolName", toolName,
                                        "reason", perm.getReason() != null ? perm.getReason() : ""));

                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Permission denied: " + (perm.getReason() != null ? perm.getReason() : "access denied"));
                        ctx.addMessage(toolResponse);
                        if (handleDenialAndCheckThreshold(sessionId, toolName,
                                DenialLayerSource.LAYER1_PERMISSION, perm.getReason(),
                                perm.getMatchedRuleId(), ctx, agentName,
                                chatToolCall, fingerprintWorkDir)) {
                            break dispatchLoop;
                        }
                        continue;
                    }

                    String pathDenied = checkPathAccess(chatToolCall, ctx, sessionId, agentName);
                    if (pathDenied != null) {
                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Path access denied: " + pathDenied);
                        ctx.addMessage(toolResponse);
                        if (handleDenialAndCheckThreshold(sessionId, toolName,
                                DenialLayerSource.LAYER1_PATH_ACCESS, pathDenied,
                                "path_access_checker", ctx, agentName,
                                chatToolCall, fingerprintWorkDir)) {
                            break dispatchLoop;
                        }
                        continue;
                    }

                    // Layer 2 consultation (design §5.1/§5.3): after the Layer 1
                    // checks pass, derive the LevelHints, resolve the
                    // SecurityLevel, and consult the channel × level permission
                    // matrix. On denial: audit + event + error response, mirroring
                    // the Layer 1 deny path. NoOp/PassThrough defaults allow
                    // everything (backward compatible). The resolved level is
                    // carried forward to the Layer 3 consultation so it is not
                    // resolved twice.
                    SecurityConsultationOutcome layer2 = checkLayer2Consultation(
                            chatToolCall, ctx, sessionId, agentName, agentModel);
                    if (layer2.isDenied()) {
                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Security policy denied: " + layer2.getDenialReason());
                        ctx.addMessage(toolResponse);
                        if (handleDenialAndCheckThreshold(sessionId, toolName,
                                DenialLayerSource.LAYER2_SECURITY_POLICY, layer2.getDenialReason(),
                                "layer2_permission_matrix", ctx, agentName,
                                chatToolCall, fingerprintWorkDir)) {
                            break dispatchLoop;
                        }
                        continue;
                    }

                    // Layer 3 consultation (design §6.1/§8): after the Layer 2
                    // matrix allows, consult the approval gate with the resolved
                    // security level + tool-call context. On denial: audit + event
                    // + error response, mirroring the Layer 1/2 deny paths.
                    // DefaultApprovalGate default approves STANDARD/ELEVATED and
                    // defense-in-depth denies RESTRICTED (plan 199).
                    String layer3Denied = checkLayer3Approval(
                            layer2.getResolvedLevel(), toolName, ctx, sessionId, agentName);
                    if (layer3Denied != null) {
                        ChatToolResponseMessage toolResponse = ChatToolResponseMessage.error(
                                chatToolCall.getId(),
                                toolName,
                                "Approval denied: " + layer3Denied);
                        ctx.addMessage(toolResponse);
                        if (handleDenialAndCheckThreshold(sessionId, toolName,
                                DenialLayerSource.LAYER3_APPROVAL_GATE, layer3Denied,
                                "layer3_approval_gate", ctx, agentName,
                                chatToolCall, fingerprintWorkDir)) {
                            break dispatchLoop;
                        }
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
                    List<CompletableFuture<ToolCallOutput>> futures = new ArrayList<>();
                    for (ChatToolCall chatToolCall : allowedCalls) {
                        AiToolCall aiToolCall = new AiToolCall();
                        aiToolCall.setToolName(chatToolCall.getName());
                        aiToolCall.setInput(chatToolCall.getArgumentsText());

                        futures.add(toolManager.callTool(chatToolCall.getName(), aiToolCall, toolExecCtx)
                                .thenApply(result -> new ToolCallOutput(chatToolCall, result)));
                    }

                    @SuppressWarnings("unchecked")
                    CompletableFuture<ToolCallOutput>[] futuresArray = futures.toArray(new CompletableFuture[0]);
                    CompletableFuture.allOf(futuresArray).join();

                    for (CompletableFuture<ToolCallOutput> f : futuresArray) {
                        ToolCallOutput output = f.join();
                        ChatToolCall chatToolCall = output.chatToolCall;
                        AiToolCallResult toolResult = output.result;
                        String toolName = chatToolCall.getName();

                        invokeHooks(AgentLifecyclePoint.PRE_ACTING, ctx, agentName, toolName, chatToolCall.getId());

                        String toolStatus;
                        ChatToolResponseMessage toolResponse;
                        if ("success".equals(toolResult.getStatus()) && toolResult.getError() == null) {
                            String resultText = toolResult.getOutput() != null ? toolResult.getOutput().getBody() : "";
                            resultText = resultText != null ? resultText : "";
                            resultText = ToolResultTruncator.truncateIfAllowed(
                                    resultText,
                                    ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                                    toolName);
                            toolResponse = ChatToolResponseMessage.fromToolCall(chatToolCall, resultText);
                            toolStatus = "success";
                        } else {
                            String errorMsg = toolResult.getError() != null ? toolResult.getError().getBody() : "unknown error";
                            toolResponse = ChatToolResponseMessage.error(
                                    chatToolCall.getId(),
                                    chatToolCall.getName(),
                                    errorMsg != null ? errorMsg : "unknown error");
                            toolStatus = "error";
                        }

                        HookResult beforeResult = invokeHooks(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED,
                                ctx, agentName, toolName, chatToolCall.getId());
                        if (beforeResult instanceof HookResult.ReenterResult) {
                            int count = reentryCounters.getOrDefault(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, 0);
                            if (count >= DEFAULT_MAX_REENTRIES) {
                                LOG.warn("Re-entry limit ({}) reached at BEFORE_TOOL_RESULT_PROCESSED, forcing PassResult",
                                        DEFAULT_MAX_REENTRIES);
                            } else {
                                reentryCounters.put(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, count + 1);
                                String reenterMsg = ((HookResult.ReenterResult) beforeResult).getMessage();
                                ctx.addMessage(ChatToolResponseMessage.fromToolCall(chatToolCall,
                                        reenterMsg != null ? reenterMsg : "hook re-enter"));
                                break;
                            }
                        }

                        ctx.addMessage(toolResponse);

                        // Layer 3-4 checkpoint recording (design §5.4a
                        // "tool execution after" trigger point): after the tool
                        // result is added to the context and before the next
                        // LLM call, record a TOOL_EXECUTION checkpoint capturing
                        // the tool-call payload and context-size snapshot. With
                        // the shipped NoOpCheckpoint default this is a no-op;
                        // with the ToolExecutionCheckpoint functional impl the
                        // checkpoint is stored in-memory for save→retrieve
                        // round-trip validation and crash/restart recovery.
                        checkpointManager.saveCheckpoint(Checkpoint.of(
                                sessionId,
                                sessionId != null
                                        ? sessionId + ":tool:" + chatToolCall.getId() + ":" + execStartTime + ":" + checkpointSeq[0]
                                        : "anon:tool:" + chatToolCall.getId() + ":" + execStartTime + ":" + checkpointSeq[0],
                                checkpointSeq[0],
                                System.currentTimeMillis(),
                                CheckpointType.TOOL_EXECUTION,
                                toolName,
                                chatToolCall.getId(),
                                chatToolCall.getArgumentsText(),
                                toolResponse.getContent(),
                                ctx.getMessages().size(),
                                ctx.getTokensUsed()));
                        checkpointSeq[0]++;

                        // Plan 183 Phase 1 intra-execution persistence: after
                        // the checkpoint is written, synchronize the session's
                        // message list with the live ctx.getMessages() and
                        // persist via sessionStore.save. This makes the
                        // session file carry all messages produced up to the
                        // last completed tool call, so a crash mid-execution
                        // leaves a restorable state. With the
                        // InMemorySessionStore default save is a no-op
                        // (in-memory readers share the live reference), so
                        // this is transparent to existing behaviour.
                        if (sessionStore != null) {
                            AgentSession persisted = sessionStore.get(sessionId);
                            if (persisted != null) {
                                persisted.replaceMessages(ctx.getMessages());
                                sessionStore.save(persisted);
                            }
                        }

                        invokeHooks(AgentLifecyclePoint.POST_ACTING, ctx, agentName, toolName, chatToolCall.getId());

                        HookResult afterResult = invokeHooks(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED,
                                ctx, agentName, toolName, chatToolCall.getId());
                        if (afterResult instanceof HookResult.ReenterResult) {
                            int count = reentryCounters.getOrDefault(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, 0);
                            if (count >= DEFAULT_MAX_REENTRIES) {
                                LOG.warn("Re-entry limit ({}) reached at AFTER_TOOL_RESULT_PROCESSED, forcing PassResult",
                                        DEFAULT_MAX_REENTRIES);
                            } else {
                                reentryCounters.put(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, count + 1);
                                break;
                            }
                        }

                        publishEvent(AgentEventType.TOOL_CALL_COMPLETED, sessionId, agentName,
                                Map.of("toolName", chatToolCall.getName(),
                                        "status", toolStatus));
                    }
                }

                if (ctx.isCancelRequested()) {
                    handleCancellation(ctx, sessionId, agentName);
                    break;
                }

                ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
            }

            if (ctx.getStatus() == AgentExecStatus.running) {
                ctx.setStatus(AgentExecStatus.completed);
            }

            // Post-loop bookkeeping (design §6.2): a paused session must NOT
            // publish EXECUTION_COMPLETED or run POST_CALL hooks, consistent
            // with cancelled / forced_stopped / escalated. The session is
            // suspended, not finished.
            if (ctx.getStatus() != AgentExecStatus.cancelled
                    && ctx.getStatus() != AgentExecStatus.forced_stopped
                    && ctx.getStatus() != AgentExecStatus.escalated
                    && ctx.getStatus() != AgentExecStatus.paused) {
                invokeHooks(AgentLifecyclePoint.POST_CALL, ctx, agentName, null, null);

                Map<String, Object> completedPayload = new HashMap<>();
                completedPayload.put("totalIterations", ctx.getCurrentIteration());
                completedPayload.put("totalTokensUsed", ctx.getTokensUsed());
                completedPayload.put("durationMs", System.currentTimeMillis() - ctx.getStartTimeMs());
                publishEvent(AgentEventType.EXECUTION_COMPLETED, sessionId, agentName, completedPayload);
            }

        } catch (Exception e) {
            if (ctx.isCancelRequested()) {
                Thread.currentThread().interrupt();
                handleCancellation(ctx, sessionId, agentName);
            } else {
                ctx.setStatus(AgentExecStatus.failed);
                ctx.setLastError(e.toString());

                invokeOnError(ctx, agentName);
                publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
            }
        }

        return CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx));
    }

    private void handleCancellation(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.cancelled);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", ctx.getCancelReason() != null ? ctx.getCancelReason() : "");
        publishEvent(AgentEventType.SESSION_CANCELLED, sessionId, agentName, payload);
    }

    /**
     * Layer 3 denial-ledger dispatch-path integration (design §6.2 / §6.3 / §8).
     * Called at every deny checkpoint (Layer 1 / 2 / 3 + post-denial-guard
     * consultation — six deny paths) after the existing audit + event + error
     * response. Records the denial into the ledger, records the denied
     * action's fingerprint into the {@link IPostDenialGuard} (design §6.3, so
     * a subsequent blind retry is detectable by the pre-Layer-1
     * consultation), then inspects the returned ledger outcome to decide
     * whether the session has reached the denial threshold.
     *
     * <p>On threshold exceeded: marks the session as {@link AgentExecStatus#paused},
     * records an {@link AuditEvent} (DENY + reason {@code "denial threshold exceeded"}
     * + matched rule {@code "layer3_denial_ledger"}), and publishes a
     * {@link AgentEventType#SESSION_PAUSED} event. The caller then
     * {@code break}s out of the dispatch for-loop.
     *
     * <p>The fingerprint-guard recording forms a closed loop with the
     * pre-Layer-1 consultation: a guard-deny is itself recorded back to the
     * guard, preventing "retry the guard-deny result" loops (design §6.3
     * recording-after-every-deny, including the guard's own deny).
     *
     * <p>With the shipped {@link NoOpDenialLedger} /
     * {@link PassThroughPostDenialGuard} defaults this is a no-op
     * pass-through that always returns {@code false} (backward compatible —
     * no spurious pauses, no fingerprint tracking).
     *
     * @param chatToolCall   the denied tool call (used to extract arguments
     *                       for the fingerprint); never null
     * @param fingerprintWorkDir the workDir string used for fingerprint
     *                       computation; may be null
     * @return {@code true} if the denial threshold has been reached and the
     *         dispatch loop should abort; {@code false} to continue with the
     *         next tool call
     */
    private boolean handleDenialAndCheckThreshold(String sessionId, String toolName,
                                                  DenialLayerSource layerSource, String reason,
                                                  String matchedRule, AgentExecutionContext ctx,
                                                  String agentName,
                                                  ChatToolCall chatToolCall, String fingerprintWorkDir) {
        // Record the denied action's fingerprint into the post-denial guard
        // (design §6.3) so a subsequent blind retry is detectable by the
        // pre-Layer-1 consultation. With the PassThroughPostDenialGuard
        // default this is a no-op (0 overhead).
        postDenialGuard.recordDeniedAction(sessionId, toolName,
                extractArguments(chatToolCall), fingerprintWorkDir);

        DenialRecord record = DenialRecord.of(
                sessionId, toolName, layerSource, reason, matchedRule,
                System.currentTimeMillis());
        DenialRecordOutcome outcome = denialLedger.recordDenial(record);
        if (!outcome.isThresholdExceeded()) {
            return false;
        }
        ctx.setStatus(AgentExecStatus.paused);
        auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                AuditDecision.DENY, "denial threshold exceeded (count=" + outcome.getCount() + ")",
                "layer3_denial_ledger", null, System.currentTimeMillis()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("toolName", toolName != null ? toolName : "");
        payload.put("layerSource", layerSource.name());
        payload.put("denialCount", outcome.getCount());
        payload.put("reason", reason != null ? reason : "");
        publishEvent(AgentEventType.SESSION_PAUSED, sessionId, agentName, payload);
        return true;
    }

    /**
     * Build the composite model identity key ({@code provider:model}) from a
     * {@link ChatOptions} instance, as returned by {@code RoutingResult.getOptions()}.
     * Null provider/model are normalized to empty strings so the key is always
     * non-null and comparable (plan 205 / L2-21). This is the model identity
     * used to detect switches between ReAct iterations.
     */
    private static String buildModelKey(ChatOptions options) {
        String provider = options.getProvider() != null ? options.getProvider() : "";
        String model = options.getModel() != null ? options.getModel() : "";
        return provider + ":" + model;
    }

    /**
     * Plan 207 (L3-2): sleep for the retry-policy-computed backoff delay.
     * A zero/negative delay is a no-op (the policy opted for immediate
     * retry). An interrupted sleep propagates as an {@link NopAiAgentException}
     * wrapping the {@link InterruptedException} (no silent swallow — Minimum
     * Rules #24) and re-sets the interrupt flag so upper layers honour it.
     */
    private static void sleepBackoff(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new NopAiAgentException(
                    "LLM retry backoff sleep interrupted: delayMs=" + delayMs, ie);
        }
    }

    /**
     * Extract the arguments map from a tool call for fingerprint computation.
     * Returns an empty map when the tool call carries no arguments.
     */
    private static Map<String, Object> extractArguments(ChatToolCall chatToolCall) {
        Map<String, Object> args = chatToolCall.getArguments();
        return args != null ? args : Collections.emptyMap();
    }

    /**
     * Resolve the workDir as a String for action-fingerprint computation
     * (design §6.3). Returns null when the agent model declares no workDir.
     */
    private static String resolveWorkDirString(AgentModel agentModel) {
        String workDir = agentModel.getWorkDir();
        return (workDir != null && !workDir.trim().isEmpty()) ? workDir : null;
    }

    /**
     * Mark the session as paused by the denial ledger and emit the
     * {@link AgentEventType#SESSION_PAUSED} event. Used at the ReAct-loop
     * iteration start when {@code denialLedger.isPaused(sessionId)} returns
     * {@code true} (e.g. a prior iteration's threshold-abort carried over).
     *
     * <p>Does not re-record a denial or increment the count — the session is
     * already over threshold. Only the state transition + event are emitted.
     */
    private void handleSessionPaused(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.paused);
        int count = denialLedger.getDenialCount(sessionId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("denialCount", count);
        payload.put("reason", "denial threshold exceeded (prior iteration)");
        publishEvent(AgentEventType.SESSION_PAUSED, sessionId, agentName, payload);
        LOG.warn("Session paused by denial ledger: session={}, denialCount={}", sessionId, count);
    }

    /**
     * Layer 4 forced-stop hard protection (design §7.2 Layer 4 / §7.3). Uses the
     * calibrated {@link ITokenEstimator}'s <b>pre-call</b> estimate: if the
     * estimated pending request exceeds {@code maxContextTokens *
     * forcedStopPercent} (default 0.9), forced stop fires.
     */
    private boolean shouldForceStop(AgentExecutionContext ctx) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        double forcedStopPercent = CompactConfig.defaults().getForcedStopPercent();
        long estimate = tokenEstimator.estimateTokens(ctx.getMessages());
        if (estimate > maxContextTokens * forcedStopPercent) {
            LOG.warn("Forced-stop triggered: pre-call estimate {} exceeds {}% of maxContextTokens {}. session={}",
                    estimate, forcedStopPercent, maxContextTokens, ctx.getSessionId());
            return true;
        }
        return false;
    }

    private void handleForcedStop(AgentExecutionContext ctx, String sessionId, String agentName,
                                  int[] checkpointSeq) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        long estimate = tokenEstimator.estimateTokens(ctx.getMessages());

        // Best-effort final summary: run the compaction pipeline (Layer 1 -> 2 -> 3)
        // so a final summary/tail is retained for the record. Never fails the agent.
        try {
            performCompaction(ctx, agentName, checkpointSeq);
        } catch (Exception e) {
            LOG.warn("Final-summary compaction during forced stop failed, continuing with tail retention. session={}",
                    sessionId, e);
        }

        ctx.setStatus(AgentExecStatus.forced_stopped);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "context-window-overflow");
        payload.put("estimatedTokens", estimate);
        payload.put("maxContextTokens", maxContextTokens);
        payload.put("forcedStopPercent", CompactConfig.defaults().getForcedStopPercent());
        publishEvent(AgentEventType.FORCED_STOP, sessionId, agentName, payload);
    }

    private boolean shouldTriggerCompaction(AgentExecutionContext ctx) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        if (ctx.getTokensUsed() > maxContextTokens * DEFAULT_TRIGGER_TOKEN_PERCENT) {
            return true;
        }
        return ctx.getMessages().size() > DEFAULT_TRIGGER_MAX_MESSAGES;
    }

    private long resolveMaxContextTokens(AgentExecutionContext ctx) {
        if (ctx.getChatOptionsModel() != null && ctx.getChatOptionsModel().getMaxTokens() != null) {
            return ctx.getChatOptionsModel().getMaxTokens();
        }
        return DEFAULT_MAX_CONTEXT_TOKENS;
    }

    private void performCompaction(AgentExecutionContext ctx, String agentName, int[] checkpointSeq) {
        CompactConfig config = CompactConfig.defaults();

        CompactionContext compactCtx = new CompactionContext(
                new ArrayList<>(ctx.getMessages()),
                config,
                ctx.getSessionId(),
                agentName,
                ctx,
                tokenEstimator
        );

        invokeHooks(AgentLifecyclePoint.PRE_COMPACT, ctx, agentName, null, null);

        CompactionResult result = contextCompactor.compact(compactCtx);

        invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, agentName, null, null);

        if (result.getCompactedMessages() != null) {
            if (result.getCompactedMessages().isEmpty()) {
                LOG.warn("Compactor returned empty compactedMessages for session {}, skipping replacement",
                        ctx.getSessionId());
            } else if (result.getTokensAfter() < result.getTokensBefore()) {
                ctx.getMessages().clear();
                ctx.getMessages().addAll(result.getCompactedMessages());
                ctx.setTokensUsed(ctx.getTokensUsed() - (result.getTokensBefore() - result.getTokensAfter()));
                LOG.info("Context compacted: tokens {} -> {}, retained {} messages for session {}",
                        result.getTokensBefore(), result.getTokensAfter(),
                        result.getRetainedMessageCount(), ctx.getSessionId());

                // Plan 187 Phase 2 compaction checkpoint (design §5.4a
                // "snapshot on compaction" trigger point): after the context
                // has actually been compacted (messages replaced + token
                // accounting adjusted), record a COMPACTION checkpoint
                // marking the new post-compaction baseline. Emitted only when
                // real compaction happened — the NoOpContextCompactor default
                // returns compactedMessages == null, so no spurious checkpoint
                // is produced. With the shipped NoOpCheckpoint default the
                // saveCheckpoint call itself is a no-op.
                String compactSummary = "compacted: " + result.getTokensBefore() + "->"
                        + result.getTokensAfter() + " tokens, " + result.getRetainedMessageCount()
                        + " messages";
                String compactionSessionId = ctx.getSessionId();
                long compactExecStart = ctx.getStartTimeMs();
                checkpointManager.saveCheckpoint(Checkpoint.of(
                        compactionSessionId,
                        compactionSessionId != null
                                ? compactionSessionId + ":compact:" + compactExecStart + ":" + checkpointSeq[0]
                                : "anon:compact:" + compactExecStart + ":" + checkpointSeq[0],
                        checkpointSeq[0],
                        System.currentTimeMillis(),
                        CheckpointType.COMPACTION,
                        null,
                        null,
                        null,
                        compactSummary,
                        ctx.getMessages().size(),
                        ctx.getTokensUsed()));
                checkpointSeq[0]++;

                // Plan 187 intra-execution persistence: compaction replaced
                // the message list, so the persisted session must be
                // re-synchronized. Without this, a crash after compaction
                // would restore pre-compaction messages and break the
                // checkpoint.messageCount <= session.messageCount invariant.
                if (sessionStore != null) {
                    AgentSession persistedCompacted = sessionStore.get(compactionSessionId);
                    if (persistedCompacted != null) {
                        persistedCompacted.replaceMessages(ctx.getMessages());
                        sessionStore.save(persistedCompacted);
                    }
                }
            }
        }
    }

    private HookResult invokeHooks(AgentLifecyclePoint point, AgentExecutionContext ctx,
                                   String agentName, String toolName, String toolCallId) {
        List<IAgentLifecycleHook> hooks = hookRegistry.getHooks(point, agentName);
        if (hooks.isEmpty()) {
            return HookResult.PassResult.instance();
        }

        for (IAgentLifecycleHook hook : hooks) {
            try {
                HookContext hookCtx = new HookContext(point, ctx);
                hookCtx.setToolName(toolName);
                hookCtx.setToolCallId(toolCallId);
                HookResult result = hook.onEvent(hookCtx);

                if (result instanceof HookResult.ReenterResult) {
                    if (point != AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED
                            && point != AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED) {
                        throw new NopAiAgentException(
                                "ReenterResult is only valid at re-entrant hook points (BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED), got: " + point);
                    }
                    return result;
                }

                if (result.isVeto()) {
                    return result;
                }
            } catch (Exception e) {
                if (point == AgentLifecyclePoint.ON_ERROR) {
                    LOG.warn("on_error hook failed, using engine default error handling", e);
                } else if (point.name().startsWith("PRE_") || point.name().startsWith("BEFORE_")) {
                    LOG.error("before_* hook at {} failed", point, e);
                    throw e;
                } else {
                    LOG.warn("after_* hook at {} failed, continuing", point, e);
                }
            }
        }
        return HookResult.PassResult.instance();
    }

    private void invokeOnError(AgentExecutionContext ctx, String agentName) {
        try {
            invokeHooks(AgentLifecyclePoint.ON_ERROR, ctx, agentName, null, null);
        } catch (Exception e) {
            LOG.warn("ON_ERROR hook invocation failed", e);
        }
    }

    private String vetoReason(HookResult result) {
        if (result instanceof HookResult.VetoResult) {
            return ((HookResult.VetoResult) result).getReason();
        }
        return "vetoed";
    }

    private void publishEvent(AgentEventType type, String sessionId, String agentName,
                              Map<String, Object> payload) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.create(type, sessionId, agentName, payload));
        }
    }

    private void publishErrorEvent(AgentEventType type, String sessionId, String agentName,
                                   String error) {
        if (eventPublisher != null) {
            eventPublisher.publish(AgentEvent.createError(type, sessionId, agentName, error));
        }
    }

    private String checkPathAccess(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                   String sessionId, String agentName) {
        Map<String, Object> arguments = chatToolCall.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String pathValue = (String) value;
            if (pathValue == null || pathValue.trim().isEmpty()) {
                continue;
            }

            PathAccessResult pathResult = pathAccessChecker.checkAccess(pathValue, ctx);
            if (!pathResult.isAllowed()) {
                auditLogger.log(new AuditEvent(sessionId, agentName, null, chatToolCall.getName(),
                        AuditDecision.DENY, pathResult.getReason(), pathResult.getMatchedRule(),
                        pathValue, System.currentTimeMillis()));
                publishEvent(AgentEventType.PATH_ACCESS_DENIED, sessionId, agentName,
                        Map.of("path", pathValue,
                                "reason", pathResult.getReason() != null ? pathResult.getReason() : ""));
                return pathResult.getReason() != null ? pathResult.getReason() : "path access denied";
            }
        }

        return null;
    }

    /**
     * Layer 2 dispatch-path consultation (design §5.1/§5.3/§8). Produces the
     * {@link LevelHints} for the tool call, resolves the {@link SecurityLevel}
     * via {@link ISecurityLevelResolver}, and consults the channel × level
     * {@link IPermissionMatrix} using the context's {@code channelKind} and
     * {@code principal}.
     *
     * <p>On denial: records an {@link AuditEvent} (DENY + reason + matched rule
     * {@code "layer2_permission_matrix"}) and publishes a
     * {@code TOOL_CALL_DENIED} event, then carries the auditable reason in the
     * returned outcome — mirroring the Layer 1 deny path. The caller turns a
     * denied outcome into a {@code ChatToolResponseMessage.error(...)} and
     * skips the call.
     *
     * <p>On allow: the outcome carries a null denial reason and the tool call
     * proceeds. With the shipped {@link NoOpSecurityLevelResolver} /
     * {@link PassThroughPermissionMatrix} defaults this always allows
     * (backward compatible — no spurious denials).
     *
     * <p>The resolved {@link SecurityLevel} is always carried in the outcome so
     * the Layer 3 consultation can reuse it without resolving twice.
     *
     * @return the consultation outcome (resolved level + optional denial
     *         reason); never null
     */
    private SecurityConsultationOutcome checkLayer2Consultation(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                                                String sessionId, String agentName, AgentModel agentModel) {
        String toolName = chatToolCall.getName();

        File workDir = resolveWorkDir(agentModel);
        LevelHints hints = levelHintsProducer.produce(toolName, chatToolCall.getArguments(), workDir, ctx);
        SecurityLevel level = securityLevelResolver.resolve(toolName, hints);

        ChannelKind channel = ctx.getChannelKind();
        Principal principal = ctx.getPrincipal();
        MatrixDecision decision = permissionMatrix.check(channel, principal, level);
        if (decision.isDenied()) {
            String reason = decision.getReason() != null
                    ? decision.getReason()
                    : "security level " + level + " denied for channel " + channel;
            String auditContext = "channel=" + (channel != null ? channel.name() : "unknown")
                    + ",level=" + level.name();
            auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                    AuditDecision.DENY, reason, "layer2_permission_matrix",
                    auditContext, System.currentTimeMillis()));
            publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                    Map.of("toolName", toolName,
                            "reason", decision.getReason() != null ? decision.getReason() : "",
                            "securityLevel", level.name(),
                            "channel", channel != null ? channel.name() : "unknown"));
            return SecurityConsultationOutcome.denied(level, reason);
        }
        return SecurityConsultationOutcome.allowed(level);
    }

    /**
     * Layer 3 dispatch-path consultation (design §6.1/§8). After the Layer 2
     * matrix allows, consults the {@link IApprovalGate} with the resolved
     * {@link SecurityLevel} and the tool-call context.
     *
     * <p>On denial: records an {@link AuditEvent} (DENY + reason + matched rule
     * {@code "layer3_approval_gate"}) and publishes a
     * {@code TOOL_CALL_DENIED} event, then returns the auditable reason —
     * mirroring the Layer 1/2 deny paths. The caller turns the non-null return
     * into a {@code ChatToolResponseMessage.error(...)} and skips the call.
     *
     * <p>On approval: returns {@code null} and the tool call proceeds. With the
     * shipped {@link DefaultApprovalGate} default this approves
     * STANDARD/ELEVATED and defense-in-depth denies RESTRICTED (plan 199).
     *
     * @param level the security level already resolved during the Layer 2
     *              consultation (reused to avoid a second resolve)
     * @return the denial reason, or {@code null} when the call is approved
     */
    private String checkLayer3Approval(SecurityLevel level, String toolName,
                                       AgentExecutionContext ctx, String sessionId, String agentName) {
        ChannelKind channel = ctx.getChannelKind();
        Principal principal = ctx.getPrincipal();
        ApprovalDecision decision = approvalGate.requestApproval(
                level, toolName, channel, principal, sessionId, agentName);
        if (decision.isDenied()) {
            String reason = decision.getReason() != null
                    ? decision.getReason()
                    : "approval denied (kind=" + decision.getDenialKind() + ") for level " + level;
            String auditContext = "channel=" + (channel != null ? channel.name() : "unknown")
                    + ",level=" + level.name()
                    + ",kind=" + decision.getDenialKind();
            auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                    AuditDecision.DENY, reason, "layer3_approval_gate",
                    auditContext, System.currentTimeMillis()));
            publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                    Map.of("toolName", toolName,
                            "reason", decision.getReason() != null ? decision.getReason() : "",
                            "securityLevel", level.name(),
                            "denialKind", decision.getDenialKind() != null ? decision.getDenialKind().name() : "",
                            "channel", channel != null ? channel.name() : "unknown"));
            return reason;
        }
        return null;
    }

    private GuardrailResult checkInputGuardrail(AgentExecutionContext ctx) {
        String inputContent = extractLastUserContent(ctx);
        GuardrailResult result = contentGuardrail.check(GuardrailDirection.INPUT, inputContent, ctx);
        if (result.isModify()) {
            String modifiedContent = ((GuardrailResult.ModifyResult) result).getContent();
            List<ChatMessage> messages = ctx.getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof ChatUserMessage) {
                    messages.get(i).setContent(modifiedContent);
                    break;
                }
            }
        }
        return result;
    }

    private String extractLastUserContent(AgentExecutionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ChatUserMessage) {
                return messages.get(i).getContent();
            }
        }
        return "";
    }

    private List<ChatToolDefinition> buildToolDefinitions(AgentModel agentModel) {
        List<ChatToolDefinition> defs = new ArrayList<>();
        if (agentModel.getTools() == null)
            return defs;

        for (String toolName : agentModel.getTools()) {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null)
                continue;

            defs.add(toToolDefinition(toolModel));
        }
        return defs;
    }

    /**
     * Compute the current agent's <b>effective (clamped)</b> allowed tool set,
     * propagated to engine-aware tools (e.g. {@code call-agent}) via
     * {@link AgentToolExecuteContext#getAllowedTools()} for sub-agent
     * permission inheritance (design §4.4: 工具权限 = 父权限 ∩ 子配置).
     *
     * <p>Clamping rule:
     * <ul>
     *   <li>If an incoming parent constraint is present in the execution
     *       context metadata (key
     *       {@link ParentPermissionConstraint#METADATA_KEY}), the effective set
     *       is the intersection of the parent's allowed tool set and the
     *       current agent's <b>declared</b> tool set
     *       ({@link AgentModel#getTools()}). This is what makes nested
     *       delegation safe: a middle agent B's effective set is already
     *       clamped to A's constraint, so when B delegates to C, C inherits
     *       B's clamped set rather than B's declared set.</li>
     *   <li>If no parent constraint is present (top-level agent), the
     *       effective set equals the declared set unchanged.</li>
     * </ul>
     *
     * @return the effective tool set; never null (an agent with no declared
     *         tools yields an empty set)
     */
    private Set<String> computeEffectiveAllowedTools(AgentModel agentModel, AgentExecutionContext ctx) {
        Set<String> declared = agentModel.getTools();
        if (declared == null) {
            declared = Collections.emptySet();
        }

        ParentPermissionConstraint parentConstraint = null;
        if (ctx.getMetadata() != null) {
            Object raw = ctx.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
            if (raw instanceof ParentPermissionConstraint) {
                parentConstraint = (ParentPermissionConstraint) raw;
            }
        }

        if (parentConstraint == null) {
            return new HashSet<>(declared);
        }

        Set<String> parentAllowed = parentConstraint.getAllowedTools();
        Set<String> effective = new HashSet<>(declared);
        effective.retainAll(parentAllowed);
        return effective;
    }

    /**
     * Resolve the agent's declared {@code workDir} (from
     * {@link AgentModel#getWorkDir()}) to a {@link File}, or {@code null} when
     * no workDir is declared (ABSENT). Replaces the hardcoded {@code new File(".")}
     * so each agent carries its own declared working directory — distinct agents
     * with distinct {@code workDir} values produce distinct effective path roots
     * rather than the shared JVM CWD (design §4.4).
     */
    private File resolveWorkDir(AgentModel agentModel) {
        String workDir = agentModel.getWorkDir();
        if (workDir == null || workDir.trim().isEmpty()) {
            return null;
        }
        return new File(workDir);
    }

    /**
     * Compute the current agent's <b>effective (clamped)</b> allowed path roots,
     * propagated to engine-aware tools (e.g. {@code call-agent}) via
     * {@link AgentToolExecuteContext#getAllowedPathRoots()} for sub-agent
     * path-permission inheritance (design §4.4:
     * 文件权限 = 父权限 ∩ 子配置).
     *
     * <p>Clamping rule (three-valued ABSENT/PRESENT semantics):
     * <ul>
     *   <li>If an incoming parent constraint is present with PRESENT path roots,
     *       the effective roots are the subset of the agent's own declared roots
     *       (from {@code workDir}) that fall UNDER any incoming parent root. This
     *       is what makes nested delegation safe: a middle agent B's effective
     *       roots are already clamped within A's scope, so when B delegates to C,
     *       C inherits a scope within A's. If none of the agent's own roots are
     *       under any incoming root, the effective set is empty (PRESENT({}) =
     *       deny all paths — maximum restriction, e.g. when the agent declares a
     *       workDir outside the parent's scope).</li>
     *   <li>If an incoming parent constraint is present but its path roots are
     *       ABSENT (null), the effective roots equal the agent's own declared
     *       roots (ABSENT acts as identity).</li>
     *   <li>If no parent constraint is present (top-level agent), the effective
     *       roots equal the agent's own declared roots
     *       (PRESENT({normalized workDir}) or ABSENT when workDir is null).</li>
     * </ul>
     *
     * @return the effective path roots; {@code null} (ABSENT) when the agent has
     *         no declared path scope and no incoming parent roots; a non-null
     *         Set (PRESENT, possibly empty) when path-scope confinement is active
     */
    private Set<String> computeEffectivePathRoots(AgentModel agentModel, AgentExecutionContext ctx) {
        Set<String> ownRoots = computeOwnDeclaredPathRoots(agentModel);

        ParentPermissionConstraint parentConstraint = null;
        if (ctx.getMetadata() != null) {
            Object raw = ctx.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
            if (raw instanceof ParentPermissionConstraint) {
                parentConstraint = (ParentPermissionConstraint) raw;
            }
        }

        if (parentConstraint == null || !parentConstraint.hasPathRoots()) {
            // No incoming parent roots (ABSENT) → effective = own declared roots
            // (ABSENT or PRESENT)
            return ownRoots;
        }

        Set<String> incomingRoots = parentConstraint.getAllowedPathRoots();

        if (ownRoots == null) {
            // No own declared roots → inherit parent's roots (ABSENT is identity)
            return new HashSet<>(incomingRoots);
        }

        // Both PRESENT → keep own roots that are under any incoming root
        Set<String> effective = new HashSet<>();
        for (String ownRoot : ownRoots) {
            if (isUnderAnyRoot(ownRoot, incomingRoots)) {
                effective.add(ownRoot);
            }
        }
        return effective;
    }

    /**
     * Compute the agent's own declared path roots from its {@code workDir}.
     *
     * @return {@code null} (ABSENT) when no workDir is declared; a non-null Set
     *         containing the normalized workDir as the single root when declared
     */
    private Set<String> computeOwnDeclaredPathRoots(AgentModel agentModel) {
        String workDir = agentModel.getWorkDir();
        if (workDir == null || workDir.trim().isEmpty()) {
            return null;
        }
        String normalized = DefaultPathAccessChecker.normalizePathStatic(workDir);
        if (normalized == null) {
            return null;
        }
        return new HashSet<>(Collections.singleton(normalized));
    }

    /**
     * Compute the current agent's <b>effective (clamped)</b> allowed path rules,
     * propagated to engine-aware tools (e.g. {@code call-agent}) via
     * {@link AgentToolExecuteContext#getAllowedPathRules()} for sub-agent
     * path-rule inheritance (design §4.4).
     *
     * <p>Rule-chain accumulation (design §4.3/§4.4):
     * <ul>
     *   <li>If no incoming parent constraint or parent rules ABSENT → effective
     *       = own declared rules (from {@link AgentModel#getPathRules()}).</li>
     *   <li>If incoming parent rules PRESENT → effective = accumulated chain
     *       (incoming parent rules + own declared rules, parent rules first).
     *       This accumulated chain is evaluated with deny-wins by the
     *       sub-agent's {@link ParentConstrainedPathAccessChecker}.</li>
     * </ul>
     *
     * @return the effective path-rule chain; {@code null} (ABSENT) when the
     *         agent has no own path-rules and no incoming parent rules; a
     *         non-null List (PRESENT) when path-rule confinement is active
     */
    private java.util.List<io.nop.ai.agent.model.PathRuleModel> computeEffectivePathRules(
            AgentModel agentModel, AgentExecutionContext ctx) {
        java.util.List<io.nop.ai.agent.model.PathRuleModel> ownRules = agentModel.getPathRules();
        boolean ownHasRules = ownRules != null && !ownRules.isEmpty();

        ParentPermissionConstraint parentConstraint = null;
        if (ctx.getMetadata() != null) {
            Object raw = ctx.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
            if (raw instanceof ParentPermissionConstraint) {
                parentConstraint = (ParentPermissionConstraint) raw;
            }
        }

        boolean parentHasRules = parentConstraint != null && parentConstraint.hasPathRules();

        if (!ownHasRules && !parentHasRules) {
            return null;
        }

        if (!parentHasRules) {
            return ownRules;
        }

        // Accumulate: incoming parent rules + own declared rules
        java.util.List<io.nop.ai.agent.model.PathRuleModel> incomingRules =
                parentConstraint.getAllowedPathRules();
        java.util.List<io.nop.ai.agent.model.PathRuleModel> effective = new ArrayList<>(incomingRules);
        if (ownHasRules) {
            effective.addAll(ownRules);
        }
        return effective;
    }

    /**
     * Check whether a normalized path root is "under" any of the given
     * (possibly non-normalized) root set. Uses the same normalization as
     * {@link DefaultPathAccessChecker#normalizePathStatic(String)}.
     */
    private boolean isUnderAnyRoot(String normalizedPath, Set<String> roots) {
        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) {
                continue;
            }
            String normalizedRoot = DefaultPathAccessChecker.normalizePathStatic(root);
            if (normalizedRoot == null) {
                continue;
            }
            if (normalizedPath.equals(normalizedRoot)) {
                return true;
            }
            String rootWithSlash = normalizedRoot.endsWith("/")
                    ? normalizedRoot : normalizedRoot + "/";
            if (normalizedPath.startsWith(rootWithSlash)) {
                return true;
            }
        }
        return false;
    }

    private static ChatToolDefinition toToolDefinition(AiToolModel toolModel) {
        Map<String, Object> parameters = ToolSchemaConverter.convert(toolModel.getSchema());
        if (parameters != null) {
            return ChatToolDefinition.of(toolModel.getName(), toolModel.getDescription(), parameters);
        }
        return ChatToolDefinition.of(toolModel.getName(), toolModel.getDescription());
    }

    /**
     * Consult registered talents once at execution setup (before the first LLM
     * call). For each talent whose admission gate passes, fire its activation
     * callback, then merge its dynamic instruction fragment into the
     * system-prompt context and its dynamic tool set (resolved through the
     * existing {@code IToolManager} pipeline) into {@code toolDefs}. All merges
     * are additive; an inactive talent is excluded only because its gate
     * explicitly returned false.
     */
    private void consultTalents(AgentExecutionContext ctx, List<ChatToolDefinition> toolDefs) {
        if (talents.isEmpty()) {
            return;
        }

        List<String> instructions = new ArrayList<>();
        List<String> talentToolNames = new ArrayList<>();

        for (ITalent talent : talents) {
            if (talent.isSupported(ctx)) {
                talent.onAttach(ctx);
                String instruction = talent.getInstruction(ctx);
                if (instruction != null && !instruction.isEmpty()) {
                    instructions.add(instruction);
                }
                List<String> tools = talent.getTools(ctx);
                if (tools != null) {
                    talentToolNames.addAll(tools);
                }
            }
        }

        for (String toolName : talentToolNames) {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null) {
                LOG.warn("Talent-provided tool not found in registry, skipping: toolName={} session={}",
                        toolName, ctx.getSessionId());
            } else {
                toolDefs.add(toToolDefinition(toolModel));
            }
        }

        if (!instructions.isEmpty()) {
            injectSystemInstruction(ctx, String.join("\n\n", instructions));
        }
    }

    private void injectSystemInstruction(AgentExecutionContext ctx, String instruction) {
        List<ChatMessage> messages = ctx.getMessages();
        int insertAt = 0;
        while (insertAt < messages.size() && messages.get(insertAt) instanceof ChatSystemMessage) {
            insertAt++;
        }
        messages.add(insertAt, new ChatSystemMessage(instruction));
    }

    /**
     * Consult the skill resolver once at execution setup (before the first LLM
     * call, alongside {@link #consultTalents}). Resolves the agent's
     * {@code availableSkills} / {@code requiredSkills} declarations against the
     * registered {@link ISkillProvider}, then merges:
     * <ul>
     *   <li>Skill instruction fragments (goals) → system-prompt context via
     *       {@link #injectSystemInstruction} (additive to agent prompt and
     *       talent instructions).</li>
     *   <li>Skill tool-name dependencies → resolved through
     *       {@code IToolManager.loadTool()} and merged into {@code toolDefs}
     *       (additive to agent + talent tools, same access-check pipeline — no
     *       parallel tool type). Missing tools are skipped with a warning, same
     *       pattern as talent tools.</li>
     *   <li>resourceScope → logged at DEBUG for observability (not enforced in
     *       phase 1).</li>
     * </ul>
     *
     * <p>A missing {@code requiredSkill} propagates the resolver's
     * {@link NopAiAgentException} fail-fast before any LLM call. With the
     * default {@link NoOpSkillProvider} (or no skills declared), this method
     * resolves an empty assembly and injects nothing — backward compatible.
     */
    private void consultSkills(AgentExecutionContext ctx, AgentModel agentModel,
                               List<ChatToolDefinition> toolDefs) {
        SkillResolver resolver = new SkillResolver(skillProvider);
        SkillAssemblyResult assembly = resolver.resolve(agentModel);

        if (assembly.isEmpty()) {
            return;
        }

        for (String toolName : assembly.getToolDependencies()) {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null) {
                LOG.warn("Skill-provided tool not found in registry, skipping: toolName={} skillNames={} session={}",
                        toolName, assembly.getActivatedSkillNames(), ctx.getSessionId());
            } else {
                toolDefs.add(toToolDefinition(toolModel));
            }
        }

        List<String> instructions = assembly.getInstructions();
        if (!instructions.isEmpty()) {
            injectSystemInstruction(ctx, String.join("\n\n", instructions));
        }

        if (LOG.isDebugEnabled() && !assembly.getResourceScope().isEmpty()) {
            LOG.debug("Skill assembly resourceScope (collected for tracing, not enforced in phase 1): "
                    + "scope={} activatedSkills={} session={}",
                    assembly.getResourceScope(), assembly.getActivatedSkillNames(), ctx.getSessionId());
        }
    }

    private ChatOptions buildChatOptions(ChatOptionsModel model, List<ChatToolDefinition> toolDefs) {
        ChatOptions options = new ChatOptions();
        if (model != null) {
            if (model.getProvider() != null)
                options.setProvider(model.getProvider());
            if (model.getModel() != null)
                options.setModel(model.getModel());
            if (model.getTemperature() != null)
                options.setTemperature(model.getTemperature());
            if (model.getTopP() != null)
                options.setTopP(model.getTopP());
            if (model.getMaxTokens() != null)
                options.setMaxTokens(model.getMaxTokens());
            if (model.getTopK() != null)
                options.setTopK(model.getTopK());
            if (model.getStop() != null)
                options.setStop(model.getStop());
        }
        if (!toolDefs.isEmpty()) {
            options.setTools(toolDefs);
            options.autoToolChoice();
        }
        return options;
    }

    private static class ToolCallOutput {
        final ChatToolCall chatToolCall;
        final AiToolCallResult result;

        ToolCallOutput(ChatToolCall chatToolCall, AiToolCallResult result) {
            this.chatToolCall = chatToolCall;
            this.result = result;
        }
    }

    /**
     * Immutable result of the Layer 2 security consultation. Carries the
     * resolved {@link SecurityLevel} (always set, so the Layer 3 approval
     * consultation can reuse it without a second resolve) and an optional
     * denial reason (non-null when the Layer 2 matrix denied the call).
     */
    private static final class SecurityConsultationOutcome {
        private final SecurityLevel resolvedLevel;
        private final String denialReason;

        private SecurityConsultationOutcome(SecurityLevel resolvedLevel, String denialReason) {
            this.resolvedLevel = resolvedLevel;
            this.denialReason = denialReason;
        }

        static SecurityConsultationOutcome allowed(SecurityLevel level) {
            return new SecurityConsultationOutcome(level, null);
        }

        static SecurityConsultationOutcome denied(SecurityLevel level, String reason) {
            return new SecurityConsultationOutcome(level, reason);
        }

        SecurityLevel getResolvedLevel() {
            return resolvedLevel;
        }

        String getDenialReason() {
            return denialReason;
        }

        boolean isDenied() {
            return denialReason != null;
        }
    }
}
