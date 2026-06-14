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
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.DefaultLevelHintsProducer;
import io.nop.ai.agent.security.IApprovalGate;
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
    private ICheckpointManager checkpointManager = NoOpCheckpoint.noOp();

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
        this(chatService, toolManager, sessionStore, permissionProvider, new AllowAllToolAccessChecker());
    }

    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                              ISessionStore sessionStore, IPermissionProvider permissionProvider,
                              IToolAccessChecker toolAccessChecker) {
        this(chatService, toolManager, sessionStore, permissionProvider,
                toolAccessChecker, new AllowAllPathAccessChecker());
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
        this.toolAccessChecker = toolAccessChecker != null ? toolAccessChecker : new AllowAllToolAccessChecker();
        this.pathAccessChecker = pathAccessChecker != null ? pathAccessChecker : new AllowAllPathAccessChecker();
        this.contentGuardrail = contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp();
        this.modelRouter = modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough();
        this.contextCompactor = contextCompactor != null ? contextCompactor : defaultPipelineCompactor(chatService);
        this.tokenEstimator = CalibratedTokenEstimator.defaultInstance();
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
                handle.thread.interrupt();
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

    private static final class CancelHandle {
        final AgentExecutionContext context;
        final Thread thread;

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

        return CompletableFuture.supplyAsync(() -> {
            session.setStatus(AgentExecStatus.running);

            CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
            runningExecutions.put(sessionId, handle);

            AgentExecutionResult result;
            try {
                result = executor.execute(ctx).toCompletableFuture().join();
            } finally {
                runningExecutions.remove(sessionId);
                session.setStatus(ctx.getStatus());
            }

            List<ChatMessage> allMessages = ctx.getMessages();
            int currentCount = allMessages.size();
            if (currentCount > historyCount) {
                List<ChatMessage> newMessages = new ArrayList<>(allMessages.subList(historyCount, currentCount));
                session.appendMessages(newMessages);
            }

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();

            return result;
        });
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

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ctx.addMessage(new ChatSystemMessage(systemPrompt));
        }

        if (session.getMessageCount() > 0) {
            ctx.getMessages().addAll(session.getMessages());
        }

        return ctx;
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
        int historyCount = session.getMessageCount();
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        // Resolve the executor with the engine's own checkers (no parent
        // constraint applies on resume — resume is a top-level recovery action,
        // not a sub-agent call). Per-agent path rules are still honoured so the
        // resumed execution is consistent with a normal top-level execution.
        IToolAccessChecker effectiveToolAccessChecker = this.toolAccessChecker;
        IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        return CompletableFuture.supplyAsync(() -> {
            CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
            runningExecutions.put(sessionId, handle);

            AgentExecutionResult result;
            try {
                result = executor.execute(ctx).toCompletableFuture().join();
            } finally {
                runningExecutions.remove(sessionId);
                session.setStatus(ctx.getStatus());
            }

            List<ChatMessage> allMessages = ctx.getMessages();
            int currentCount = allMessages.size();
            if (currentCount > historyCount) {
                List<ChatMessage> newMessages = new ArrayList<>(allMessages.subList(historyCount, currentCount));
                session.appendMessages(newMessages);
            }

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();

            return result;
        });
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
                    .checkpointManager(checkpointManager)
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
            return sessionId;
        }
        return UUID.randomUUID().toString();
    }

    private AgentModel loadAgentModel(String agentName) {
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
