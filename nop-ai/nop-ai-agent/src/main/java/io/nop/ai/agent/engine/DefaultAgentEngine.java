package io.nop.ai.agent.engine;

import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.ICircuitBreaker;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.ISustainer;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoOpGoalTracker;
import io.nop.ai.agent.reliability.NoOpSustainer;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.reliability.ThresholdBreaker;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.runtime.IActorRuntime;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.runtime.recovery.IRecoveryManager;
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
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.NoOpModelSwitchedMessageWriter;
import io.nop.ai.agent.skill.ISkillCurator;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.NoOpSkillCurator;
import io.nop.ai.agent.skill.NoOpSkillProvider;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.scheduler.ITeamTaskSchedulerDaemon;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

public class DefaultAgentEngine implements IAgentEngine {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final DefaultAgentEventPublisher eventPublisher;
    private final ISessionStore sessionStore;
    private final String instanceId = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, AgentSessionLifecycle.CancelHandle> runningExecutions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private ExecutorService agentExecutor;
    private volatile boolean ownAgentExecutor;

    private final DefaultAgentEngineConfig config;
    private final AgentCallDelegate callDelegate;
    private final SessionLockRenewal lockRenewal;
    private final AgentSessionSupport sessionSupport;
    private final AgentExecutorResolver executorResolver;
    private final AgentTeamBinder teamBinder;
    private final AgentSessionLifecycle lifecycle;
    private final AgentStartupWarnings startupWarnings;


    public static final class Builder {
        private IChatService chatService;
        private IToolManager toolManager;
        private ISessionStore sessionStore = new InMemorySessionStore();
        private IPermissionProvider permissionProvider = new AllowAllPermissionProvider();
        private IToolAccessChecker toolAccessChecker = new DefaultToolAccessChecker();
        private IPathAccessChecker pathAccessChecker = new DefaultPathAccessChecker();
        private IContentGuardrail contentGuardrail = NoOpContentGuardrail.noOp();
        private Predicate<ChatMessage> forkMessageFilter;
        private IModelRouter modelRouter = PassThroughModelRouter.passThrough();
        private IContextCompactor contextCompactor;
        private List<ITalent> talents = java.util.Collections.emptyList();
        private ISkillProvider skillProvider = NoOpSkillProvider.noOp();
        private ISkillCurator skillCurator = NoOpSkillCurator.noOp();
        private IToolCallRepairer toolCallRepairer;
        private IAgentMessenger messenger = NoOpAgentMessenger.noOp();
        private Function<String, IMailbox> mailboxFactory;
        private IPermissionMatrix permissionMatrix = new DefaultPermissionMatrix();
        private ISecurityLevelResolver securityLevelResolver = new DefaultSecurityLevelResolver();
        private IApprovalGate approvalGate = new DefaultApprovalGate();
        private IDenialLedger denialLedger = new DefaultDenialLedger();
        private IPostDenialGuard postDenialGuard = new DefaultPostDenialGuard();
        private IAuditLogger auditLogger = new Slf4jAuditLogger();
        private ICheckpointManager checkpointManager = NoOpCheckpoint.noOp();
        private IMemoryStoreProvider memoryStoreProvider = new InMemoryMemoryStoreProvider();
        private IUsageRecorder usageRecorder = NoOpUsageRecorder.noOp();
        private IModelSwitchedMessageWriter modelSwitchedMessageWriter = NoOpModelSwitchedMessageWriter.noOp();
        private IBudgetProvider budgetProvider = NoOpBudgetProvider.noOp();
        private IRetryPolicy retryPolicy = new StandardRetryPolicy();
        private ICircuitBreaker circuitBreaker = new ThresholdBreaker();
        private IGoalTracker goalTracker = NoOpGoalTracker.noOp();
        private ISustainer sustainer = NoOpSustainer.noOp();
        private IConflictStrategy conflictStrategy = FailFastStrategy.failFast();
        private IWriteIntentRegistry writeIntentRegistry = new InMemoryWriteIntentRegistry();
        private IContributionRegistry contributionRegistry = NoOpContributionRegistry.noOp();
        private io.nop.ai.agent.security.ISandboxBackend sandboxBackend;
        private ITeamManager teamManager;
        private ITeamTaskStore teamTaskStore;
        private ITeamAclChecker teamAclChecker;
        private int memoryInjectionBudgetTokens = 1024;
        private ExecutorService agentExecutor;
        private long callAgentTimeoutMs = 120_000L;
        private long llmTimeoutMs = 120_000L;
        private long toolTimeoutMs = 300_000L;

        public Builder(IChatService chatService, IToolManager toolManager) {
            this.chatService = chatService;
            this.toolManager = toolManager;
            this.contextCompactor = AgentStartupWarnings.defaultPipelineCompactor(chatService);
        }

        public Builder sessionStore(ISessionStore val) { this.sessionStore = val; return this; }
        public Builder permissionProvider(IPermissionProvider val) { this.permissionProvider = val; return this; }
        public Builder toolAccessChecker(IToolAccessChecker val) { this.toolAccessChecker = val; return this; }
        public Builder pathAccessChecker(IPathAccessChecker val) { this.pathAccessChecker = val; return this; }
        public Builder contentGuardrail(IContentGuardrail val) { this.contentGuardrail = val; return this; }
        public Builder forkMessageFilter(Predicate<ChatMessage> val) { this.forkMessageFilter = val; return this; }
        public Builder modelRouter(IModelRouter val) { this.modelRouter = val; return this; }
        public Builder contextCompactor(IContextCompactor val) { this.contextCompactor = val; return this; }
        public Builder talents(List<ITalent> val) { this.talents = val; return this; }
        public Builder skillProvider(ISkillProvider val) { this.skillProvider = val; return this; }
        public Builder skillCurator(ISkillCurator val) { this.skillCurator = val; return this; }
        public Builder toolCallRepairer(IToolCallRepairer val) { this.toolCallRepairer = val; return this; }
        public Builder messenger(IAgentMessenger val) { this.messenger = val; return this; }
        public Builder mailboxFactory(Function<String, IMailbox> val) { this.mailboxFactory = val; return this; }
        public Builder permissionMatrix(IPermissionMatrix val) { this.permissionMatrix = val; return this; }
        public Builder securityLevelResolver(ISecurityLevelResolver val) { this.securityLevelResolver = val; return this; }
        public Builder approvalGate(IApprovalGate val) { this.approvalGate = val; return this; }
        public Builder denialLedger(IDenialLedger val) { this.denialLedger = val; return this; }
        public Builder postDenialGuard(IPostDenialGuard val) { this.postDenialGuard = val; return this; }
        public Builder auditLogger(IAuditLogger val) { this.auditLogger = val; return this; }
        public Builder checkpointManager(ICheckpointManager val) { this.checkpointManager = val; return this; }
        public Builder memoryStoreProvider(IMemoryStoreProvider val) { this.memoryStoreProvider = val; return this; }
        public Builder usageRecorder(IUsageRecorder val) { this.usageRecorder = val; return this; }
        public Builder modelSwitchedMessageWriter(IModelSwitchedMessageWriter val) { this.modelSwitchedMessageWriter = val; return this; }
        public Builder budgetProvider(IBudgetProvider val) { this.budgetProvider = val; return this; }
        public Builder retryPolicy(IRetryPolicy val) { this.retryPolicy = val; return this; }
        public Builder circuitBreaker(ICircuitBreaker val) { this.circuitBreaker = val; return this; }
        public Builder goalTracker(IGoalTracker val) { this.goalTracker = val; return this; }
        public Builder sustainer(ISustainer val) { this.sustainer = val; return this; }
        public Builder conflictStrategy(IConflictStrategy val) { this.conflictStrategy = val; return this; }
        public Builder writeIntentRegistry(IWriteIntentRegistry val) { this.writeIntentRegistry = val; return this; }
        public Builder contributionRegistry(IContributionRegistry val) { this.contributionRegistry = val; return this; }
        public Builder sandboxBackend(io.nop.ai.agent.security.ISandboxBackend val) { this.sandboxBackend = val; return this; }
        public Builder teamManager(ITeamManager val) { this.teamManager = val; return this; }
        public Builder teamTaskStore(ITeamTaskStore val) { this.teamTaskStore = val; return this; }
        public Builder teamAclChecker(ITeamAclChecker val) { this.teamAclChecker = val; return this; }
        public Builder memoryInjectionBudgetTokens(int val) { this.memoryInjectionBudgetTokens = val; return this; }
        public Builder agentExecutor(ExecutorService val) { this.agentExecutor = val; return this; }
        public Builder callAgentTimeoutMs(long val) { this.callAgentTimeoutMs = val; return this; }
        public Builder llmTimeoutMs(long val) { this.llmTimeoutMs = val; return this; }
        public Builder toolTimeoutMs(long val) { this.toolTimeoutMs = val; return this; }

        public DefaultAgentEngine build() {
            DefaultAgentEngine engine = new DefaultAgentEngine(
                    chatService, toolManager, sessionStore,
                    permissionProvider, toolAccessChecker, pathAccessChecker,
                    contentGuardrail, modelRouter, contextCompactor);
            applyTo(engine);
            engine.startupWarnings.warnIfInsecureDefaults(engine.config);
            return engine;
        }

        private void applyTo(DefaultAgentEngine engine) {
            if (talents != null) engine.setTalents(talents);
            if (forkMessageFilter != null) engine.setForkMessageFilter(forkMessageFilter);
            if (skillProvider != null) engine.setSkillProvider(skillProvider);
            if (skillCurator != null) engine.setSkillCurator(skillCurator);
            if (toolCallRepairer != null) engine.setToolCallRepairer(toolCallRepairer);
            if (messenger != null) engine.setMessenger(messenger);
            if (mailboxFactory != null) engine.setMailboxFactory(mailboxFactory);
            if (permissionMatrix != null) engine.setPermissionMatrix(permissionMatrix);
            if (securityLevelResolver != null) engine.setSecurityLevelResolver(securityLevelResolver);
            if (approvalGate != null) engine.setApprovalGate(approvalGate);
            if (denialLedger != null) engine.setDenialLedger(denialLedger);
            if (postDenialGuard != null) engine.setPostDenialGuard(postDenialGuard);
            if (auditLogger != null) engine.setAuditLogger(auditLogger);
            if (checkpointManager != null) engine.setCheckpointManager(checkpointManager);
            if (memoryStoreProvider != null) engine.setMemoryStoreProvider(memoryStoreProvider);
            if (usageRecorder != null) engine.setUsageRecorder(usageRecorder);
            if (modelSwitchedMessageWriter != null) engine.setModelSwitchedMessageWriter(modelSwitchedMessageWriter);
            if (budgetProvider != null) engine.setBudgetProvider(budgetProvider);
            if (retryPolicy != null) engine.setRetryPolicy(retryPolicy);
            if (circuitBreaker != null) engine.setCircuitBreaker(circuitBreaker);
            if (goalTracker != null) engine.setGoalTracker(goalTracker);
            if (sustainer != null) engine.setSustainer(sustainer);
            if (conflictStrategy != null) engine.setConflictStrategy(conflictStrategy);
            if (writeIntentRegistry != null) engine.setWriteIntentRegistry(writeIntentRegistry);
            if (contributionRegistry != null) engine.setContributionRegistry(contributionRegistry);
            if (sandboxBackend != null) engine.setSandboxBackend(sandboxBackend);
            if (teamManager != null) engine.setTeamManager(teamManager);
            if (teamTaskStore != null) engine.setTeamTaskStore(teamTaskStore);
            if (teamAclChecker != null) engine.setTeamAclChecker(teamAclChecker);
            engine.setMemoryInjectionBudgetTokens(memoryInjectionBudgetTokens);
            if (agentExecutor != null) engine.setAgentExecutor(agentExecutor);
            engine.setCallAgentTimeoutMs(callAgentTimeoutMs);
            engine.setLlmTimeoutMs(llmTimeoutMs);
            engine.setToolTimeoutMs(toolTimeoutMs);
        }
    }

    /**
     * Plan 304: static factory method replacing the 8-constructor chain.
     */
    public static Builder builder(IChatService chatService, IToolManager toolManager) {
        return new Builder(chatService, toolManager);
    }

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
        this.config = new DefaultAgentEngineConfig();
        this.config.setPermissionProvider(permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider());
        this.config.setToolAccessChecker(toolAccessChecker != null ? toolAccessChecker : new DefaultToolAccessChecker());
        this.config.setPathAccessChecker(pathAccessChecker != null ? pathAccessChecker : new DefaultPathAccessChecker());
        this.config.setContentGuardrail(contentGuardrail != null ? contentGuardrail : NoOpContentGuardrail.noOp());
        this.config.setModelRouter(modelRouter != null ? modelRouter : PassThroughModelRouter.passThrough());
        this.config.setContextCompactor(contextCompactor != null ? contextCompactor : AgentStartupWarnings.defaultPipelineCompactor(chatService));
        this.config.setTokenEstimator(TokenEstimators.defaultEstimator());
        this.startupWarnings = new AgentStartupWarnings();
        this.callDelegate = new AgentCallDelegate(this);
        this.sessionSupport = new AgentSessionSupport(this.callDelegate,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        this.lockRenewal = new SessionLockRenewal(this.config, this.runningExecutions);
        this.executorResolver = new AgentExecutorResolver(this.config, chatService, toolManager,
                sessionStore, this.eventPublisher, this, this.callDelegate,
                this::getAgentExecutor, this.startupWarnings);
        this.teamBinder = new AgentTeamBinder(this.config);
        this.lifecycle = new AgentSessionLifecycle(this.config, sessionStore, this.eventPublisher,
                this.instanceId, this.runningExecutions, this::getAgentExecutor,
                this.executorResolver, this.sessionSupport, this.teamBinder, this.lockRenewal,
                this.startupWarnings, this);
        this.startupWarnings.warnIfInsecureDefaults(this.config);
    }

    public void setTalents(List<ITalent> talents) { config.setTalents(talents); }

    public void setSkillProvider(ISkillProvider skillProvider) { config.setSkillProvider(skillProvider); }

    public void setSkillCurator(ISkillCurator skillCurator) { config.setSkillCurator(skillCurator); }

    public void setToolCallRepairer(IToolCallRepairer toolCallRepairer) { config.setToolCallRepairer(toolCallRepairer); }

    public void setForkMessageFilter(Predicate<ChatMessage> forkMessageFilter) { config.setForkMessageFilter(forkMessageFilter); }

    public void setPermissionMatrix(IPermissionMatrix permissionMatrix) { config.setPermissionMatrix(permissionMatrix); }

    public IPermissionMatrix getPermissionMatrix() { return config.getPermissionMatrix(); }

    public void setSecurityLevelResolver(ISecurityLevelResolver securityLevelResolver) { config.setSecurityLevelResolver(securityLevelResolver); }

    public ISecurityLevelResolver getSecurityLevelResolver() { return config.getSecurityLevelResolver(); }

    public void setApprovalGate(IApprovalGate approvalGate) { config.setApprovalGate(approvalGate); }

    public IApprovalGate getApprovalGate() { return config.getApprovalGate(); }

    public void setDenialLedger(IDenialLedger denialLedger) { config.setDenialLedger(denialLedger); }

    public IDenialLedger getDenialLedger() { return config.getDenialLedger(); }

    public void setPostDenialGuard(IPostDenialGuard postDenialGuard) { config.setPostDenialGuard(postDenialGuard); }

    public IPostDenialGuard getPostDenialGuard() { return config.getPostDenialGuard(); }

    public void setAuditLogger(IAuditLogger auditLogger) { config.setAuditLogger(auditLogger); }

    public IAuditLogger getAuditLogger() { return config.getAuditLogger(); }

    public void setCheckpointManager(ICheckpointManager checkpointManager) { config.setCheckpointManager(checkpointManager); }

    public ICheckpointManager getCheckpointManager() { return config.getCheckpointManager(); }

    public void setMemoryStoreProvider(IMemoryStoreProvider memoryStoreProvider) { config.setMemoryStoreProvider(memoryStoreProvider); }

    public IMemoryStoreProvider getMemoryStoreProvider() { return config.getMemoryStoreProvider(); }

    public void setModelSwitchedMessageWriter(IModelSwitchedMessageWriter modelSwitchedMessageWriter) { config.setModelSwitchedMessageWriter(modelSwitchedMessageWriter); }

    public IModelSwitchedMessageWriter getModelSwitchedMessageWriter() { return config.getModelSwitchedMessageWriter(); }

    public void setBudgetProvider(IBudgetProvider budgetProvider) { config.setBudgetProvider(budgetProvider); }

    public IBudgetProvider getBudgetProvider() { return config.getBudgetProvider(); }

    public void setRetryPolicy(IRetryPolicy retryPolicy) { config.setRetryPolicy(retryPolicy); }

    public IRetryPolicy getRetryPolicy() { return config.getRetryPolicy(); }

    public void setCircuitBreaker(ICircuitBreaker circuitBreaker) { config.setCircuitBreaker(circuitBreaker); }

    public ICircuitBreaker getCircuitBreaker() { return config.getCircuitBreaker(); }

    public void setGoalTracker(IGoalTracker goalTracker) { config.setGoalTracker(goalTracker); }

    public IGoalTracker getGoalTracker() { return config.getGoalTracker(); }

    public void setSustainer(ISustainer sustainer) { config.setSustainer(sustainer); }

    public ISustainer getSustainer() { return config.getSustainer(); }

    public void setConflictStrategy(IConflictStrategy conflictStrategy) { config.setConflictStrategy(conflictStrategy); }

    public IConflictStrategy getConflictStrategy() { return config.getConflictStrategy(); }

    public void setWriteIntentRegistry(IWriteIntentRegistry writeIntentRegistry) { config.setWriteIntentRegistry(writeIntentRegistry); }

    public IWriteIntentRegistry getWriteIntentRegistry() { return config.getWriteIntentRegistry(); }

    public void setContributionRegistry(IContributionRegistry contributionRegistry) { config.setContributionRegistry(contributionRegistry); }

    public IContributionRegistry getContributionRegistry() { return config.getContributionRegistry(); }

    public void setActorRuntime(IActorRuntime actorRuntime) { config.setActorRuntime(actorRuntime); }

    public IActorRuntime getActorRuntime() { return config.getActorRuntime(); }

    public void setTeamManager(ITeamManager teamManager) { config.setTeamManager(teamManager); }

    public ITeamManager getTeamManager() { return config.getTeamManager(); }

    public void setTeamTaskStore(ITeamTaskStore teamTaskStore) { config.setTeamTaskStore(teamTaskStore); }

    public ITeamTaskStore getTeamTaskStore() { return config.getTeamTaskStore(); }

    public void setTeamAclChecker(ITeamAclChecker teamAclChecker) { config.setTeamAclChecker(teamAclChecker); }

    public ITeamAclChecker getTeamAclChecker() { return config.getTeamAclChecker(); }

    public void setSandboxBackend(io.nop.ai.agent.security.ISandboxBackend sandboxBackend) { config.setSandboxBackend(sandboxBackend); }

    public io.nop.ai.agent.security.ISandboxBackend getSandboxBackend() { return config.getSandboxBackend(); }

    public void setSessionTakeoverLock(ISessionTakeoverLock sessionTakeoverLock) { config.setSessionTakeoverLock(sessionTakeoverLock); }

    public ISessionTakeoverLock getSessionTakeoverLock() { return config.getSessionTakeoverLock(); }

    public void setLockLeaseMs(long lockLeaseMs) { config.setLockLeaseMs(lockLeaseMs); }

    public long getLockLeaseMs() { return config.getLockLeaseMs(); }

    public void setLockRenewIntervalMs(long lockRenewIntervalMs) { config.setLockRenewIntervalMs(lockRenewIntervalMs); }

    public long getLockRenewIntervalMs() { return config.getLockRenewIntervalMs(); }

    public void setRecoveryManager(IRecoveryManager recoveryManager) { config.setRecoveryManager(recoveryManager); }

    public IRecoveryManager getRecoveryManager() { return config.getRecoveryManager(); }

    public void setTeamTaskSchedulerDaemon(ITeamTaskSchedulerDaemon teamTaskSchedulerDaemon) { config.setTeamTaskSchedulerDaemon(teamTaskSchedulerDaemon); }

    public ITeamTaskSchedulerDaemon getTeamTaskSchedulerDaemon() { return config.getTeamTaskSchedulerDaemon(); }

    public void setMemoryInjectionBudgetTokens(int memoryInjectionBudgetTokens) { config.setMemoryInjectionBudgetTokens(memoryInjectionBudgetTokens); }

    public int getMemoryInjectionBudgetTokens() { return config.getMemoryInjectionBudgetTokens(); }

    public void setCallAgentTimeoutMs(long callAgentTimeoutMs) { config.setCallAgentTimeoutMs(callAgentTimeoutMs); }

    public long getCallAgentTimeoutMs() { return config.getCallAgentTimeoutMs(); }

    public void setLlmTimeoutMs(long llmTimeoutMs) { config.setLlmTimeoutMs(llmTimeoutMs); }

    public long getLlmTimeoutMs() { return config.getLlmTimeoutMs(); }

    public void setToolTimeoutMs(long toolTimeoutMs) { config.setToolTimeoutMs(toolTimeoutMs); }

    public long getToolTimeoutMs() { return config.getToolTimeoutMs(); }

    public void setUsageRecorder(IUsageRecorder usageRecorder) {
        config.setUsageRecorder(usageRecorder);
        startupWarnings.resetUsageRecorderNoOpWarned();
    }

    public IUsageRecorder getUsageRecorder() { return config.getUsageRecorder(); }

    public void setMessenger(IAgentMessenger messenger) { callDelegate.setMessenger(messenger); }

    public IAgentMessenger getMessenger() { return callDelegate.getMessenger(); }

    public void setMailboxFactory(Function<String, IMailbox> mailboxFactory) { sessionSupport.setMailboxFactory(mailboxFactory); }

    public Function<String, IMailbox> getMailboxFactory() { return sessionSupport.getMailboxFactory(); }

    public void setLockRenewExecutor(ScheduledExecutorService lockRenewExecutor) { lockRenewal.setLockRenewExecutor(lockRenewExecutor); }


    public IAgentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public String getInstanceId() {
        return instanceId;
    }

    synchronized ExecutorService getAgentExecutor() {
        if (agentExecutor == null) {
            agentExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "nop-ai-agent-exec");
                t.setDaemon(true);
                return t;
            });
            ownAgentExecutor = true;
        }
        return agentExecutor;
    }

    public void setAgentExecutor(ExecutorService agentExecutor) {
        this.agentExecutor = Objects.requireNonNull(agentExecutor, "agentExecutor must not be null");
        this.ownAgentExecutor = false;
    }


    IToolAccessChecker getToolAccessCheckerForTest() {
        return config.getToolAccessChecker();
    }

    IPathAccessChecker getPathAccessCheckerForTest() {
        return config.getPathAccessChecker();
    }

    public IMailbox getSessionMailbox(String sessionId) {
        return sessionSupport.getSessionMailbox(sessionId);
    }

    public AgentExecStatus getSessionStatus(String sessionId) {
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException("getSessionStatus failed: session not found: sessionId=" + sessionId);
        }
        return session.getStatus();
    }

    public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
        // It has no Principal source in the foundational slice, so the tenant
        // context is null = all data visible. The set/clear structure is
        // present for uniformity (a future principal source only changes the
        // captured value).
        ThreadLocalTenantResolver.set(null);
        try {
            AgentSessionLifecycle.CancelHandle handle = sessionId != null ? runningExecutions.get(sessionId) : null;

            if (handle != null) {
                AgentExecutionContext ctx = handle.context;
                ctx.setCancelRequested(true);
                ctx.setCancelReason(reason);

                AgentSession session = sessionStore.get(sessionId);
                String agentName = session != null ? session.getAgentName() : null;
                lifecycle.publishCancelRequested(sessionId, agentName, reason, forced);

                if (forced) {
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
                lifecycle.publishCancelRequested(sessionId, agentName, reason, forced);
                lifecycle.publishCancelled(sessionId, agentName, reason);
                // status (cancelled) but does NOT enter the inner finally
                // (no handle was registered). Clean up the checkpoint cache
                // symmetrically so cancelled sessions do not leak cache entries.
                config.getCheckpointManager().remove(sessionId);
            }

            return CompletableFuture.completedFuture(null);
        } finally {
            ThreadLocalTenantResolver.clear();
        }
    }

    public CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
        // so set the thread-local tenant context from the request's Principal
        // (null-safe) for the duration of the DB operations, then clear it.
        ThreadLocalTenantResolver.set(resolveTenantId(request));
        try {
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

            String childSessionId = sessionStore.forkSession(parentSessionId, inheritContext, props, config.getForkMessageFilter());

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("parentSessionId", parentSessionId);
            eventPayload.put("childSessionId", childSessionId);
            eventPayload.put("inheritContext", inheritContext);

            AgentSession childSession = sessionStore.get(childSessionId);
            String childAgentName = childSession != null ? childSession.getAgentName() : null;
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_FORKED,
                    childSessionId, childAgentName, eventPayload));

            return CompletableFuture.completedFuture(childSessionId);
        } finally {
            ThreadLocalTenantResolver.clear();
        }
    }

    public AgentMessageAck sendMessage(AgentMessageRequest request) {
        String sessionId = sessionSupport.resolveSessionId(request.getSessionId());
        CompletableFuture<AgentExecutionResult> future = doExecute(request, sessionId);
        future.exceptionally(ex -> {
            LOG.error("Agent execution failed for agentName={}, sessionId={}: {}",
                    request.getAgentName(), sessionId, ex.getMessage(), ex);
            return null;
        });
        return new AgentMessageAck(sessionId);
    }

    private static String resolveTenantId(AgentMessageRequest request) {
        if (request == null || request.getPrincipal() == null) {
            return null;
        }
        return request.getPrincipal().getTenantId();
    }

    private static int extractDelegationDepth(AgentMessageRequest request) {
        if (request.getMetadata() == null || request.getMetadata().isEmpty()) {
            return 0;
        }
        Object raw = request.getMetadata().get(
                io.nop.ai.agent.tool.CallAgentExecutor.DELEGATION_DEPTH_METADATA_KEY);
        if (raw == null) {
            return 0;
        }
        if (!(raw instanceof Integer)) {
            throw new NopAiAgentException(
                    "doExecute failed: metadata key '"
                            + io.nop.ai.agent.tool.CallAgentExecutor.DELEGATION_DEPTH_METADATA_KEY
                            + "' is present but not an Integer (got: "
                            + raw.getClass().getName() + ")");
        }
        return (Integer) raw;
    }

    public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
        String sessionId = sessionSupport.resolveSessionId(request.getSessionId());
        return doExecute(request, sessionId);
    }

    private CompletableFuture<AgentExecutionResult> doExecute(AgentMessageRequest request, String sessionId) {
        // so the supplyAsync lambda body can set the thread-local tenant
        // context on the worker thread before any DB store operation.
        String tenantId = resolveTenantId(request);
        AgentModel agentModel = sessionSupport.loadAgentModel(request.getAgentName());
        // team but no functional ITeamManager is wired, surface the
        // misconfiguration before entering the async block.
        teamBinder.precheckTeamDeclarations(agentModel);
        AgentSession session = sessionStore.getOrCreate(sessionId, request.getAgentName());
        // recovery paths (resumeSession/restoreSession — which have no
        // request/Principal source) can re-establish the tenant context
        // before tenant-scoped DB operations. Only set when the request
        // carries a tenant, so a follow-up anonymous request does not clobber
        // a previously captured tenant.
        if (tenantId != null) {
            session.setTenantId(tenantId);
        }
        int historyCount = session.getMessageCount();

        if (historyCount == 0) {
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CREATED,
                    sessionId, request.getAgentName(), null));
        } else {
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_LOADED,
                    sessionId, request.getAgentName(),
                    java.util.Map.of("historyCount", historyCount)));
        }

        AgentExecutionContext ctx = lifecycle.buildBaseExecutionContext(agentModel, session);

        if (request.getMetadata() != null) {
            ctx.getMetadata().putAll(request.getMetadata());
        }

        // Propagate the request's channel / principal into the execution context
        // so the dispatch path can consult the Layer 2 security matrix. Both are
        // optional (null = unknown channel / anonymous identity); null inputs are
        // set as-is so downstream consumers see the semantically-correct "unknown".
        ctx.setChannelKind(request.getChannelKind());
        ctx.setPrincipal(request.getPrincipal());

        // metadata (propagated by CallAgentExecutor via a dedicated key) and
        // expose it on the context so the ReAct executor can pass it to
        // AgentToolExecuteContext for CallAgentExecutor's depth guard. Absent
        // = top-level agent (depth 0). Fail-fast on malformed values (non-
        // Integer) — never silently ignore.
        ctx.setDelegationDepth(extractDelegationDepth(request));

        ctx.addMessage(new ChatUserMessage(request.getUserMessage()));

        IToolAccessChecker effectiveToolAccessChecker = resolveEffectiveToolAccessChecker(request);
        IPathAccessChecker perAgentBase = resolvePerAgentPathChecker(agentModel);
        IPathAccessChecker effectivePathAccessChecker = resolveEffectivePathAccessChecker(request, perAgentBase);
        sessionSupport.ensureSessionMailbox(sessionId);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // synchronous phase (before supplyAsync) so that cancelSession can
        // find it during the async-enqueue window (after execute() returns
        // but before the supplyAsync lambda starts running). putIfAbsent is
        // the atomic dedup guard — a non-null return means another execution
        // is already registered for this session, so we fail-fast instead of
        // silently overwriting the existing handle.
        //
        // called BEFORE putIfAbsent — if another JVM instance is already
        // restoring/executing this session, fail-fast (裁定 4 路径 a/b).
        // tryAcquire + putIfAbsent are wrapped together so the catch path
        // can release the lock when putIfAbsent fails (裁定 5 路径 1).
        //
        // acquired + the execution slot is reserved, start a periodic
        // tryRenew task so long-running agents do not let the lease expire.
        // The renewHandle is cancelled on every release path (mirrors
        // releaseLockQuietly) so no scheduler thread leaks.
        AgentSessionLifecycle.CancelHandle handle = new AgentSessionLifecycle.CancelHandle(ctx, null);
        try {
            if (!config.getSessionTakeoverLock().tryAcquire(sessionId, instanceId, config.getLockLeaseMs())) {
                throw new NopAiAgentException(
                        "doExecute failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            AgentSessionLifecycle.CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                throw new NopAiAgentException(
                        "doExecute failed: session already executing: sessionId=" + sessionId);
            }
            handle.renewHandle = lockRenewal.startLockRenewal(handle, sessionId, instanceId);
        } catch (RuntimeException e) {
            lifecycle.releaseLockQuietly(sessionId, instanceId);
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }

        try {
            // of ForkJoinPool.commonPool() so concurrent agents do not starve
            // each other (commonPool defaults to ~3-7 threads JVM-wide).
            return CompletableFuture.supplyAsync(() -> {
                // tenant context on the worker thread BEFORE any DB store
                // operation. Standard ThreadLocal does not cross the
                // supplyAsync boundary, so the capture from the synchronous
                // phase must be re-applied here. Cleared in the finally below
                // so the pooled worker thread does not leak tenant context.
                ThreadLocalTenantResolver.set(tenantId);
                try {
                session.setStatus(AgentExecStatus.running);

                // running. cancelSession(forced=true) reads this volatile field.
                handle.thread = Thread.currentThread();

                AgentExecutionResult result;
                try {
                    // this inner try so a failure in either triggers the
                    // symmetric cleanup in the finally below (handle / actor /
                    // takeover lock / heartbeat renewal). Previously both sat
                    // BEFORE the inner try — a failure bypassed the finally and
                    // permanently leaked all four resources, bricking the
                    // sessionId ("session already executing" on every retry).
                    //
                    // on isEnabled() (NoOp default returns false → skipped, no
                    // exception-based control flow). When enabled, createActor
                    // registers an AgentActor that runs a mailbox consumption loop
                    // on a dedicated thread. The Actor is a container/observer,
                    // not a replacement for the ReAct executor.
                    // Actor immediately after createActor returns and before
                    // execute(ctx), so the consumption loop can inject polled
                    // messages into the ReAct reasoning context.
                    if (config.getActorRuntime().isEnabled()) {
                        AgentActor actor = config.getActorRuntime().createActor(sessionId, request.getAgentName());
                        actor.setSteeringQueue(ctx.getSteeringQueue());
                    }

                    // Runs after createActor so the actorId is available.
                    teamBinder.autoBindTeam(agentModel, sessionId, request.getAgentName());

                    result = executor.execute(ctx).toCompletableFuture().join();
                } finally {
                    // handle, never another execution's handle (eliminates the
                    // [14-1] mutual-clobber race where the first execution's
                    // finally removes the second execution's handle).
                    runningExecutions.remove(sessionId, handle);
                    // was lost mid-execution, force terminal status to
                    // failed (the executor's cancel path would otherwise
                    // set cancelled — lease-lost is a system-level failure,
                    // not a user-initiated cancel).
                    session.setStatus(ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    // so finished sessions do not block future sessions from
                    // writing the same files. Safe to call on every exit path
                    // (release of an unknown/empty session is a no-op).
                    config.getWriteIntentRegistry().releaseSession(sessionId);
                    // terminal sessions so it does not grow unbounded.
                    // NOT called for paused — paused is non-terminal and must
                    // retain checkpoints for restoreSession recovery.
                    if (AgentSessionLifecycle.isTerminalStatus(session.getStatus())) {
                        config.getCheckpointManager().remove(sessionId);
                    }
                    // entry. The actorId is reverse-looked-up via sessionId
                    // (no CancelHandle or AgentExecutionContext modification).
                    if (config.getActorRuntime().isEnabled()) {
                        config.getActorRuntime().getActorBySession(sessionId)
                                .ifPresent(a -> config.getActorRuntime().destroyActor(a.getActorId()));
                    }
                    // 路径 3 — inner finally). Fault-tolerant: a failed
                    // release only LOG.warn (the lease auto-expires via TTL).
                    lifecycle.releaseLockQuietly(sessionId, instanceId);
                    // renewal task (裁定 mirrors releaseLockQuietly path 3)
                    // so no scheduler thread leaks past execution end.
                    SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
                }

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
                } finally {
                    // pooled thread does not leak tenant state to the next task.
                    ThreadLocalTenantResolver.clear();
                }
        }, getAgentExecutor());
        } catch (RuntimeException e) {
            // (e.g. RejectedExecutionException), clean up the pre-registered
            // handle so a subsequent execute() is not permanently blocked.
            runningExecutions.remove(sessionId, handle);
            // — outer catch / supplyAsync submission failure). Same
            // fault-tolerant releaseLockQuietly as the inner finally.
            lifecycle.releaseLockQuietly(sessionId, instanceId);
            // task (裁定 mirrors releaseLockQuietly path 2).
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }

    public CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
        return lifecycle.resumeSession(sessionId, approver, reason);
    }

    public CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
        return lifecycle.restoreSession(sessionId, approver, reason);
    }

    /** @see AgentSessionLifecycle#restorePendingSessions */
    public SessionRestoreSummary restorePendingSessions(String approver, String reason) {
        return lifecycle.restorePendingSessions(approver, reason);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            LOG.debug("DefaultAgentEngine.close() called more than once — no-op (idempotent)");
            return;
        }
        // Shut down self-created pools only. Externally injected pools
        // (setXxxExecutor cleared the own* flag) are left untouched.
        if (lockRenewal.isOwnLockRenewExecutor() && lockRenewal.getLockRenewExecutor() != null) {
            try {
                lockRenewal.getLockRenewExecutor().shutdown();
                LOG.debug("DefaultAgentEngine.close(): shut down self-created lockRenewExecutor");
            } catch (RuntimeException e) {
                LOG.warn("DefaultAgentEngine.close(): failed to shut down lockRenewExecutor: {}",
                        e.toString());
            }
        }
        if (ownAgentExecutor && agentExecutor != null) {
            try {
                agentExecutor.shutdown();
                LOG.debug("DefaultAgentEngine.close(): shut down self-created agentExecutor");
            } catch (RuntimeException e) {
                LOG.warn("DefaultAgentEngine.close(): failed to shut down agentExecutor: {}",
                        e.toString());
            }
        }
    }

    /**
     * Plan 278 (AR-09): whether {@link #close()} has been called.
     */
    public boolean isClosed() {
        return closed.get();
    }


    // ---- test-facing package-private facades for the extracted resolver (MA4.2-05) ----
    IToolAccessChecker resolveEffectiveToolAccessChecker(AgentMessageRequest request) {
        return executorResolver.resolveEffectiveToolAccessChecker(request);
    }

    IPathAccessChecker resolvePerAgentPathChecker(AgentModel agentModel) {
        return executorResolver.resolvePerAgentPathChecker(agentModel);
    }

    IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request) {
        return executorResolver.resolveEffectivePathAccessChecker(request);
    }

    IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request, IPathAccessChecker perAgentBase) {
        return executorResolver.resolveEffectivePathAccessChecker(request, perAgentBase);
    }

    IAgentExecutor resolveExecutor(AgentModel model) {
        return executorResolver.resolveExecutor(model);
    }

    IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker) {
        return executorResolver.resolveExecutor(model, toolAccessChecker);
    }

    IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker) {
        return executorResolver.resolveExecutor(model, toolAccessChecker, pathAccessChecker);
    }

    static String formatMemorySection(java.util.List<io.nop.ai.agent.memory.AiMemoryItem> items) {
        return AgentSessionLifecycle.formatMemorySection(items);
    }

    /** @see DefaultAgentEngineConfig#curateSkills */
    public io.nop.ai.agent.skill.SkillCurationResult curateSkills() {
        return config.curateSkills();
    }

}
