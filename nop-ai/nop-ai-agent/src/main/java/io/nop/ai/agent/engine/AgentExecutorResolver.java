package io.nop.ai.agent.engine;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.contribution.Contribution;
import io.nop.ai.agent.contribution.ContributionType;
import io.nop.ai.agent.contribution.HookPayload;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.ParentConstrainedPathAccessChecker;
import io.nop.ai.agent.security.ParentConstrainedToolAccessChecker;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.agent.security.RuleBasedPathAccessChecker;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.toolkit.api.IToolManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Agent-executor / access-checker resolution for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05). Composes the effective tool/path
 * access checkers (parent-constraint clamping), builds the ReAct executor
 * via {@link ReActAgentExecutorBuilder}, resolves hook/middleware
 * contributions and wires the optional SPI dependencies from the injected
 * {@link DefaultAgentEngineConfig}.
 */
public class AgentExecutorResolver {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
    private final DefaultAgentEngineConfig config;
    private final IChatService chatService;
    private final IToolManager toolManager;
    private final ISessionStore sessionStore;
    private final IAgentEventPublisher eventPublisher;
    private final DefaultAgentEngine engine;
    private final AgentCallDelegate callDelegate;
    private final java.util.function.Supplier<ExecutorService> agentExecutorSupplier;
    private final AgentStartupWarnings startupWarnings;

    public AgentExecutorResolver(DefaultAgentEngineConfig config,
                                 IChatService chatService,
                                 IToolManager toolManager,
                                 ISessionStore sessionStore,
                                 IAgentEventPublisher eventPublisher,
                                 DefaultAgentEngine engine,
                                 AgentCallDelegate callDelegate,
                                 java.util.function.Supplier<ExecutorService> agentExecutorSupplier,
                                 AgentStartupWarnings startupWarnings) {
        this.config = config;
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.sessionStore = sessionStore;
        this.eventPublisher = eventPublisher;
        this.engine = engine;
        this.callDelegate = callDelegate;
        this.agentExecutorSupplier = agentExecutorSupplier;
        this.startupWarnings = startupWarnings;
    }

    private ExecutorService agentExecutor() {
        return agentExecutorSupplier.get();
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    public IToolAccessChecker resolveEffectiveToolAccessChecker(AgentMessageRequest request) {
        if (request.getMetadata() == null || request.getMetadata().isEmpty()) {
            return config.getToolAccessChecker();
        }
        Object raw = request.getMetadata().get(ParentPermissionConstraint.METADATA_KEY);
        if (raw == null) {
            return config.getToolAccessChecker();
        }
        if (!(raw instanceof ParentPermissionConstraint)) {
            throw new NopAiAgentException(
                    "doExecute failed: metadata key '" + ParentPermissionConstraint.METADATA_KEY
                            + "' is present but not a ParentPermissionConstraint (got: "
                            + raw.getClass().getName() + ")");
        }
        ParentPermissionConstraint constraint = (ParentPermissionConstraint) raw;
        return new ParentConstrainedToolAccessChecker(constraint, config.getToolAccessChecker());
    }
    public IPathAccessChecker resolvePerAgentPathChecker(AgentModel agentModel) {
        java.util.List<io.nop.ai.agent.model.PathRuleModel> rules = agentModel.getPathRules();
        if (rules == null || rules.isEmpty()) {
            return config.getPathAccessChecker();
        }
        return new RuleBasedPathAccessChecker(rules, config.getPathAccessChecker());
    }
    public IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request) {
        return resolveEffectivePathAccessChecker(request, config.getPathAccessChecker());
    }
    public IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request,
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
    public IAgentExecutor resolveExecutor(AgentModel model) {
        return resolveExecutor(model, config.getToolAccessChecker(), config.getPathAccessChecker());
    }

    /**
     * Backward-compatible two-arg overload. Delegates with the engine's own
     * {@code config.getPathAccessChecker()} for the path checker. Existing callers (e.g.
     * plan-169 tests) that only override the tool checker continue to compile
     * and behave identically.
     */
    public IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker) {
        return resolveExecutor(model, config.getToolAccessChecker(), config.getPathAccessChecker());
    }

    /**
     * (possibly wrapped) tool and path access checkers. Both checkers default
     * to the engine's own fields when no override is supplied, so top-level
     * agent executions receive the unwrapped checkers and sub-agent executions
     * (where a parent constraint is present) receive the wrapped checkers.
     */
    public IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker,
                                   IPathAccessChecker pathAccessChecker) {
        // MA6.3-AR-4: make the NoOp usage-recorder default observable instead
        // of silent. Fired lazily at first execution (not at construction):
        // the Builder wiring sequence (constructor → applyTo → build) would
        // otherwise emit a spurious WARN for an engine that wires a real
        // recorder after construction. One-shot per engine instance.
        startupWarnings.warnIfNoOpUsageRecorder(config);
        String mode = model.getMode();
        if (mode == null || mode.isEmpty() || "react".equals(mode)) {
            DefaultHookRegistry hookRegistry = DefaultHookRegistry.fromAgentModel(model);
            resolveHookContributions(hookRegistry);
            resolveMiddlewares(model, hookRegistry);
            return ReActAgentExecutor.builder()
                    .chatService(chatService)
                    .toolManager(toolManager)
                    .eventPublisher(eventPublisher)
                    .permissionProvider(config.getPermissionProvider())
                    .toolAccessChecker(toolAccessChecker)
                    .pathAccessChecker(pathAccessChecker)
                    .hookRegistry(hookRegistry)
                    .contextCompactor(config.getContextCompactor())
                    .contentGuardrail(config.getContentGuardrail())
                    .modelRouter(config.getModelRouter())
                    .tokenEstimator(config.getTokenEstimator())
                    .talents(config.getTalents())
                    .skillProvider(config.getSkillProvider())
                    .toolCallRepairer(config.getToolCallRepairer())
                    .engine(engine)
                    .messenger(callDelegate.getMessenger())
                    .securityLevelResolver(config.getSecurityLevelResolver())
                    .permissionMatrix(config.getPermissionMatrix())
                    .approvalGate(config.getApprovalGate())
                    .denialLedger(config.getDenialLedger())
                    .postDenialGuard(config.getPostDenialGuard())
                    .auditLogger(config.getAuditLogger())
                    .checkpointManager(config.getCheckpointManager())
                    .sessionStore(this.sessionStore)
                    .memoryStoreProvider(config.getMemoryStoreProvider())
                    .usageRecorder(config.getUsageRecorder())
                    .modelSwitchedMessageWriter(config.getModelSwitchedMessageWriter())
                    .budgetProvider(config.getBudgetProvider())
                    .retryPolicy(config.getRetryPolicy())
                    .circuitBreaker(config.getCircuitBreaker())
                    .goalTracker(config.getGoalTracker())
                    .sustainer(config.getSustainer())
                    .conflictStrategy(config.getConflictStrategy())
                    .writeIntentRegistry(config.getWriteIntentRegistry())
                    .contributionRegistry(config.getContributionRegistry())
                    .sandboxBackend(config.getSandboxBackend())
                    .teamManager(config.getTeamManager())
                    .teamTaskStore(config.getTeamTaskStore())
                    .teamAclChecker(config.getTeamAclChecker())
                    // LLM/tool timeouts and the dedicated executor (used to wrap
                    // the synchronous chatService.call with a timeout) to the
                    // ReAct executor.
                    .llmTimeoutMs(config.getLlmTimeoutMs())
                    .toolTimeoutMs(config.getToolTimeoutMs())
                    .timeoutExecutor(agentExecutorSupplier.get())
                    .build();
        }
        if ("single-turn".equals(mode)) {
            return new SingleTurnExecutor(chatService, eventPublisher);
        }
        if ("plan".equals(mode)) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_PLAN_MODE_NOT_IMPLEMENTED)
                    .param(NopAiAgentErrors.ARG_MODE, mode);
        }
        throw new NopAiAgentException("Unknown agent execution mode: " + mode);
    }
    private void resolveHookContributions(io.nop.ai.agent.hook.IHookRegistry hookRegistry) {
        List<Contribution> hookContributions = config.getContributionRegistry().getContributions(ContributionType.HOOK);
        if (hookContributions.isEmpty()) {
            return;
        }
        for (Contribution c : hookContributions) {
            Object payload = c.getPayload();
            if (!(payload instanceof HookPayload)) {
                LOG.warn("DefaultAgentEngine: skipping HOOK contribution with unexpected payload type"
                        + " (expected HookPayload): type={}, id={}, source={}, payloadClass={}",
                        c.getType(), c.getId(), c.getSource(),
                        payload != null ? payload.getClass().getName() : "null");
                continue;
            }
            HookPayload hp = (HookPayload) payload;
            hookRegistry.register(hp.getPoint(), hp.getHook());
        }
    }
    private void resolveMiddlewares(AgentModel model, io.nop.ai.agent.hook.IHookRegistry hookRegistry) {
        if (model == null) {
            return;
        }
        List<io.nop.ai.agent.model.AgentMiddlewareModel> mwModels = model.getMiddlewares();
        if (mwModels == null || mwModels.isEmpty()) {
            return;
        }
        for (io.nop.ai.agent.model.AgentMiddlewareModel mwModel : mwModels) {
            String impl = mwModel.getImpl();
            String pointName = mwModel.getPoint();
            if (impl == null || impl.isEmpty()) {
                LOG.warn("DefaultAgentEngine: skipping middleware with empty impl class: point={}", pointName);
                continue;
            }
            io.nop.ai.agent.hook.AgentLifecyclePoint point =
                    io.nop.ai.agent.hook.DefaultHookRegistry.resolveLifecyclePoint(pointName);
            if (point == null) {
                LOG.warn("DefaultAgentEngine: skipping middleware with unknown lifecycle point: impl={}, point={}",
                        impl, pointName);
                continue;
            }
            try {
                Object instance = io.nop.commons.util.ClassHelper.safeNewInstance(impl);
                if (!(instance instanceof io.nop.ai.agent.middleware.IAgentMiddleware)) {
                    LOG.warn("DefaultAgentEngine: middleware impl does not implement IAgentMiddleware: impl={}, actualClass={}",
                            impl, instance != null ? instance.getClass().getName() : "null");
                    continue;
                }
                hookRegistry.registerMiddleware(point, (io.nop.ai.agent.middleware.IAgentMiddleware) instance);
            } catch (Exception e) {
                LOG.warn("DefaultAgentEngine: failed to instantiate middleware impl={}, point={}", impl, pointName, e);
            }
        }
    }
}

