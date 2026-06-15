package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.Layer2TurnPruningStrategy;
import io.nop.ai.agent.compact.Layer3FullSummaryStrategy;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.compact.PipelineCompactor;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.DefaultLevelHintsProducer;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
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
import io.nop.ai.agent.security.ILevelHintsProducer;
import io.nop.ai.agent.security.NoOpAuditLogger;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.ParentConstrainedPathAccessChecker;
import io.nop.ai.agent.security.ParentConstrainedToolAccessChecker;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.RuleBasedPathAccessChecker;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.ISkillCurator;
import io.nop.ai.agent.skill.NoOpSkillCurator;
import io.nop.ai.agent.skill.NoOpSkillProvider;
import io.nop.ai.agent.skill.SkillCurationResult;
import io.nop.ai.agent.skill.SkillModel;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultAgentEngine implements IAgentEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final DefaultAgentEventPublisher eventPublisher;
    private final ISessionStore sessionStore;
    private final IPermissionProvider permissionProvider;
    private final IToolAccessChecker toolAccessChecker;
    private final IPathAccessChecker pathAccessChecker;
    private final IContentGuardrail contentGuardrail;
    private final IModelRouter modelRouter;
    private final IContextCompactor contextCompactor;
    private final ITokenEstimator tokenEstimator;
    private List<ITalent> talents = java.util.Collections.emptyList();
    private ISkillProvider skillProvider = NoOpSkillProvider.noOp();
    private ISkillCurator skillCurator = NoOpSkillCurator.noOp();
    private IToolCallRepairer toolCallRepairer;
    private IAgentMessenger messenger = NoOpAgentMessenger.noOp();
    private IPermissionMatrix permissionMatrix = PassThroughPermissionMatrix.passThrough();
    private ISecurityLevelResolver securityLevelResolver = NoOpSecurityLevelResolver.noOp();
    private ILevelHintsProducer levelHintsProducer = new DefaultLevelHintsProducer();
    private IApprovalGate approvalGate = AutoApproveGate.autoApprove();
    private IDenialLedger denialLedger = NoOpDenialLedger.noOp();
    private IPostDenialGuard postDenialGuard = PassThroughPostDenialGuard.passThrough();
    // Plan 194 (AUDIT-13-02): audit logger defaults to Slf4jAuditLogger so the
    // engine produces a visible audit trail out-of-the-box (tool decisions are
    // logged to SLF4J INFO) instead of silently discarding audit events via
    // NoOpAuditLogger. Integrators can replace it via setAuditLogger (e.g. to
    // write audit events to a database).
    private IAuditLogger auditLogger = new Slf4jAuditLogger();
    private ICheckpointManager checkpointManager = NoOpCheckpoint.noOp();
    private IMemoryStoreProvider memoryStoreProvider = new InMemoryMemoryStoreProvider();
    // Plan 192: max token budget for budgeted working-memory auto-injection into
    // the system prompt. Shipped default gives memory a reasonable share of the
    // context without dominating it. A value <= 0 disables injection (explicit
    // opt-out / backward-compatible escape hatch).
    private int memoryInjectionBudgetTokens = 1024;

    private final ConcurrentHashMap<String, CancelHandle> runningExecutions = new ConcurrentHashMap<>();

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager) {
        this(chatService, toolManager, new InMemorySessionStore());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                              ISessionStore sessionStore) {
        this(chatService, toolManager, sessionStore, new AllowAllPermissionProvider());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore, IPermissionProvider permissionProvider) {
        this(chatService, toolManager, sessionStore, permissionProvider, new DefaultToolAccessChecker());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore, IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker) {
        this(chatService, toolManager, sessionStore, permissionProvider,
                toolAccessChecker, new DefaultPathAccessChecker());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore,
                               IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker,
                               IPathAccessChecker pathAccessChecker) {
        this(chatService, toolManager, sessionStore, permissionProvider,
                toolAccessChecker, pathAccessChecker, NoOpContentGuardrail.noOp());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore, IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker,
                               IContentGuardrail contentGuardrail) {
        this(chatService, toolManager, sessionStore, permissionProvider,
                toolAccessChecker, pathAccessChecker, contentGuardrail,
                PassThroughModelRouter.passThrough());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore, IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker,
                               IContentGuardrail contentGuardrail, IModelRouter modelRouter) {
        this(chatService, toolManager, sessionStore, permissionProvider,
                toolAccessChecker, pathAccessChecker, contentGuardrail,
                modelRouter, new MicroCompressionCompactor());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore, IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker,
                               IContentGuardrail contentGuardrail, IModelRouter modelRouter,
                               IContextCompactor contextCompactor) {
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.eventPublisher = new DefaultAgentEventPublisher();
        this.sessionStore = sessionStore;
        this.permissionProvider = permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider();
        this.toolAccessChecker = toolAccessChecker != null ? toolAccessChecker : new DefaultToolAccessChecker();
        this.pathAccessChecker = pathAccessChecker != null ? pathAccessChecker : new DefaultPathAccessChecker();
        this.contentGuardrail = contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp();
        this.modelRouter = modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough();
        this.contextCompactor = contextCompactor != null ? contextCompactor : defaultPipelineCompactor(chatService);
        this.tokenEstimator = CalibratedTokenEstimator.defaultInstance();
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger);
    }

    /**
     * Build the default 5-layer pipeline orchestrator composing the layers that
     * are available. Layer 1 (micro-compression), Layer 2 (turn pruning) and
     * Layer 3 (LLM summarization, with graceful fallback when the LLM is
     * unavailable) are all composed here. The {@code compressionModel} is read
     * from the per-agent {@link io.nop.ai.agent.session.CompactConfig} at
     * runtime.
     */
    private static IContextCompactor defaultPipelineCompactor(IChatService chatService) {
        return new PipelineCompactor(
                new MicroCompressionCompactor(),
                new Layer2TurnPruningStrategy(),
                new Layer3FullSummaryStrategy(chatService)
        );
    }

    /**
     * Emit a one-time WARN per engine instance when the resolved Layer 1
     * checkers are {@code AllowAll*} instances, or when the resolved audit
     * logger is a {@code NoOpAuditLogger} — i.e. the integrator has
     * explicitly opted into an insecure downgrade (open box: dangerous tools
     * and sensitive paths have no protection; audit events are silently
     * discarded). The default constructor chain now resolves to
     * {@code Default*} checkers and {@code Slf4jAuditLogger} (secure by
     * default, plans 193 + 194), so these WARNs fire only when an integrator
     * deliberately passes an {@code AllowAll*} / {@code NoOpAuditLogger}
     * instance — making the security downgrade visible rather than silent.
     * See design {@code nop-ai-agent-security-and-permissions.md} §4.6 / §4.7.
     *
     * <p>The WARN covers the Layer 1 tool/path checkers (plan 193) and the
     * audit logger (plan 194). Layer 2/3 components default-downgrade
     * enumeration is a successor extension point ([13-4]).
     *
     * <p>Fail-loud: the WARN is unconditionally emitted via {@code LOG.warn}
     * when an {@code AllowAll*} / {@code NoOpAuditLogger} instance is
     * detected — never silently swallowed or written as an empty body.
     */
    private static void warnIfInsecureDefaults(IToolAccessChecker toolChecker,
                                               IPathAccessChecker pathChecker,
                                               IAuditLogger auditLogger) {
        if (toolChecker instanceof AllowAllToolAccessChecker) {
            LOG.warn("DefaultAgentEngine constructed with AllowAllToolAccessChecker: "
                    + "dangerous tools (bash/write-file/delete-file/move-file/patch-file/apply-delta/"
                    + "http-request/graphql-query) are NOT blocked. This is an insecure default. "
                    + "To restore secure-by-default behaviour, do not pass an AllowAllToolAccessChecker "
                    + "to the constructor — the default already uses DefaultToolAccessChecker.");
        }
        if (pathChecker instanceof AllowAllPathAccessChecker) {
            LOG.warn("DefaultAgentEngine constructed with AllowAllPathAccessChecker: "
                    + "sensitive paths (~/.ssh/, ~/.aws/, /etc/, .env, id_rsa, ...) are NOT blocked. "
                    + "This is an insecure default. To restore secure-by-default behaviour, do not pass "
                    + "an AllowAllPathAccessChecker to the constructor — the default already uses "
                    + "DefaultPathAccessChecker.");
        }
        if (auditLogger instanceof NoOpAuditLogger) {
            LOG.warn("DefaultAgentEngine constructed with NoOpAuditLogger: "
                    + "audit events are being DISCARDED — tool decisions (deny/approve/override) "
                    + "leave NO record. This is an insecure downgrade of the audit trail. "
                    + "To restore secure-by-default behaviour, do not pass a NoOpAuditLogger "
                    + "to setAuditLogger — the default already uses Slf4jAuditLogger. "
                    + "For a custom audit sink (e.g. database), supply your own IAuditLogger.");
        }
    }

    public IAgentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    /**
     * Register the set of dynamic-admission talents ({@link ITalent}) passed to
     * the executor on the ReAct path. Composition via the executor Builder —
     * no constructor chain change. Default is an empty list (no talents), so
     * engine behaviour is unchanged unless talents are explicitly registered.
     */
    public void setTalents(List<ITalent> talents) {
        this.talents = talents != null ? talents : java.util.Collections.emptyList();
    }

    /**
     * Register the {@link ISkillProvider} passed to the executor on the ReAct
     * path. Composition via the executor Builder — no constructor chain change.
     * Default is {@link NoOpSkillProvider} (discovers zero skills), so engine
     * behaviour is unchanged unless a provider is explicitly registered.
     */
    public void setSkillProvider(ISkillProvider skillProvider) {
        this.skillProvider = skillProvider != null ? skillProvider : NoOpSkillProvider.noOp();
    }

    /**
     * Register the {@link ISkillCurator} used for on-demand skill quality
     * evaluation. Composition via this setter — no constructor chain change.
     * Default is {@link NoOpSkillCurator} (returns an empty curation result),
     * so engine behaviour is unchanged unless a curator is explicitly
     * registered. The curator is an on-demand analytical tool, not invoked
     * during {@code ReActAgentExecutor.execute()} (design
     * {@code skill-system-design.md} §5.5).
     */
    public void setSkillCurator(ISkillCurator skillCurator) {
        this.skillCurator = skillCurator != null ? skillCurator : NoOpSkillCurator.noOp();
    }

    /**
     * On-demand skill curation: source the skill registry from the registered
     * {@link ISkillProvider}, invoke the registered {@link ISkillCurator}, and
     * return the {@link SkillCurationResult} synchronously. The curator is
     * advisory and non-mutating — it evaluates skill definitions and produces
     * recommendations, never modifies them.
     *
     * <p>If no {@code ISkillProvider} is registered (defaults to
     * {@code NoOpSkillProvider}), curation returns an empty success result
     * (zero skills to assess).
     *
     * @return the curation result (never null); carries per-skill assessments,
     *         registry-level observations, and metadata with a success/fail
     *         marker
     */
    public SkillCurationResult curateSkills() {
        java.util.Collection<SkillModel> skills = skillProvider.getSkills();
        return skillCurator.curate(skills);
    }

    /**
     * Register the {@link IToolCallRepairer} passed to the executor on the ReAct
     * path. Composition via the executor Builder — no constructor chain change.
     * Default is {@code null}, which causes the executor to default to
     * {@code NoOpToolCallRepairer.INSTANCE}, so engine behaviour is unchanged
     * unless a repairer is explicitly registered. Set to a
     * {@link io.nop.ai.agent.repair.ChainRepairer} to opt in to 4-stage
     * functional repair.
     */
    public void setToolCallRepairer(IToolCallRepairer toolCallRepairer) {
        this.toolCallRepairer = toolCallRepairer;
    }

    /**
     * Register the {@link IAgentMessenger} used for inter-agent messaging
     * (request-response and fire-and-forget between agent endpoints). Composition
     * via this setter — no constructor chain change. Default is
     * {@link NoOpAgentMessenger#noOp()} (send is a debug-log no-op; request
     * fails fast with {@code UnsupportedOperationException}), so engine
     * behaviour is unchanged unless a messenger is explicitly registered. Set to
     * a {@link io.nop.ai.agent.message.LocalAgentMessenger} backed by the
     * platform {@code LocalMessageService} to opt in to inter-agent messaging.
     *
     * <p>The messenger is accessible to future tools (e.g. {@code call-agent},
     * {@code send-message}) and the actor runtime (L4-8) via
     * {@link #getMessenger()}.
     */
    public void setMessenger(IAgentMessenger messenger) {
        this.messenger = messenger != null ? messenger : NoOpAgentMessenger.noOp();
    }

    /**
     * Return the {@link IAgentMessenger} wired into this engine, or the
     * {@link NoOpAgentMessenger} default if none was explicitly set.
     */
    public IAgentMessenger getMessenger() {
        return messenger;
    }

    /**
     * Register the {@link IPermissionMatrix} used for channel × security-level
     * permission decisions (design §5.3). Composition via this setter — no
     * constructor chain change. Default is {@link PassThroughPermissionMatrix}
     * (all channels allow all levels), so engine behaviour is unchanged unless
     * a matrix is explicitly registered.
     *
     * <p>The dispatch-path consultation (calling {@code matrix.check(...)} in
     * the ReAct / tool-dispatch path) is deferred to the L2-13 successor
     * ({@code ISecurityLevelResolver} produces the {@code SecurityLevel} input).
     * The pass-through default makes this wiring transparent to runtime
     * behaviour.
     */
    public void setPermissionMatrix(IPermissionMatrix permissionMatrix) {
        this.permissionMatrix = permissionMatrix != null ? permissionMatrix : PassThroughPermissionMatrix.passThrough();
    }

    /**
     * Return the {@link IPermissionMatrix} wired into this engine, or the
     * {@link PassThroughPermissionMatrix} default if none was explicitly set.
     */
    public IPermissionMatrix getPermissionMatrix() {
        return permissionMatrix;
    }

    /**
     * Register the {@link ISecurityLevelResolver} used for action-kind × hints
     * security-level resolution (design §5.1). Composition via this setter — no
     * constructor chain change. Default is {@link NoOpSecurityLevelResolver}
     * (all operations resolve to STANDARD, equivalent to no classification), so
     * engine behaviour is unchanged unless a resolver is explicitly registered.
     *
     * <p>The dispatch-path consultation (calling {@code resolver.resolve(...)}
     * in the ReAct / tool-dispatch path) is deferred to a successor plan
     * (requires {@code AgentExecutionContext} channelKind/principal fields + a
     * {@code io.nop.ai.agent.security.LevelHints} runtime-production chain). The
     * NoOp default makes this wiring transparent to runtime behaviour.
     */
    public void setSecurityLevelResolver(ISecurityLevelResolver securityLevelResolver) {
        this.securityLevelResolver = securityLevelResolver != null
                ? securityLevelResolver
                : NoOpSecurityLevelResolver.noOp();
    }

    /**
     * Return the {@link ISecurityLevelResolver} wired into this engine, or the
     * {@link NoOpSecurityLevelResolver} default if none was explicitly set.
     */
    public ISecurityLevelResolver getSecurityLevelResolver() {
        return securityLevelResolver;
    }

    /**
     * Register the {@link ILevelHintsProducer} used to derive the auditable
     * {@link io.nop.ai.agent.security.LevelHints} for each tool call on the
     * dispatch path (design §5.1). Composition via this setter — no constructor
     * chain change. Default is {@link DefaultLevelHintsProducer} (a functional
     * implementation that produces semantically-distinct hints), so engine
     * behaviour is unchanged unless a producer is explicitly registered.
     *
     * <p>The producer feeds the {@link ISecurityLevelResolver}; both are
     * consulted together in the dispatch-path Layer 2 step.
     */
    public void setLevelHintsProducer(ILevelHintsProducer levelHintsProducer) {
        this.levelHintsProducer = levelHintsProducer != null
                ? levelHintsProducer
                : new DefaultLevelHintsProducer();
    }

    /**
     * Return the {@link ILevelHintsProducer} wired into this engine, or the
     * {@link DefaultLevelHintsProducer} default if none was explicitly set.
     */
    public ILevelHintsProducer getLevelHintsProducer() {
        return levelHintsProducer;
    }

    /**
     * Register the {@link IApprovalGate} used for Layer 3 human-approval
     * governance (design §6.1). Composition via this setter — no constructor
     * chain change. Default is {@link AutoApproveGate} (all requests
     * auto-approved with approver "auto"), so engine behaviour is unchanged
     * unless a functional gate is explicitly registered.
     *
     * <p>The gate is consulted in the dispatch loop after the Layer 2
     * permission matrix allows a tool call and before the call is added to
     * {@code allowedCalls}. A denial records an {@code AuditEvent} (DENY +
     * reason + matched rule {@code "layer3_approval_gate"}) and produces a
     * {@code ChatToolResponseMessage.error(...)}, mirroring the Layer 1 / 2
     * deny paths.
     */
    public void setApprovalGate(IApprovalGate approvalGate) {
        this.approvalGate = approvalGate != null ? approvalGate : AutoApproveGate.autoApprove();
    }

    /**
     * Return the {@link IApprovalGate} wired into this engine, or the
     * {@link AutoApproveGate} default if none was explicitly set.
     */
    public IApprovalGate getApprovalGate() {
        return approvalGate;
    }

    /**
     * Register the {@link IDenialLedger} used for Layer 3 denial-counting and
     * threshold-pause governance (design §6.2). Composition via this setter —
     * no constructor chain change. Default is {@link NoOpDenialLedger} (no
     * counting, no pausing), so engine behaviour is unchanged unless a
     * functional ledger is explicitly registered.
     *
     * <p>The ledger is consulted in the dispatch loop at every deny checkpoint
     * (Layer 1 / 2 / 3 — five deny paths): each denial is recorded, and the
     * returned {@code thresholdExceeded} flag decides whether to abort the
     * dispatch loop and mark the session as {@code paused}. On the next
     * ReAct-loop iteration start, {@code IDenialLedger.isPaused(...)} is
     * consulted: a paused session aborts the ReAct loop before any further
     * LLM call.
     */
    public void setDenialLedger(IDenialLedger denialLedger) {
        this.denialLedger = denialLedger != null ? denialLedger : NoOpDenialLedger.noOp();
    }

    /**
     * Return the {@link IDenialLedger} wired into this engine, or the
     * {@link NoOpDenialLedger} default if none was explicitly set.
     */
    public IDenialLedger getDenialLedger() {
        return denialLedger;
    }

    /**
     * Register the {@link IPostDenialGuard} used for Layer 3 post-denial
     * blind-retry blocking (design §6.3 / L3-7). Composition via this setter
     * — no constructor chain change. Default is
     * {@link PassThroughPostDenialGuard} (no blocking, no recording), so
     * engine behaviour is unchanged unless a functional guard is explicitly
     * registered.
     *
     * <p>The guard is consulted in the dispatch loop before the Layer 1
     * {@code IToolAccessChecker} check for each tool call: if the action's
     * fingerprint is already in the session's denied set (a blind retry),
     * the call is denied before any Layer 1/2/3 check. After every Layer 1/2/3
     * deny (and after the guard's own consultation deny), the denied action's
     * fingerprint is recorded into the guard, so a subsequent blind retry is
     * detectable.
     */
    public void setPostDenialGuard(IPostDenialGuard postDenialGuard) {
        this.postDenialGuard = postDenialGuard != null
                ? postDenialGuard
                : PassThroughPostDenialGuard.passThrough();
    }

    /**
     * Return the {@link IPostDenialGuard} wired into this engine, or the
     * {@link PassThroughPostDenialGuard} default if none was explicitly set.
     */
    public IPostDenialGuard getPostDenialGuard() {
        return postDenialGuard;
    }

    /**
     * Register the {@link IAuditLogger} used to record tool-decision audit
     * events (deny/approve/override) produced on the dispatch path (design
     * §4.7 / plan 194). Composition via this setter — no constructor chain
     * change. Shipped default is {@link Slf4jAuditLogger} (audit events logged
     * to SLF4J INFO), so the engine produces a visible audit trail
     * out-of-the-box. Setting to {@code null} preserves the
     * {@code Slf4jAuditLogger} default.
     *
     * <p>Unlike {@link #setDenialLedger} (whose NoOp default is a planful
     * successor, Layer 3 denial-counting), a {@link NoOpAuditLogger}
     * downgrade discards the audit trail entirely — a security downgrade of
     * an already-shipped secure default. To keep that downgrade visible
     * rather than silent, this setter re-runs {@code warnIfInsecureDefaults}
     * after the assignment, which emits a one-time WARN when a
     * {@code NoOpAuditLogger} instance is detected. This is the actual
     * hit-path for a NoOp downgrade (the constructor-time field defaults to
     * {@code Slf4jAuditLogger}, so the constructor-time check never hits
     * NoOp on the shipped default).
     */
    public void setAuditLogger(IAuditLogger auditLogger) {
        this.auditLogger = auditLogger != null ? auditLogger : new Slf4jAuditLogger();
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger);
    }

    /**
     * Return the {@link IAuditLogger} wired into this engine, or the
     * {@link Slf4jAuditLogger} default if none was explicitly set.
     */
    public IAuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * Register the {@link ICheckpointManager} used for Layer 3-4 checkpoint
     * recording (design §5.4). Composition via this setter — no constructor
     * chain change. Default is {@link NoOpCheckpoint} (no checkpoints
     * recorded), so engine behaviour is unchanged unless a functional manager
     * is explicitly registered.
     *
     * <p>The manager is consulted in the dispatch loop after every tool
     * execution completes: a {@code TOOL_EXECUTION} checkpoint is recorded
     * capturing the tool-call payload and context-size snapshot.
     */
    public void setCheckpointManager(ICheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager != null
                ? checkpointManager
                : NoOpCheckpoint.noOp();
    }

    /**
     * Return the {@link ICheckpointManager} wired into this engine, or the
     * {@link NoOpCheckpoint} default if none was explicitly set.
     */
    public ICheckpointManager getCheckpointManager() {
        return checkpointManager;
    }

    /**
     * Wire the {@link IMemoryStoreProvider} consulted by working-memory tools
     * (read-memory / write-memory / search-memory) via the dispatch loop. The
     * shipped default is an {@link InMemoryMemoryStoreProvider} (working-memory
     * tools work out-of-the-box without any provider configuration). When
     * explicitly set to {@code null} the executor skips memory-store
     * resolution and memory tools fail fast at execution time.
     */
    public void setMemoryStoreProvider(IMemoryStoreProvider memoryStoreProvider) {
        this.memoryStoreProvider = memoryStoreProvider;
    }

    public IMemoryStoreProvider getMemoryStoreProvider() {
        return memoryStoreProvider;
    }

    /**
     * Plan 192: max token budget for budgeted working-memory auto-injection
     * into the system prompt (consumed in {@link #buildBaseExecutionContext}).
     * Shipped default is 1024. A value {@code <= 0} disables injection
     * (explicit opt-out; empty memory is also never injected).
     */
    public void setMemoryInjectionBudgetTokens(int memoryInjectionBudgetTokens) {
        this.memoryInjectionBudgetTokens = memoryInjectionBudgetTokens;
    }

    public int getMemoryInjectionBudgetTokens() {
        return memoryInjectionBudgetTokens;
    }

    /**
     * Test-only accessor for the engine's own tool access checker. Used by
     * integration tests to verify that
     * {@link #resolveEffectiveToolAccessChecker} wraps (or does not wrap) this
     * checker based on request metadata.
     */
    IToolAccessChecker getToolAccessCheckerForTest() {
        return this.toolAccessChecker;
    }

    /**
     * Test-only accessor for the engine's own path access checker. Used by
     * integration tests to verify that
     * {@link #resolveEffectivePathAccessChecker} wraps (or does not wrap) this
     * checker based on request metadata.
     */
    IPathAccessChecker getPathAccessCheckerForTest() {
        return this.pathAccessChecker;
    }

    @Override
    public AgentExecStatus getSessionStatus(String sessionId) {
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException("getSessionStatus failed: session not found: sessionId=" + sessionId);
        }
        return session.getStatus();
    }

    @Override
    public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
        CancelHandle handle = sessionId != null ? runningExecutions.get(sessionId) : null;

        if (handle != null) {
            AgentExecutionContext ctx = handle.context;
            ctx.setCancelRequested(true);
            ctx.setCancelReason(reason);

            AgentSession session = sessionStore.get(sessionId);
            String agentName = session != null ? session.getAgentName() : null;
            publishCancelRequested(sessionId, agentName, reason, forced);

            if (forced) {
                // Plan 197: null-check — during the async-enqueue window the
                // handle is pre-registered but thread is not yet bound to the
                // execution thread. Interrupting null would NPE; interrupting
                // the calling thread (if we pre-bound it) would be wrong.
                Thread t = handle.thread;
                if (t != null) {
                    t.interrupt();
                }
            }
        } else {
            AgentSession session = sessionStore.get(sessionId);
            if (session == null) {
                throw new NopAiAgentException(
                        "cancelSession failed: session not found: sessionId=" + sessionId);
            }
            session.setStatus(AgentExecStatus.cancelled);
            String agentName = session.getAgentName();
            publishCancelRequested(sessionId, agentName, reason, forced);
            publishCancelled(sessionId, agentName, reason);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void publishCancelRequested(String sessionId, String agentName, String reason, boolean forced) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason != null ? reason : "");
        payload.put("forced", forced);
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CANCEL_REQUESTED,
                sessionId, agentName, payload));
    }

    private void publishCancelled(String sessionId, String agentName, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason != null ? reason : "");
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CANCELLED,
                sessionId, agentName, payload));
    }

    @Override
    public CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
        String parentSessionId = request.getSessionId();
        if (parentSessionId == null || parentSessionId.isEmpty()) {
            throw new NopAiAgentException(
                    "forkSession failed: request.sessionId is null or empty, cannot resolve parent session");
        }

        AgentSession parentSession = sessionStore.get(parentSessionId);
        if (parentSession == null) {
            throw new NopAiAgentException(
                    "forkSession failed: parent session not found: parentSessionId=" + parentSessionId);
        }

        Map<String, Object> props = new HashMap<>();
        if (request.getAgentName() != null && !request.getAgentName().isEmpty()) {
            props.put("agentName", request.getAgentName());
        }
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            props.putAll(request.getMetadata());
        }

        String childSessionId = sessionStore.forkSession(parentSessionId, inheritContext, props);

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("parentSessionId", parentSessionId);
        eventPayload.put("childSessionId", childSessionId);
        eventPayload.put("inheritContext", inheritContext);

        AgentSession childSession = sessionStore.get(childSessionId);
        String childAgentName = childSession != null ? childSession.getAgentName() : null;
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_FORKED,
                childSessionId, childAgentName, eventPayload));

        return CompletableFuture.completedFuture(childSessionId);
    }

    /**
     * Plan 197 (AUDIT-14-01): {@code thread} is {@code volatile} and
     * lazily bound. The handle is pre-registered in the synchronous phase
     * (before {@code supplyAsync}) to close the cancel-lost-window — at
     * that point the ForkJoinPool execution thread is not yet known, so
     * {@code thread} is initialized to {@code null}. The lambda updates it
     * to {@code Thread.currentThread()} at entry. {@code cancelSession}
     * null-checks before {@code interrupt()} so a forced cancel during the
     * enqueue window does not interrupt the calling thread.
     */
    private static final class CancelHandle {
        final AgentExecutionContext context;
        volatile Thread thread;

        CancelHandle(AgentExecutionContext context, Thread thread) {
            this.context = context;
            this.thread = thread;
        }
    }

    @Override
    public AgentMessageAck sendMessage(AgentMessageRequest request) {
        String sessionId = resolveSessionId(request.getSessionId());
        CompletableFuture<AgentExecutionResult> future = doExecute(request, sessionId);
        future.exceptionally(ex -> {
            LOG.error("Agent execution failed for agentName={}, sessionId={}: {}",
                    request.getAgentName(), sessionId, ex.getMessage(), ex);
            return null;
        });
        return new AgentMessageAck(sessionId);
    }

    @Override
    public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
        String sessionId = resolveSessionId(request.getSessionId());
        return doExecute(request, sessionId);
    }

    private CompletableFuture<AgentExecutionResult> doExecute(AgentMessageRequest request, String sessionId) {
        AgentModel agentModel = loadAgentModel(request.getAgentName());

        AgentSession session = sessionStore.getOrCreate(sessionId, request.getAgentName());
        int historyCount = session.getMessageCount();

        if (historyCount == 0) {
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CREATED,
                    sessionId, request.getAgentName(), null));
        } else {
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_LOADED,
                    sessionId, request.getAgentName(),
                    java.util.Map.of("historyCount", historyCount)));
        }

        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        if (request.getMetadata() != null) {
            ctx.getMetadata().putAll(request.getMetadata());
        }

        // Propagate the request's channel / principal into the execution context
        // so the dispatch path can consult the Layer 2 security matrix. Both are
        // optional (null = unknown channel / anonymous identity); null inputs are
        // set as-is so downstream consumers see the semantically-correct "unknown".
        ctx.setChannelKind(request.getChannelKind());
        ctx.setPrincipal(request.getPrincipal());

        ctx.addMessage(new ChatUserMessage(request.getUserMessage()));

        IToolAccessChecker effectiveToolAccessChecker = resolveEffectiveToolAccessChecker(request);
        IPathAccessChecker perAgentBase = resolvePerAgentPathChecker(agentModel);
        IPathAccessChecker effectivePathAccessChecker = resolveEffectivePathAccessChecker(request, perAgentBase);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // Plan 197 (AUDIT-14-01): Pre-register the CancelHandle in the
        // synchronous phase (before supplyAsync) so that cancelSession can
        // find it during the async-enqueue window (after execute() returns
        // but before the supplyAsync lambda starts running). putIfAbsent is
        // the atomic dedup guard — a non-null return means another execution
        // is already registered for this session, so we fail-fast instead of
        // silently overwriting the existing handle.
        CancelHandle handle = new CancelHandle(ctx, null);
        CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
        if (existing != null) {
            throw new NopAiAgentException(
                    "doExecute failed: session already executing: sessionId=" + sessionId);
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                session.setStatus(AgentExecStatus.running);

                // Plan 197: bind the execution thread now that the lambda is
                // running. cancelSession(forced=true) reads this volatile field.
                handle.thread = Thread.currentThread();

                AgentExecutionResult result;
                try {
                    result = executor.execute(ctx).toCompletableFuture().join();
                } finally {
                    // Plan 197: value-comparison remove — only remove our own
                    // handle, never another execution's handle (eliminates the
                    // [14-1] mutual-clobber race where the first execution's
                    // finally removes the second execution's handle).
                    runningExecutions.remove(sessionId, handle);
                    session.setStatus(ctx.getStatus());
                }

            // Plan 183 Phase 1: replaceMessages replaces the session's message
            // list with the full ctx.getMessages() (idempotent full-sync). This
            // unifies the intra-execution and post-execution sync paths: both
            // produce the same terminal state (session.messages == ctx
            // messages) without duplicate appends. When the executor ran
            // intra-execution persistence (FileBackedSessionStore), the final
            // replaceMessages here is idempotent — same messages, same result.
            // When no intra-execution persistence ran (InMemorySessionStore),
            // this is the only sync and produces the complete session state.
            session.replaceMessages(ctx.getMessages());

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();
            sessionStore.save(session);

            return result;
        });
        } catch (RuntimeException e) {
            // Plan 197: if supplyAsync itself fails to submit the task
            // (e.g. RejectedExecutionException), clean up the pre-registered
            // handle so a subsequent execute() is not permanently blocked.
            runningExecutions.remove(sessionId, handle);
            throw e;
        }
    }

    /**
     * Build the base {@link AgentExecutionContext} shared by the normal
     * {@code doExecute} path and the {@code resumeSession} path: create the
     * context from the agent model + session, add the system prompt, and replay
     * the session's existing message history. The caller is responsible for
     * appending any new user message ({@code doExecute} appends one;
     * {@code resumeSession} does not — resume is a transparent continuation of
     * the existing conversation, not a new turn).
     * <p>
     * Extracted from {@code doExecute} so resume re-uses the exact same
     * context-building sequence (system prompt + history) without duplicating
     * it.
     */
    private AgentExecutionContext buildBaseExecutionContext(AgentModel agentModel, AgentSession session) {
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, session.getSessionId());

        String systemPrompt = null;
        if (agentModel.getPrompt() != null) {
            systemPrompt = agentModel.getPrompt().getSource();
        }

        // Plan 192: auto-inject budgeted working memory into the system prompt.
        // Memory is a session-level persistent context (like the system prompt),
        // so it is injected here — the single point shared by doExecute (new
        // turn) and resumeSession (recovery continuation). Only non-empty
        // budgeted memory is injected (backward compatible). A null provider
        // (test-only opt-out) or budget <= 0 (explicit opt-out) skips injection
        // without throwing.
        String memorySection = buildBudgetedMemorySection(session.getSessionId());
        if (memorySection != null) {
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                systemPrompt = memorySection;
            } else {
                systemPrompt = systemPrompt + "\n\n" + memorySection;
            }
        }

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ctx.addMessage(new ChatSystemMessage(systemPrompt));
        }

        if (session.getMessageCount() > 0) {
            ctx.getMessages().addAll(session.getMessages());
        }

        return ctx;
    }

    /**
     * Plan 192: resolve the current session's budgeted working memory and
     * format it as a single memory section string. Returns {@code null} when
     * there is nothing to inject (null provider, budget <= 0, empty store, or
     * empty {@code readBudgeted} result) — callers must check for null before
     * appending, so empty memory never produces a system message (backward
     * compatible).
     */
    private String buildBudgetedMemorySection(String sessionId) {
        if (memoryStoreProvider == null || memoryInjectionBudgetTokens <= 0) {
            return null;
        }
        java.util.Map<String, Object> context = new HashMap<>();
        context.put("sessionId", sessionId);
        context.put("source", "system-prompt-auto-injection");

        List<io.nop.ai.agent.memory.AiMemoryItem> items;
        try {
            io.nop.ai.agent.memory.IAiMemoryStore store = memoryStoreProvider.getOrCreate(sessionId);
            items = store.readBudgeted(memoryInjectionBudgetTokens, context);
        } catch (RuntimeException e) {
            LOG.warn("Budgeted memory injection skipped for sessionId={}", sessionId, e);
            return null;
        }

        if (items == null || items.isEmpty()) {
            return null;
        }

        return formatMemorySection(items);
    }

    /**
     * Plan 192: format a non-empty list of budgeted memory items into a single
     * LLM-readable section. The section has a recognizable header (so the LLM
     * and human reviewers can distinguish injected memory from the base system
     * prompt) and one line per item (content is mandatory; key/type are
     * included when present for context).
     */
    static String formatMemorySection(List<io.nop.ai.agent.memory.AiMemoryItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Working Memory\n");
        for (io.nop.ai.agent.memory.AiMemoryItem item : items) {
            sb.append("- ");
            String key = item.getKey();
            String type = item.getType();
            boolean hasMeta = false;
            if (type != null && !type.isEmpty()) {
                sb.append('[').append(type).append("] ");
                hasMeta = true;
            }
            if (key != null && !key.isEmpty()) {
                sb.append('[').append(key).append("] ");
                hasMeta = true;
            }
            sb.append(item.getContent() != null ? item.getContent() : "");
            sb.append('\n');
        }
        // Trim the trailing newline so the section is clean.
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\n') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    @Override
    public CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException(
                    "resumeSession failed: session not found: sessionId=" + sessionId);
        }
        if (session.getStatus() != AgentExecStatus.paused) {
            throw new NopAiAgentException(
                    "resumeSession failed: session is not paused (status=" + session.getStatus()
                            + "), only paused sessions can be resumed: sessionId=" + sessionId);
        }

        String agentName = session.getAgentName();

        // Capture the pre-reset denial count for the audit event before clearing.
        int preResetDenialCount = denialLedger.getDenialCount(sessionId);

        // Clear the pause by resetting the ledger (design §6.2 sticky recovery).
        denialLedger.reset(sessionId);

        // Transition the session back to running before re-execution.
        session.setStatus(AgentExecStatus.running);

        Map<String, Object> resumePayload = new HashMap<>();
        resumePayload.put("approver", approver != null ? approver : "");
        resumePayload.put("reason", reason != null ? reason : "");
        resumePayload.put("preResetDenialCount", preResetDenialCount);
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESUMED,
                sessionId, agentName, resumePayload));

        // Re-execute the session as a transparent continuation: rebuild the
        // context from the agent model + the existing conversation history (NO
        // new user message is appended — resume continues where the paused
        // execution left off, letting the LLM re-plan from the last denied
        // tool-call error response rather than starting a new turn).
        AgentModel agentModel = loadAgentModel(agentName);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        // Resolve the executor with the engine's own checkers (no parent
        // constraint applies on resume — resume is a top-level recovery action,
        // not a sub-agent call). Per-agent path rules are still honoured so the
        // resumed execution is consistent with a normal top-level execution.
        IToolAccessChecker effectiveToolAccessChecker = this.toolAccessChecker;
        IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // Plan 197 (AUDIT-14-01): Pre-register CancelHandle in the synchronous
        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        CancelHandle handle = new CancelHandle(ctx, null);
        CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
        if (existing != null) {
            throw new NopAiAgentException(
                    "resumeSession failed: session already executing: sessionId=" + sessionId);
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                handle.thread = Thread.currentThread();

                AgentExecutionResult result;
                try {
                    result = executor.execute(ctx).toCompletableFuture().join();
                } finally {
                    // Plan 197: value-comparison remove — only remove our own handle.
                    runningExecutions.remove(sessionId, handle);
                    session.setStatus(ctx.getStatus());
                }

            // Plan 183 Phase 1: replaceMessages unifies the post-execution
            // sync with the intra-execution persistence path (see doExecute
            // for the full rationale). Idempotent full-sync — no duplicate
            // appends.
            session.replaceMessages(ctx.getMessages());

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();
            sessionStore.save(session);

            return result;
        });
        } catch (RuntimeException e) {
            // Plan 197: clean up pre-registered handle if supplyAsync fails.
            runningExecutions.remove(sessionId, handle);
            throw e;
        }
    }

    @Override
    public CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopAiAgentException(
                    "restoreSession failed: sessionId must not be null or empty");
        }
        // Plan 197 (AUDIT-14-01): the redundant containsKey check is removed.
        // putIfAbsent below is the atomic dedup guard; the old containsKey was
        // a TOCTOU race (could pass, then another thread registers before
        // putIfAbsent). All three entry points now share the same fail-fast
        // behavior via putIfAbsent.

        // Load from persistent store (FileBackedSessionStore.get returns the
        // persisted session on cache-miss; InMemorySessionStore.get returns
        // null for unknown sessions, which is the correct "no persistent
        // state" signal).
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException(
                    "restoreSession failed: no persistent state found for session "
                            + "(was the session ever persisted, or is the session store in-memory only?): sessionId="
                            + sessionId);
        }

        AgentExecStatus currentStatus = session.getStatus();
        if (isTerminalStatus(currentStatus)) {
            throw new NopAiAgentException(
                    "restoreSession failed: session is in a terminal state (status="
                            + currentStatus + "), only non-terminal sessions can be restored: sessionId="
                            + sessionId);
        }

        String agentName = session.getAgentName();

        // Checkpoint journal consumption (plan 182 investment realized on
        // the restore path): the latest checkpoint provides resume-point
        // metadata + a consistency check (checkpoint.messageCount ≤ persisted
        // session.messageCount, since the checkpoint was written after a tool
        // execution that produced messages now present in the session file).
        // Best-effort: a warning is logged on inconsistency but recovery is
        // not blocked — the persisted session is the source of truth, the
        // checkpoint is a verification supplement, not a message source.
        Checkpoint latestCheckpoint = checkpointManager.getLatestCheckpoint(sessionId);
        String latestCheckpointWatermark = null;
        if (latestCheckpoint != null) {
            latestCheckpointWatermark = latestCheckpoint.getWatermark();
            int checkpointMsgCount = latestCheckpoint.getMessageCount();
            int sessionMsgCount = session.getMessageCount();
            if (checkpointMsgCount > sessionMsgCount) {
                LOG.warn("restoreSession checkpoint consistency warning: checkpoint messageCount {} "
                                + "exceeds persisted session messageCount {} — persisted history may be incomplete. "
                                + "Continuing with best-effort recovery (session is source of truth). session={}",
                        checkpointMsgCount, sessionMsgCount, sessionId);
            }
        }

        // Transition the session back to running before re-execution. A
        // session that was running when the process crashed has status=running
        // in the persisted file; a pending session has status=pending. Both
        // are non-terminal and valid restore candidates.
        session.setStatus(AgentExecStatus.running);

        // Publish the SESSION_RESTORED audit event carrying approver, reason,
        // and latestCheckpointWatermark for audit trail.
        Map<String, Object> restorePayload = new HashMap<>();
        restorePayload.put("approver", approver != null ? approver : "");
        restorePayload.put("reason", reason != null ? reason : "");
        restorePayload.put("latestCheckpointWatermark",
                latestCheckpointWatermark != null ? latestCheckpointWatermark : "");
        restorePayload.put("preRestoreStatus", currentStatus != null ? currentStatus.name() : "");
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESTORED,
                sessionId, agentName, restorePayload));

        // Rebuild the execution context from the agent model + the persisted
        // conversation history (NO new user message — restore continues where
        // the crashed execution left off, letting the LLM re-plan from the
        // last completed tool result rather than starting a new turn).
        AgentModel agentModel = loadAgentModel(agentName);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        IToolAccessChecker effectiveToolAccessChecker = this.toolAccessChecker;
        IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // Plan 197 (AUDIT-14-01): Pre-register CancelHandle in the synchronous
        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        CancelHandle handle = new CancelHandle(ctx, null);
        CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
        if (existing != null) {
            throw new NopAiAgentException(
                    "restoreSession failed: session already executing: sessionId=" + sessionId);
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                handle.thread = Thread.currentThread();

                AgentExecutionResult result;
                try {
                    result = executor.execute(ctx).toCompletableFuture().join();
                } finally {
                    // Plan 197: value-comparison remove — only remove our own handle.
                    runningExecutions.remove(sessionId, handle);
                    session.setStatus(ctx.getStatus());
                }

            // Plan 183 Phase 1: replaceMessages unifies the post-execution
            // sync with the intra-execution persistence path.
            session.replaceMessages(ctx.getMessages());

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();
            sessionStore.save(session);

            return result;
        });
        } catch (RuntimeException e) {
            // Plan 197: clean up pre-registered handle if supplyAsync fails.
            runningExecutions.remove(sessionId, handle);
            throw e;
        }
    }

    /**
     * Plan 184 auto-restore-on-startup batch orchestrator. Discovers every
     * persisted session via {@link ISessionStore#listAllSessions()}, filters
     * to {@code running}/{@code pending} candidates (skipping {@code paused}
     * — governance — and terminal statuses), restores each candidate
     * sequentially by delegating to {@link #restoreSession}, and returns a
     * {@link SessionRestoreSummary}. Per-session failure isolation: a thrown
     * restore is recorded in the failed bucket and the batch continues.
     *
     * <p>See {@link IAgentEngine#restorePendingSessions} for the full
     * contract. The implementation deliberately reuses {@code restoreSession}
     * rather than re-implementing the restore protocol, so the checkpoint
     * consistency check, {@code SESSION_RESTORED} event publication, and
     * post-execution persistence are identical for batch and single-session
     * restores (Wiring Verification — Minimum Rules #23).
     */
    @Override
    public SessionRestoreSummary restorePendingSessions(String approver, String reason) {
        Collection<AgentSession> discovered;
        try {
            discovered = sessionStore.listAllSessions();
        } catch (UnsupportedOperationException e) {
            // Fail-fast: store does not support discovery. Surface as
            // NopAiAgentException so the operator learns the deployment is
            // misconfigured rather than seeing a silent empty summary.
            throw new NopAiAgentException(
                    "restorePendingSessions failed: the session store does not support "
                            + "discovery (listAllSessions threw UnsupportedOperationException). "
                            + "Auto-restore requires a discovery-capable store such as "
                            + "FileBackedSessionStore. Underlying error: " + e.getMessage(), e);
        }
        if (discovered == null || discovered.isEmpty()) {
            return new SessionRestoreSummary(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        List<SessionRestoreSummary.Entry> restored = new ArrayList<>();
        List<SessionRestoreSummary.SkipEntry> skipped = new ArrayList<>();
        List<SessionRestoreSummary.Entry> failed = new ArrayList<>();

        // Iterate over a snapshot to avoid concurrent-modification surprises
        // if restoreSession mutates the store's cache.
        List<AgentSession> snapshot = new ArrayList<>(discovered);
        for (AgentSession session : snapshot) {
            String sessionId = session.getSessionId();
            AgentExecStatus status = session.getStatus();

            if (status == AgentExecStatus.running || status == AgentExecStatus.pending) {
                // Restore candidate. Delegate to the single-session primitive
                // (Wiring Verification: this calls restoreSession rather than
                // duplicating the restore protocol). Sequential restore with
                // per-session failure isolation.
                try {
                    AgentExecutionResult result = restoreSession(sessionId, approver, reason)
                            .toCompletableFuture().join();
                    restored.add(new SessionRestoreSummary.Entry(
                            sessionId,
                            result.getStatus() != null ? result.getStatus().name() : "unknown"));
                } catch (Throwable t) {
                    // A single session restore failure must not abort the
                    // batch. Record the failure and continue.
                    LOG.warn("DefaultAgentEngine.restorePendingSessions: failed to restore session {}",
                            sessionId, t);
                    failed.add(new SessionRestoreSummary.Entry(sessionId, t.toString()));
                }
            } else if (status == AgentExecStatus.paused) {
                // Governance: sticky-pause requires an explicit human
                // resumeSession (plan 180). Auto-restore would bypass the
                // human-intervention contract.
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "paused: sticky-pause requires an explicit resumeSession (plan 180)"));
            } else if (isTerminalStatus(status)) {
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "terminal: session already reached a final outcome"));
            } else {
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "non-restorable status: " + status));
            }
        }

        LOG.info("restorePendingSessions completed: restored={}, skipped={}, failed={} (approver={}, reason={})",
                restored.size(), skipped.size(), failed.size(), approver, reason);

        return new SessionRestoreSummary(restored, skipped, failed);
    }

    /**
     * Returns true if the status is terminal (the session has finished and
     * cannot be restored). A session in a terminal state has no reason to be
     * restored — it already reached a final outcome.
     */
    private static boolean isTerminalStatus(AgentExecStatus status) {
        return status == AgentExecStatus.completed
                || status == AgentExecStatus.failed
                || status == AgentExecStatus.cancelled
                || status == AgentExecStatus.forced_stopped
                || status == AgentExecStatus.escalated;
    }

    /**
     * Resolve the effective {@link IToolAccessChecker} for the current
     * execution: if the request carries a parent permission constraint in its
     * metadata (propagated by {@code call-agent}), wrap the engine's own
     * {@code toolAccessChecker} with a {@link ParentConstrainedToolAccessChecker}
     * scoped to this sub-agent execution. When no constraint is present
     * (top-level agent), return the engine's own checker unchanged — backward
     * compatible.
     *
     * <p>The wrapping is scoped to this execution only — it does not mutate the
     * engine's own {@code toolAccessChecker} field.
     *
     * <p><b>Fail-fast</b>: if the metadata key is present but the value is not a
     * {@link ParentPermissionConstraint} (malformed), throw
     * {@link NopAiAgentException} — never silently ignore a malformed
     * constraint.
     */
    IToolAccessChecker resolveEffectiveToolAccessChecker(AgentMessageRequest request) {
        if (request.getMetadata() == null || request.getMetadata().isEmpty()) {
            return this.toolAccessChecker;
        }
        Object raw = request.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
        if (raw == null) {
            return this.toolAccessChecker;
        }
        if (!(raw instanceof ParentPermissionConstraint)) {
            throw new NopAiAgentException(
                    "doExecute failed: metadata key '" + ParentPermissionConstraint.METADATA_KEY
                            + "' is present but not a ParentPermissionConstraint (got: "
                            + raw.getClass().getName() + ")");
        }
        ParentPermissionConstraint constraint = (ParentPermissionConstraint) raw;
        return new ParentConstrainedToolAccessChecker(constraint, this.toolAccessChecker);
    }

    /**
     * Resolve the per-agent base {@link IPathAccessChecker} from the agent
     * model's declared {@code <path-rules>}. When the agent declares non-empty
     * path-rules, the engine's global {@code pathAccessChecker} is wrapped with
     * a {@link RuleBasedPathAccessChecker} so the agent's own glob rules
     * (first-match-wins, DENY → deny, ALLOW/no-match → delegate) are enforced.
     * When no path-rules are declared, the global checker is returned unchanged
     * (backward compatible).
     *
     * <p>Composition order: global deny-list (innermost) → per-agent rules →
     * parent constraint (outermost, applied by
     * {@link #resolveEffectivePathAccessChecker(AgentMessageRequest, IPathAccessChecker)}).
     * A path must pass all layers.
     */
    IPathAccessChecker resolvePerAgentPathChecker(AgentModel agentModel) {
        java.util.List<io.nop.ai.agent.model.PathRuleModel> rules = agentModel.getPathRules();
        if (rules == null || rules.isEmpty()) {
            return this.pathAccessChecker;
        }
        return new RuleBasedPathAccessChecker(rules, this.pathAccessChecker);
    }

    /**
     * Backward-compatible single-arg overload. Delegates to the two-arg version
     * with the engine's own {@code pathAccessChecker} as the per-agent base
     * (existing tests that do not opt into per-agent path-rules continue to
     * compile and behave identically).
     */
    IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request) {
        return resolveEffectivePathAccessChecker(request, this.pathAccessChecker);
    }

    /**
     * Resolve the effective {@link IPathAccessChecker} for the current
     * execution, using the supplied {@code perAgentBase} as the base checker
     * (typically the global checker wrapped with the agent's own
     * {@link RuleBasedPathAccessChecker} when path-rules are declared). If the
     * request carries a parent permission constraint in its metadata
     * (propagated by {@code call-agent}) AND the constraint's path roots are
     * PRESENT (non-null), wrap {@code perAgentBase} with a
     * {@link ParentConstrainedPathAccessChecker} scoped to this sub-agent
     * execution. When no constraint is present OR the constraint's path roots
     * are ABSENT (null), return {@code perAgentBase} unchanged.
     *
     * <p>Composition order: {@code perAgentBase} (global deny-list + per-agent
     * rules) is the innermost layer; the parent-constraint wrapper is the
     * outermost layer.
     *
     * <p><b>Fail-fast</b>: if the metadata key is present but the value is not a
     * {@link ParentPermissionConstraint} (malformed), throw
     * {@link NopAiAgentException} — never silently ignore a malformed
     * constraint.
     */
    IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request,
                                                          IPathAccessChecker perAgentBase) {
        if (request.getMetadata() == null || request.getMetadata().isEmpty()) {
            return perAgentBase;
        }
        Object raw = request.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
        if (raw == null) {
            return perAgentBase;
        }
        if (!(raw instanceof ParentPermissionConstraint)) {
            throw new NopAiAgentException(
                    "doExecute failed: metadata key '" + ParentPermissionConstraint.METADATA_KEY
                            + "' is present but not a ParentPermissionConstraint (got: "
                            + raw.getClass().getName() + ")");
        }
        ParentPermissionConstraint constraint = (ParentPermissionConstraint) raw;
        if (!constraint.hasPathRoots() && !constraint.hasPathRules()) {
            // Constraint present but path roots AND path rules ABSENT → no path confinement
            return perAgentBase;
        }
        return new ParentConstrainedPathAccessChecker(constraint, perAgentBase);
    }

    IAgentExecutor resolveExecutor(AgentModel model) {
        return resolveExecutor(model, this.toolAccessChecker, this.pathAccessChecker);
    }

    /**
     * Backward-compatible two-arg overload. Delegates with the engine's own
     * {@code pathAccessChecker} for the path checker. Existing callers (e.g.
     * plan-169 tests) that only override the tool checker continue to compile
     * and behave identically.
     */
    IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker) {
        return resolveExecutor(model, toolAccessChecker, this.pathAccessChecker);
    }

    /**
     * Build the executor for the given agent model, using the effective
     * (possibly wrapped) tool and path access checkers. Both checkers default
     * to the engine's own fields when no override is supplied, so top-level
     * agent executions receive the unwrapped checkers and sub-agent executions
     * (where a parent constraint is present) receive the wrapped checkers.
     */
    IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker,
                                   IPathAccessChecker pathAccessChecker) {
        String mode = model.getMode();
        if (mode == null || mode.isEmpty() || "react".equals(mode)) {
            DefaultHookRegistry hookRegistry = DefaultHookRegistry.fromAgentModel(model);
            return ReActAgentExecutor.builder()
                    .chatService(chatService)
                    .toolManager(toolManager)
                    .eventPublisher(eventPublisher)
                    .permissionProvider(permissionProvider)
                    .toolAccessChecker(toolAccessChecker)
                    .pathAccessChecker(pathAccessChecker)
                    .hookRegistry(hookRegistry)
                    .contextCompactor(contextCompactor)
                    .contentGuardrail(contentGuardrail)
                    .modelRouter(modelRouter)
                    .tokenEstimator(tokenEstimator)
                    .talents(talents)
                    .skillProvider(skillProvider)
                    .toolCallRepairer(toolCallRepairer)
                    .engine(this)
                    .messenger(messenger)
                    .securityLevelResolver(securityLevelResolver)
                    .permissionMatrix(permissionMatrix)
                    .levelHintsProducer(levelHintsProducer)
                    .approvalGate(approvalGate)
                    .denialLedger(denialLedger)
                    .postDenialGuard(postDenialGuard)
                    .auditLogger(this.auditLogger)
                    .checkpointManager(checkpointManager)
                    .sessionStore(this.sessionStore)
                    .memoryStoreProvider(this.memoryStoreProvider)
                    .build();
        }
        if ("single-turn".equals(mode)) {
            return new SingleTurnExecutor(chatService, eventPublisher);
        }
        if ("plan".equals(mode)) {
            throw new UnsupportedOperationException("Plan execution mode is not yet implemented: mode=plan");
        }
        throw new NopAiAgentException("Unknown agent execution mode: " + mode);
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            // P0 path-traversal guard (finding [13-15]): a caller-controlled
            // sessionId flows into Path.resolve(sessionId) in the file-backed
            // stores. Reject any id outside [A-Za-z0-9_-] before use. The
            // UUID fallback below produces a regex-safe id by construction.
            // Note: this covers execute/sendMessage only — resumeSession/
            // restoreSession/cancelSession bypass resolveSessionId and are
            // guarded by the store/checkpoint-layer containment check
            // (SessionIds.requireContainedPath).
            return SessionIds.requireValidIdentifier(sessionId);
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Load the {@link AgentModel} for the given agent name from the VFS
     * resource {@code "/<agentName>.agent.xml"}.
     *
     * <p><b>Fail-closed path-injection guard (finding [13-16])</b>: the
     * agentName is validated against the strict allow-list
     * {@code ^[A-Za-z0-9_-]+$} via {@link AgentNames#requireValidIdentifier}
     * <b>before</b> any string concatenation or
     * {@code ResourceComponentManager} call. A caller-controlled agentName
     * (sourced from the public API {@code AgentMessageRequest.agentName}, or
     * indirectly from LLM-supplied {@code call-agent} tool args) containing
     * {@code /}, {@code \}, {@code ..}, NUL, whitespace, or any Unicode is
     * rejected by throwing {@link NopAiAgentException} — no silent
     * sanitization, no truncation, no fall-back. This single guard covers all
     * three callers ({@code doExecute}, {@code resumeSession},
     * {@code restoreSession}) and the indirect
     * {@code CallAgentExecutor} → {@code engine.execute} path.
     *
     * @param agentName the caller-supplied agent name
     * @return the loaded agent model
     * @throws NopAiAgentException if the agentName is {@code null}, empty, or
     *         contains any character outside {@code [A-Za-z0-9_-]}, or if the
     *         model fails to load
     */
    private AgentModel loadAgentModel(String agentName) {
        AgentNames.requireValidIdentifier(agentName);
        String path = "/" + agentName + ".agent.xml";
        try {
            Object obj = ResourceComponentManager.instance().loadComponentModel(path);
            if (!(obj instanceof AgentModel)) {
                throw new NopAiAgentException("Failed to load agent model from " + path
                        + ": unexpected type " + obj.getClass().getName());
            }
            return (AgentModel) obj;
        } catch (NopAiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new NopAiAgentException("Failed to load agent model: agentName=" + agentName, e);
        }
    }
}
