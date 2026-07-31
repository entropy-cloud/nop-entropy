package io.nop.ai.agent.engine;

import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.ICircuitBreaker;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.ISustainer;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoOpGoalTracker;
import io.nop.ai.agent.reliability.NoOpSustainer;
import io.nop.ai.agent.reliability.NoRetryPolicy;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.reliability.ThresholdBreaker;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.runtime.IActorRuntime;
import io.nop.ai.agent.runtime.NoOpActorRuntime;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.runtime.lock.NoOpSessionTakeoverLock;
import io.nop.ai.agent.runtime.recovery.IRecoveryManager;
import io.nop.ai.agent.runtime.recovery.NoOpRecoveryManager;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.IApprovalGate;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IPostDenialGuard;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.NoOpAuditLogger;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.session.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.NoOpModelSwitchedMessageWriter;
import io.nop.ai.agent.skill.ISkillCurator;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.NoOpSkillCurator;
import io.nop.ai.agent.skill.NoOpSkillProvider;
import io.nop.ai.agent.skill.SkillCurationResult;
import io.nop.ai.agent.skill.SkillModel;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.NoOpTeamTaskStore;
import io.nop.ai.agent.team.scheduler.ITeamTaskSchedulerDaemon;
import io.nop.ai.agent.team.scheduler.NoOpTeamTaskSchedulerDaemon;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.api.chat.messages.ChatMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Optional-dependency configuration surface of the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05). Holds every optional SPI dependency
 * and the fully-documented setter/getter pair for each; the engine keeps the
 * public signatures as thin delegations to this class so the wiring API is
 * unchanged. Setter bodies (including validation and NoOp fallbacks) are
 * moved verbatim.
 */
public class DefaultAgentEngineConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);

    private IPermissionProvider permissionProvider;
    private IToolAccessChecker toolAccessChecker;
    private IPathAccessChecker pathAccessChecker;
    private IContentGuardrail contentGuardrail;
    private IModelRouter modelRouter;
    private IContextCompactor contextCompactor;
    private ITokenEstimator tokenEstimator;
    private java.util.List<ITalent> talents = java.util.Collections.emptyList();
    private ISkillProvider skillProvider = NoOpSkillProvider.noOp();
    private ISkillCurator skillCurator = NoOpSkillCurator.noOp();
    private IToolCallRepairer toolCallRepairer;
    private Predicate<ChatMessage> forkMessageFilter;
    private IPermissionMatrix permissionMatrix = new DefaultPermissionMatrix();
    private ISecurityLevelResolver securityLevelResolver = new DefaultSecurityLevelResolver();
    private IApprovalGate approvalGate = new DefaultApprovalGate();
    private IDenialLedger denialLedger = new DefaultDenialLedger();
    private IPostDenialGuard postDenialGuard = new DefaultPostDenialGuard();
    private IAuditLogger auditLogger = new Slf4jAuditLogger();
    private ICheckpointManager checkpointManager = NoOpCheckpoint.noOp();
    private IMemoryStoreProvider memoryStoreProvider = new InMemoryMemoryStoreProvider();
    private IUsageRecorder usageRecorder = NoOpUsageRecorder.noOp();
    private volatile boolean usageRecorderNoOpWarned;
    private IModelSwitchedMessageWriter modelSwitchedMessageWriter = NoOpModelSwitchedMessageWriter.noOp();
    private IBudgetProvider budgetProvider = NoOpBudgetProvider.noOp();
    private IRetryPolicy retryPolicy = new StandardRetryPolicy();
    private ICircuitBreaker circuitBreaker = new ThresholdBreaker();
    private IGoalTracker goalTracker = NoOpGoalTracker.noOp();
    private ISustainer sustainer = NoOpSustainer.noOp();
    private IConflictStrategy conflictStrategy = FailFastStrategy.failFast();
    private IWriteIntentRegistry writeIntentRegistry = new InMemoryWriteIntentRegistry();
    private IContributionRegistry contributionRegistry = NoOpContributionRegistry.noOp();
    private IActorRuntime actorRuntime = NoOpActorRuntime.noOp();
    private ITeamManager teamManager = NoOpTeamManager.noOp();
    private ITeamTaskStore teamTaskStore = NoOpTeamTaskStore.noOp();
    private ITeamAclChecker teamAclChecker = NoOpTeamAclChecker.noOp();
    private io.nop.ai.agent.security.ISandboxBackend sandboxBackend =
            io.nop.ai.agent.security.NoOpSandboxBackend.INSTANCE;
    private ISessionTakeoverLock sessionTakeoverLock = NoOpSessionTakeoverLock.noOp();
    private long lockLeaseMs = 1_800_000L;
    private long lockRenewIntervalMs = 600_000L;
    private IRecoveryManager recoveryManager = NoOpRecoveryManager.noOp();
    private ITeamTaskSchedulerDaemon teamTaskSchedulerDaemon = NoOpTeamTaskSchedulerDaemon.noOp();
    private int memoryInjectionBudgetTokens = 1024;
    private long callAgentTimeoutMs = 120_000L;
    private long llmTimeoutMs = 120_000L;
    private long toolTimeoutMs = 300_000L;

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
     * Register the optional message filter applied by {@link #forkSession}
     * when context inheritance is requested (MA6.5-AR-8). null (default)
     * preserves the full-inheritance behaviour.
     */
    public void setForkMessageFilter(Predicate<ChatMessage> forkMessageFilter) {
        this.forkMessageFilter = forkMessageFilter;
    }

                                /**
     * Register the {@link IPermissionMatrix} used for channel × security-level
     * permission decisions (design §5.3). Composition via this setter — no
     * constructor chain change. Shipped default is
     * {@link DefaultPermissionMatrix} (design §5.3 channel × level matrix
     * with usability-safe null channel). {@link PassThroughPermissionMatrix}
     * is retained as a public opt-in.
     */
    public void setPermissionMatrix(IPermissionMatrix permissionMatrix) {
        this.permissionMatrix = permissionMatrix != null ? permissionMatrix : new DefaultPermissionMatrix();
    }

    /**
     * Return the {@link IPermissionMatrix} wired into this engine, or the
     * {@link DefaultPermissionMatrix} default if none was explicitly set.
     */
    public IPermissionMatrix getPermissionMatrix() {
        return permissionMatrix;
    }

    /**
     * Register the {@link ISecurityLevelResolver} used for action-kind × hints
     * security-level resolution (design §5.1). Composition via this setter — no
     * constructor chain change. Shipped default is
     * {@link DefaultSecurityLevelResolver} (trusted-by-default variant of the
     * design §5.1 rule table). {@link NoOpSecurityLevelResolver} is retained
     * as a public opt-in.
     */
    public void setSecurityLevelResolver(ISecurityLevelResolver securityLevelResolver) {
        this.securityLevelResolver = securityLevelResolver != null
                ? securityLevelResolver
                : new DefaultSecurityLevelResolver();
    }

    public ISecurityLevelResolver getSecurityLevelResolver() {
        return securityLevelResolver;
    }

    /**
     * Register the {@link IApprovalGate} used for Layer 3 human-approval
     * governance (design §6.1 / §4.8). Composition via this setter — no
     * constructor chain change. Shipped default is {@link DefaultApprovalGate}
     * (STANDARD/ELEVATED auto-approved, RESTRICTED defense-in-depth denied —
     * plan 199), so the engine provides a visible approval-gate boundary
     * out-of-the-box. Setting to {@code null} preserves the
     * {@code DefaultApprovalGate} default.
     *
     * <p>To explicitly opt into unconditional auto-approval of ALL levels
     * (including RESTRICTED), pass {@link AutoApproveGate#autoApprove()} —
     * this triggers a one-time WARN making the downgrade visible.
     *
     * <p>The gate is consulted in the dispatch loop after the Layer 2
     * permission matrix allows a tool call and before the call is added to
     * {@code allowedCalls}. A denial records an {@code AuditEvent} (DENY +
     * reason + matched rule {@code "layer3_approval_gate"}) and produces a
     * {@code ChatToolResponseMessage.error(...)}, mirroring the Layer 1 / 2
     * deny paths.
     */
    public void setApprovalGate(IApprovalGate approvalGate) {
        this.approvalGate = approvalGate != null ? approvalGate : new DefaultApprovalGate();
    }

    /**
     * Return the {@link IApprovalGate} wired into this engine, or the
     * {@link DefaultApprovalGate} default if none was explicitly set.
     */
    public IApprovalGate getApprovalGate() {
        return approvalGate;
    }

    /**
     * Register the {@link IDenialLedger} used for Layer 3 denial-counting and
     * threshold-pause governance (design §6.2). Composition via this setter —
     * no constructor chain change. Shipped default is
     * {@link DefaultDenialLedger} (in-memory threshold-based counting,
     * threshold = 3). {@link NoOpDenialLedger} is retained as a public opt-in.
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
        this.denialLedger = denialLedger != null ? denialLedger : new DefaultDenialLedger();
    }

    /**
     * Return the {@link IDenialLedger} wired into this engine, or the
     * {@link DefaultDenialLedger} default if none was explicitly set.
     */
    public IDenialLedger getDenialLedger() {
        return denialLedger;
    }

    /**
     * Register the {@link IPostDenialGuard} used for Layer 3 post-denial
     * blind-retry blocking (design §6.3 / L3-7). Composition via this setter
     * — no constructor chain change. Shipped default is
     * {@link DefaultPostDenialGuard} (fingerprint-based blind-retry blocking).
     * {@link PassThroughPostDenialGuard} is retained as a public opt-in.
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
                : new DefaultPostDenialGuard();
    }

    /**
     * Return the {@link IPostDenialGuard} wired into this engine, or the
     * {@link DefaultPostDenialGuard} default if none was explicitly set.
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
     * Wire the {@link IUsageRecorder} consulted at the ReAct loop's token
     * accumulation point (plan 201 / design
     * {@code nop-ai-agent-usage-and-billing.md} §3.1). The shipped default is
     * {@link NoOpUsageRecorder} (usage data discarded — pass-through; a
     * one-shot WARN at first execution makes the missing metering visible,
     * MA6.3-AR-4). When explicitly set to {@code null} the recorder falls back
     * to {@link NoOpUsageRecorder} so the accumulation point always has a
     * non-null sink. Functional options: {@link SimpleUsageRecorder}
     * (structured SLF4J log line), {@code DbUsageRecorder} (persistence, L2-18).
     */
    public void setUsageRecorder(IUsageRecorder usageRecorder) {
        this.usageRecorder = usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp();
        // A later re-wire may replace a NoOp default; allow the WARN to fire
        // again only if the newly wired recorder is still NoOp.
        this.usageRecorderNoOpWarned = false;
    }

    public IUsageRecorder getUsageRecorder() {
        return usageRecorder;
    }

    /**
     * Plan 205 (L2-21): wire a functional {@link IModelSwitchedMessageWriter}
     * that persists model-switched audit messages (role=80) to
     * {@code nop_ai_session_message} when the routed model changes between
     * ReAct iterations (design {@code nop-ai-agent-usage-and-billing.md}
     * §3.5). Optional: when null, falls back to
     * {@link NoOpModelSwitchedMessageWriter} (pass-through).
     */
    public void setModelSwitchedMessageWriter(IModelSwitchedMessageWriter modelSwitchedMessageWriter) {
        this.modelSwitchedMessageWriter = modelSwitchedMessageWriter != null
                ? modelSwitchedMessageWriter
                : NoOpModelSwitchedMessageWriter.noOp();
    }

    public IModelSwitchedMessageWriter getModelSwitchedMessageWriter() {
        return modelSwitchedMessageWriter;
    }

    /**
     * Plan 206 (L2-22): wire a functional {@link IBudgetProvider} that computes
     * a session-level cost/limit snapshot consulted by the ReAct loop before
     * each {@code IModelRouter.route()} call (design
     * {@code nop-ai-agent-usage-and-billing.md} §3.6). Optional: when null,
     * falls back to {@link NoOpBudgetProvider} (unlimited pass-through) so the
     * refresh point always has a non-null provider. Budget is not a security
     * component, so no insecure-default WARN is emitted.
     */
    public void setBudgetProvider(IBudgetProvider budgetProvider) {
        this.budgetProvider = budgetProvider != null ? budgetProvider : NoOpBudgetProvider.noOp();
    }

    public IBudgetProvider getBudgetProvider() {
        return budgetProvider;
    }

    /**
     * Plan 207 (L3-2): wire a functional {@link IRetryPolicy} consulted by the
     * ReAct loop's single-LLM-call retry point (design
     * {@code nop-ai-agent-llm-layer.md} §7 / {@code nop-ai-agent-reliability.md}
     * §3.1). When {@code chatService.call(...)} throws, the retry loop
     * classifies the error and asks the policy RETRY / STOP / FALLBACK.
     * Optional: when null, falls back to {@link NoRetryPolicy} (unconditional
     * STOP — fail fast, backward compatible, zero-regression) so the retry
     * loop always has a non-null policy. Retry is not a security component,
     * so no insecure-default WARN is emitted.
     */
    public void setRetryPolicy(IRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy != null ? retryPolicy : new StandardRetryPolicy();
    }

    public IRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public void setCircuitBreaker(ICircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new ThresholdBreaker();
    }

    public ICircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Plan 211 (L3-3): wire a functional {@link IGoalTracker} consulted at the
     * ReAct loop's per-iteration boundary (design
     * {@code nop-ai-agent-reliability.md} §5.3). {@code recordIteration} is
     * called once per iteration after the LLM response so the tracker can
     * update its per-session progress state; {@code assessGoal} is called at
     * the next iteration's start and a STUCK return aborts the loop with
     * status=escalated. Optional: when null, falls back to
     * {@link NoOpGoalTracker} (unconditional PROGRESSING + explicit no-op
     * recording — backward compatible, zero-regression) so the ReAct loop
     * always has a non-null tracker. Goal tracking is not a security
     * component, so no insecure-default WARN is emitted.
     */
    public void setGoalTracker(IGoalTracker goalTracker) {
        this.goalTracker = goalTracker != null ? goalTracker : NoOpGoalTracker.noOp();
    }

    public IGoalTracker getGoalTracker() {
        return goalTracker;
    }

    /**
     * Plan 212 (L3-8): wire a functional {@link ISustainer} consulted at the
     * ReAct loop's exit decision point (design
     * {@code nop-ai-agent-reliability.md} §5.1a Sisyphean model). When the
     * reactLoop exits naturally because the iteration budget was exhausted
     * (MAX_ITERATIONS) while the status is still running, the engine asks the
     * sustainer CONTINUE (extend the budget by one sustain-round step and
     * re-enter the loop) or STOP (proceed to the terminal-state change).
     * Optional: when null, falls back to {@link NoOpSustainer} (unconditional
     * STOP — backward compatible, zero-regression) so the ReAct loop always
     * has a non-null sustainer. Sustaining is not a security component, so no
     * insecure-default WARN is emitted.
     */
    public void setSustainer(ISustainer sustainer) {
        this.sustainer = sustainer != null ? sustainer : NoOpSustainer.noOp();
    }

    public ISustainer getSustainer() {
        return sustainer;
    }

    /**
     * Plan 214 (L2-13a): wire a functional {@link IConflictStrategy}
     * consulted in the dispatch loop after the Layer 3 approval gate and
     * before {@code allowedCalls.add(...)} (design
     * {@code nop-ai-agent-multi-agent.md} §4.4). When the
     * {@link IWriteIntentRegistry} reports that the current tool call's
     * write intent collides with another session's existing intent on the
     * same file, the strategy decides ALLOW or DENY. Optional: when null,
     * falls back to {@link FailFastStrategy} (fail-fast on cross-session
     * conflicts — backward compatible, zero-regression for single-session
     * executions). Conflict detection is not a security component, so no
     * insecure-default WARN is emitted.
     */
    public void setConflictStrategy(IConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy != null
                ? conflictStrategy
                : FailFastStrategy.failFast();
    }

    public IConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    /**
     * Plan 214 (L2-13a): wire the {@link IWriteIntentRegistry} consulted in
     * the dispatch loop to register write intents and detect cross-session
     * conflicts (design {@code nop-ai-agent-multi-agent.md} §3.1). Optional:
     * when null, falls back to {@link InMemoryWriteIntentRegistry}
     * (in-process registry — backward compatible). Cross-process registry
     * (DB-backed) is a successor that depends on the L4-8 Actor Runtime.
     */
    public void setWriteIntentRegistry(IWriteIntentRegistry writeIntentRegistry) {
        this.writeIntentRegistry = writeIntentRegistry != null
                ? writeIntentRegistry
                : new InMemoryWriteIntentRegistry();
    }

    public IWriteIntentRegistry getWriteIntentRegistry() {
        return writeIntentRegistry;
    }

    /**
     * Plan 217 (L4-6): wire a functional {@link IContributionRegistry} that
     * holds the 7 plugin contribution types (TOOL / COMMAND / HOOK /
     * MCP_SERVER / PERMISSION_RULE / PROMPT / ROUTER). The engine resolves
     * HOOK contributions into the per-execution hook registry at
     * {@code resolveExecutor} assembly time (after
     * {@code DefaultHookRegistry.fromAgentModel(model)}), and the executor
     * resolves PROMPT contributions into the system prompt at setup time
     * (additive, alongside skill instructions). The other five types are
     * queryable via {@link IContributionRegistry#getContributions} but are
     * not auto-resolved into their eventual extension points in this
     * version — each is an explicit successor.
     *
     * <p>Optional: when null, falls back to {@link NoOpContributionRegistry}
     * (register is an explicit false, queries return empty — zero behaviour
     * regression). Contributions are incremental capability, so no
     * insecure-default WARN is emitted (consistent with IMailbox /
     * IUsageRecorder adjudication).
     */
    public void setContributionRegistry(IContributionRegistry contributionRegistry) {
        this.contributionRegistry = contributionRegistry != null
                ? contributionRegistry
                : NoOpContributionRegistry.noOp();
    }

    /**
     * Return the {@link IContributionRegistry} wired into this engine, or the
     * {@link NoOpContributionRegistry} default if none was explicitly set.
     */
    public IContributionRegistry getContributionRegistry() {
        return contributionRegistry;
    }

    /**
     * Plan 218 (L4-8): wire an optional {@link IActorRuntime} that manages
     * {@link io.nop.ai.agent.runtime.AgentActor} instances. When the runtime
     * is enabled ({@code isEnabled() == true}), the three execution entry
     * points (doExecute / resumeSession / restoreSession) additionally
     * register an Actor at supplyAsync-lambda entry (after session status is
     * set to running) and destroy it in the finally block. The Actor runs an
     * observation-only mailbox consumption loop on a dedicated single thread
     * — it is an execution container/observer, NOT a replacement for the
     * ReAct executor.
     *
     * <p>Optional: when null, falls back to {@link NoOpActorRuntime}
     * ({@code isEnabled() == false} — the engine skips the Actor path
     * entirely, zero behaviour regression). Actor runtime is incremental
     * capability, so no insecure-default WARN is emitted (consistent with
     * IMailbox / IContributionRegistry adjudication).
     */
    public void setActorRuntime(IActorRuntime actorRuntime) {
        this.actorRuntime = actorRuntime != null ? actorRuntime : NoOpActorRuntime.noOp();
    }

    /**
     * Return the {@link IActorRuntime} wired into this engine, or the
     * {@link NoOpActorRuntime} default if none was explicitly set.
     */
    public IActorRuntime getActorRuntime() {
        return actorRuntime;
    }

    /**
     * Plan 223 (L4-8-team-manager): wire an optional {@link ITeamManager}
     * that manages agent team lifecycles (create / disband / member
     * management / status query). The engine does NOT call the teamManager
     * on its execution path in the foundational slice — team creation and
     * member binding are driven by integrators / successor team tools
     * (e.g. {@code team-task-create} / {@code team-send-message} /
     * {@code team-status} as IToolExecutor, which are explicit successors).
     *
     * <p>Optional: when null, falls back to {@link NoOpTeamManager}
     * (write operations throw {@link UnsupportedOperationException}, read
     * operations return empty — Minimum Rules #24 No Silent No-Op, zero
     * behaviour regression). TeamManager is incremental capability, so no
     * insecure-default WARN is emitted (consistent with IActorRuntime /
     * IMailbox adjudication).
     */
    public void setTeamManager(ITeamManager teamManager) {
        this.teamManager = teamManager != null ? teamManager : NoOpTeamManager.noOp();
    }

    /**
     * Return the {@link ITeamManager} wired into this engine, or the
     * {@link NoOpTeamManager} default if none was explicitly set.
     */
    public ITeamManager getTeamManager() {
        return teamManager;
    }

    /**
     * Plan 225 (L4-8-team-tools): wire an optional {@link ITeamTaskStore}
     * that backs the {@code team-task-create} tool and the {@code team-status}
     * task count. The engine does NOT call the teamTaskStore on its execution
     * path — team tools consume it at execution time via
     * {@link AgentToolExecuteContext#getTeamTaskStore()}.
     *
     * <p>Optional: when null, falls back to {@link NoOpTeamTaskStore}
     * (createTask throws {@link UnsupportedOperationException}, queries return
     * empty — Minimum Rules #24 No Silent No-Op, zero behaviour regression).
     * TeamTaskStore is incremental capability, so no insecure-default WARN
     * is emitted (consistent with teamManager / IActorRuntime adjudication).
     */
    public void setTeamTaskStore(ITeamTaskStore teamTaskStore) {
        this.teamTaskStore = teamTaskStore != null ? teamTaskStore : NoOpTeamTaskStore.noOp();
    }

    /**
     * Return the {@link ITeamTaskStore} wired into this engine, or the
     * {@link NoOpTeamTaskStore} default if none was explicitly set.
     */
    public ITeamTaskStore getTeamTaskStore() {
        return teamTaskStore;
    }

    /**
     * Plan 228 (L4-team-acl-enforcement): wire an optional
     * {@link ITeamAclChecker} that the 4 team tool executors
     * (team-send-message / team-status / team-task-create / team-task-update)
     * consult after resolving the caller's team and before performing the
     * actual operation. The engine does NOT call the checker on its dispatch
     * path — team tools consume it at execution time via
     * {@link AgentToolExecuteContext#getTeamAclChecker()}.
     *
     * <p>Optional: when null, falls back to {@link NoOpTeamAclChecker}
     * ({@code checkAccess} always returns {@code allow(null)} — an explicit
     * allow decision, not a silent skip; zero behaviour regression).
     * TeamAclChecker is incremental capability, so no insecure-default WARN
     * is emitted (consistent with teamManager / teamTaskStore adjudication).
     */
    public void setTeamAclChecker(ITeamAclChecker teamAclChecker) {
        this.teamAclChecker = teamAclChecker != null ? teamAclChecker : NoOpTeamAclChecker.noOp();
    }

    /**
     * Return the {@link ITeamAclChecker} wired into this engine, or the
     * {@link NoOpTeamAclChecker} default if none was explicitly set.
     */
    public ITeamAclChecker getTeamAclChecker() {
        return teamAclChecker;
    }

    /**
     * Plan 219 (L4-7): wire the {@link io.nop.ai.agent.security.ISandboxBackend}
     * — the Layer 4 defense-in-depth chain tail (design §7.1 / §8). The
     * sandbox backend is platform-level isolation infrastructure for
     * high-risk tool executors (shell-exec / code-exec IToolExecutor
     * successors) to consume; the engine itself does not call it on the
     * dispatch path. The wired backend is propagated to the
     * {@link ReActAgentExecutor} via {@link ReActAgentExecutor.Builder#sandboxBackend}
     * in {@code resolveExecutor} so a functional tool executor running
     * inside the ReAct loop can reach it.
     *
     * <p>Shipped default is {@link io.nop.ai.agent.security.NoOpSandboxBackend}
     * (host ProcessBuilder execution — the Layer 1 designable baseline,
     * design §7.1 "Noop | 无隔离（默认）"). Optional: when null, falls back
     * to {@link io.nop.ai.agent.security.NoOpSandboxBackend#INSTANCE}.
     *
     * <p>This setter does NOT call {@code warnIfInsecureDefaults}. NoOp is
     * the starting state (not a downgrade of a more-secure shipped
     * alternative); see the field-level comment and plan 219 Phase 1
     * Decision for the full rationale.
     */
    public void setSandboxBackend(io.nop.ai.agent.security.ISandboxBackend sandboxBackend) {
        this.sandboxBackend = sandboxBackend != null
                ? sandboxBackend
                : io.nop.ai.agent.security.NoOpSandboxBackend.INSTANCE;
    }

    /**
     * Return the {@link io.nop.ai.agent.security.ISandboxBackend} wired
     * into this engine, or the {@link io.nop.ai.agent.security.NoOpSandboxBackend}
     * default if none was explicitly set.
     */
    public io.nop.ai.agent.security.ISandboxBackend getSandboxBackend() {
        return sandboxBackend;
    }

    /**
     * Plan 221 (L4-8-P4): wire an optional cross-process
     * {@link ISessionTakeoverLock} that prevents two JVM instances sharing
     * the same backing store from simultaneously restoring and executing
     * the same crashed/pending session (double-execution correctness gap).
     *
     * <p>When a functional lock is wired (e.g.
     * {@link io.nop.ai.agent.runtime.lock.DbSessionTakeoverLock}), the
     * three execution entry points (doExecute / resumeSession /
     * restoreSession) call {@code tryAcquire(sessionId, instanceId,
     * lockLeaseMs)} before {@code putIfAbsent} and release it on every
     * cleanup path via {@code releaseLockQuietly}.
     * {@code restorePendingSessions} additionally consults {@code isHeld}
     * to skip sessions already being processed by another instance.
     *
     * <p>Optional: when null, falls back to
     * {@link NoOpSessionTakeoverLock} ({@code tryAcquire} unconditionally
     * returns {@code true}, {@code isHeld} returns {@code false} — engine
     * walks the existing in-process {@code putIfAbsent} path, zero
     * behaviour regression). The takeover lock is incremental capability,
     * so no insecure-default WARN is emitted (consistent with
     * {@code IActorRuntime} / {@code IMailbox} adjudication).
     */
    public void setSessionTakeoverLock(ISessionTakeoverLock sessionTakeoverLock) {
        this.sessionTakeoverLock = sessionTakeoverLock != null
                ? sessionTakeoverLock
                : NoOpSessionTakeoverLock.noOp();
    }

    /**
     * Return the {@link ISessionTakeoverLock} wired into this engine, or
     * the {@link NoOpSessionTakeoverLock} default if none was explicitly
     * set.
     */
    public ISessionTakeoverLock getSessionTakeoverLock() {
        return sessionTakeoverLock;
    }

    /**
     * Plan 221 (L4-8-P4): set the lease duration (in ms) used when
     * acquiring the takeover lock. Default = {@code 1_800_000L} (30 min).
     * Integrators may align this with the agent's
     * {@code maxWallClockMinutes}. The lease is passive — the lock
     * auto-expires when the holder crashes, no background sweeper thread.
     */
    public void setLockLeaseMs(long lockLeaseMs) {
        this.lockLeaseMs = lockLeaseMs;
    }

    public long getLockLeaseMs() {
        return lockLeaseMs;
    }

    /**
     * Plan 273 (carry-over 14-06): set the heartbeat renewal interval (in
     * ms) for the takeover lock. While an execution is running, the engine
     * schedules a periodic {@link ISessionTakeoverLock#tryRenew} at this
     * interval so long-running agents (&gt; {@code lockLeaseMs}) do not let
     * their lease expire and get preempted by another JVM instance
     * (double-execution). Default = {@code 600_000L} (10 min, 1/3 of the
     * default 30min lease). A value {@code <= 0} disables the renewal
     * scheduler (the lease then behaves as pure passive TTL — backward-
     * compatible escape hatch). Only takes effect when a functional
     * takeover lock is wired; under the shipped
     * {@link NoOpSessionTakeoverLock} default the renewal task is a
     * harmless no-op.
     */
    public void setLockRenewIntervalMs(long lockRenewIntervalMs) {
        this.lockRenewIntervalMs = lockRenewIntervalMs;
    }

    public long getLockRenewIntervalMs() {
        return lockRenewIntervalMs;
    }

    /**
     * Plan 222 (L4-8-P4-RecoveryDaemon): wire an optional
     * {@link IRecoveryManager} daemon that continuously sweeps stale
     * takeover locks and detects orphan sessions in multi-instance
     * unattended deployments (complementing the one-shot
     * {@code restorePendingSessions} startup scan).
     *
     * <p>The engine does <b>not</b> call {@code start()}/{@code stop()} on
     * the manager — per {@code IAgentEngine}'s design contract
     * (deployment-layer lifecycle decision, see
     * {@code IAgentEngine.java:166-171}). Integrators wire the manager
     * here, then call {@code start()} (e.g. after app startup) and
     * {@code stop()} (e.g. before app shutdown) from the deployment layer.
     *
     * <p>Optional: when null, falls back to
     * {@link NoOpRecoveryManager} ({@code scanOnce} returns an all-zero
     * {@code RecoveryScanResult}, {@code start}/{@code stop} are no-ops —
     * zero behaviour regression). The RecoveryManager is incremental
     * capability, so no insecure-default WARN is emitted.
     */
    public void setRecoveryManager(IRecoveryManager recoveryManager) {
        this.recoveryManager = recoveryManager != null
                ? recoveryManager
                : NoOpRecoveryManager.noOp();
    }

    /**
     * Return the {@link IRecoveryManager} wired into this engine, or the
     * {@link NoOpRecoveryManager} default if none was explicitly set.
     */
    public IRecoveryManager getRecoveryManager() {
        return recoveryManager;
    }

    /**
     * Plan 236 (L4-blockedBy-resolution-engine): wire an optional
     * {@link ITeamTaskSchedulerDaemon} that continuously sweeps dependency-
     * ready team tasks and auto-claims + auto-dispatches them in dependency
     * order, closing the "unattended multi-agent orchestration" loop (a
     * successor to plan 233's manual {@code TeamTaskFlowOrchestrator.execute}
     * entry point).
     *
     * <p>The engine does <b>not</b> call {@code start()}/{@code stop()} on
     * the daemon — per the same design contract as
     * {@link #setRecoveryManager} (deployment-layer lifecycle decision,
     * see {@code IAgentEngine.java:166-171}). Integrators wire the daemon
     * here, then call {@code start()} (e.g. after app startup) and
     * {@code stop()} (e.g. before app shutdown) from the deployment layer.
     *
     * <p>The functional {@link io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon}
     * consumes the engine's already-wired {@link #getTeamManager()},
     * {@link #getTeamTaskStore()}, and this engine itself (as the
     * {@link IAgentEngine} for member-agent delegation). It does NOT call
     * {@code TeamTaskFlowOrchestrator.execute(teamId)}; instead each scan
     * resolves ready tasks via {@link io.nop.ai.agent.team.flow.TeamTaskTopology}
     * and dispatches each CAS-claimed task to a bound member agent (plan
     * 236 design 裁定 1). See plan 236 for the full scheduling / dependency-
     * order / lifecycle / failure adjudication.
     *
     * <p>Optional: when null, falls back to
     * {@link NoOpTeamTaskSchedulerDaemon} ({@code scanOnce} returns an
     * all-zero {@link io.nop.ai.agent.team.scheduler.SchedulerScanResult},
     * {@code start}/{@code stop} are no-ops — zero behaviour regression).
     * The daemon is incremental capability, so no insecure-default WARN is
     * emitted.
     */
    public void setTeamTaskSchedulerDaemon(ITeamTaskSchedulerDaemon teamTaskSchedulerDaemon) {
        this.teamTaskSchedulerDaemon = teamTaskSchedulerDaemon != null
                ? teamTaskSchedulerDaemon
                : NoOpTeamTaskSchedulerDaemon.noOp();
    }

    /**
     * Return the {@link ITeamTaskSchedulerDaemon} wired into this engine, or
     * the {@link NoOpTeamTaskSchedulerDaemon} default if none was explicitly
     * set.
     */
    public ITeamTaskSchedulerDaemon getTeamTaskSchedulerDaemon() {
        return teamTaskSchedulerDaemon;
    }
    public void setPermissionProvider(IPermissionProvider permissionProvider) { this.permissionProvider = permissionProvider; }
    public IPermissionProvider getPermissionProvider() { return permissionProvider; }
    public void setToolAccessChecker(IToolAccessChecker toolAccessChecker) { this.toolAccessChecker = toolAccessChecker; }
    public IToolAccessChecker getToolAccessChecker() { return toolAccessChecker; }
    public void setPathAccessChecker(IPathAccessChecker pathAccessChecker) { this.pathAccessChecker = pathAccessChecker; }
    public IPathAccessChecker getPathAccessChecker() { return pathAccessChecker; }
    public void setContentGuardrail(IContentGuardrail contentGuardrail) { this.contentGuardrail = contentGuardrail; }
    public IContentGuardrail getContentGuardrail() { return contentGuardrail; }
    public void setModelRouter(IModelRouter modelRouter) { this.modelRouter = modelRouter; }
    public IModelRouter getModelRouter() { return modelRouter; }
    public void setContextCompactor(IContextCompactor contextCompactor) { this.contextCompactor = contextCompactor; }
    public IContextCompactor getContextCompactor() { return contextCompactor; }
    public void setTokenEstimator(ITokenEstimator tokenEstimator) { this.tokenEstimator = tokenEstimator; }
    public ITokenEstimator getTokenEstimator() { return tokenEstimator; }
    public void setTalents(java.util.List<ITalent> talents) { this.talents = talents != null ? talents : java.util.Collections.emptyList(); }
    public java.util.List<ITalent> getTalents() { return talents; }
    public Predicate<ChatMessage> getForkMessageFilter() { return forkMessageFilter; }
    public ISkillProvider getSkillProvider() { return skillProvider; }
    public ISkillCurator getSkillCurator() { return skillCurator; }
    public IToolCallRepairer getToolCallRepairer() { return toolCallRepairer; }
    public int getMemoryInjectionBudgetTokens() { return memoryInjectionBudgetTokens; }
    public void setMemoryInjectionBudgetTokens(int memoryInjectionBudgetTokens) { this.memoryInjectionBudgetTokens = memoryInjectionBudgetTokens; }
    public long getCallAgentTimeoutMs() { return callAgentTimeoutMs; }
    public void setCallAgentTimeoutMs(long callAgentTimeoutMs) {
        if (callAgentTimeoutMs <= 0) {
            throw new NopAiAgentException("callAgentTimeoutMs must be positive, got: " + callAgentTimeoutMs);
        }
        this.callAgentTimeoutMs = callAgentTimeoutMs;
    }
    public long getLlmTimeoutMs() { return llmTimeoutMs; }
    public void setLlmTimeoutMs(long llmTimeoutMs) { this.llmTimeoutMs = llmTimeoutMs; }
    public long getToolTimeoutMs() { return toolTimeoutMs; }
    public void setToolTimeoutMs(long toolTimeoutMs) { this.toolTimeoutMs = toolTimeoutMs; }

}
