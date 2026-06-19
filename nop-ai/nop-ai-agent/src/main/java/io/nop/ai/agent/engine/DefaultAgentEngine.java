package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.Layer2TurnPruningStrategy;
import io.nop.ai.agent.compact.Layer3FullSummaryStrategy;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.compact.PipelineCompactor;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.contribution.Contribution;
import io.nop.ai.agent.contribution.ContributionType;
import io.nop.ai.agent.contribution.HookPayload;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.memory.InMemoryMemoryStoreProvider;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.CallAgentRequestPayload;
import io.nop.ai.agent.message.CallAgentResponsePayload;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxMessageHandler;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.TeamMemberRefModel;
import io.nop.ai.agent.model.TeamModel;
import io.nop.ai.agent.repair.IToolCallRepairer;
import io.nop.ai.agent.reliability.AlwaysClosed;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.ICircuitBreaker;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.ISustainer;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.reliability.NoOpGoalTracker;
import io.nop.ai.agent.reliability.NoOpSustainer;
import io.nop.ai.agent.reliability.NoRetryPolicy;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.runtime.IActorRuntime;
import io.nop.ai.agent.runtime.NoOpActorRuntime;
import io.nop.ai.agent.runtime.lock.ISessionTakeoverLock;
import io.nop.ai.agent.runtime.lock.NoOpSessionTakeoverLock;
import io.nop.ai.agent.runtime.recovery.IRecoveryManager;
import io.nop.ai.agent.runtime.recovery.NoOpRecoveryManager;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultLevelHintsProducer;
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
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.NoOpModelSwitchedMessageWriter;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.ISkillCurator;
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
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamModelConverter;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamStatus;
import io.nop.ai.agent.team.scheduler.ITeamTaskSchedulerDaemon;
import io.nop.ai.agent.team.scheduler.NoOpTeamTaskSchedulerDaemon;
import io.nop.ai.agent.usage.IUsageRecorder;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
    // Plan 224 (L4-8-call-agent-async): subscription for the engine-level
    // call-agent request handler. Registered on the call-agent topic when a
    // functional messenger is wired via setMessenger; cancelled and re-registered
    // idempotently on each setMessenger call. NoOp messenger leaves it null
    // (no registration, zero regression). The handler executes the requested
    // sub-agent via engine.execute() and returns a CallAgentResponsePayload.
    private volatile IMessageSubscription callAgentSubscription;
    // Plan 216 (L4-5): optional per-session deferred-ack mailbox factory. When
    // non-null, the engine creates a mailbox for each session on first
    // execution and registers a MailboxMessageHandler on the session's inbox
    // topic so that ASYNC messages delivered via the messenger are buffered in
    // the mailbox for deferred-ack consumption. Shipped default is null (no
    // mailbox — messages go through the existing synchronous handler path,
    // zero behaviour regression). A functional factory (e.g.
    // sessionId -> new DeferredAckMailbox(...)) is registered via
    // setMailboxFactory.
    private Function<String, IMailbox> mailboxFactory;
    private final ConcurrentHashMap<String, IMailbox> sessionMailboxes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IMessageSubscription> sessionMailboxSubscriptions = new ConcurrentHashMap<>();
    private IPermissionMatrix permissionMatrix = new DefaultPermissionMatrix();
    private ISecurityLevelResolver securityLevelResolver = new DefaultSecurityLevelResolver();
    private ILevelHintsProducer levelHintsProducer = new DefaultLevelHintsProducer();
    private IApprovalGate approvalGate = new DefaultApprovalGate();
    private IDenialLedger denialLedger = new DefaultDenialLedger();
    private IPostDenialGuard postDenialGuard = new DefaultPostDenialGuard();
    // Plan 194 (AUDIT-13-02): audit logger defaults to Slf4jAuditLogger so the
    // engine produces a visible audit trail out-of-the-box (tool decisions are
    // logged to SLF4J INFO) instead of silently discarding audit events via
    // NoOpAuditLogger. Integrators can replace it via setAuditLogger (e.g. to
    // write audit events to a database).
    private IAuditLogger auditLogger = new Slf4jAuditLogger();
    private ICheckpointManager checkpointManager = NoOpCheckpoint.noOp();
    private IMemoryStoreProvider memoryStoreProvider = new InMemoryMemoryStoreProvider();
    // Plan 201 (L2-17): usage recorder defaults to NoOpUsageRecorder so the
    // ReAct loop's token accumulation point is wired out-of-the-box without
    // forcing a persistence sink. A functional recorder (e.g. DbUsageRecorder,
    // L2-18) is registered via setUsageRecorder.
    private IUsageRecorder usageRecorder = NoOpUsageRecorder.noOp();
    // Plan 205 (L2-21): model-switched message writer defaults to
    // NoOpModelSwitchedMessageWriter so the ReAct loop's model-switch detection
    // is wired out-of-the-box without forcing a persistence sink. A functional
    // writer (e.g. DbModelSwitchedMessageWriter) is registered via
    // setModelSwitchedMessageWriter.
    private IModelSwitchedMessageWriter modelSwitchedMessageWriter = NoOpModelSwitchedMessageWriter.noOp();
    // Plan 206 (L2-22): budget provider defaults to NoOpBudgetProvider so the
    // ReAct loop's pre-route budget refresh is wired out-of-the-box without
    // forcing a cost/limit source. A functional provider (e.g. a future
    // DbBudgetProvider) is registered via setBudgetProvider. Combined with the
    // shipped PassThroughModelRouter (which never changes the model), the
    // shipped default behaviour is zero-change — no budget-driven downgrade.
    private IBudgetProvider budgetProvider = NoOpBudgetProvider.noOp();
    // Plan 207 (L3-2): retry policy defaults to NoRetryPolicy so the ReAct
    // loop's single-LLM-call retry point is wired out-of-the-box without
    // changing the pre-plan-207 fail-fast behaviour (NoRetryPolicy
    // unconditionally returns STOP → the call executes exactly once and any
    // exception propagates as-is). A functional policy (e.g.
    // StandardRetryPolicy) is registered via setRetryPolicy.
    private IRetryPolicy retryPolicy = NoRetryPolicy.noRetry();
    // Plan 210 (L3-1): circuit breaker defaults to AlwaysClosed so the ReAct
    // loop's single-LLM-call outer check is wired out-of-the-box without
    // changing the pre-plan-210 zero-circuit-breaking behaviour (AlwaysClosed
    // unconditionally allows every call and treats recording as explicit
    // no-ops). A functional breaker (e.g. ThresholdBreaker) is registered via
    // setCircuitBreaker.
    private ICircuitBreaker circuitBreaker = AlwaysClosed.alwaysClosed();
    // Plan 211 (L3-3): goal tracker defaults to NoOpGoalTracker so the ReAct
    // loop's per-iteration progress assessment is wired out-of-the-box without
    // changing the pre-plan-211 behaviour (NoOpGoalTracker unconditionally
    // reports PROGRESSING and treats recordIteration as an explicit no-op, so
    // no STUCK abort ever fires). A functional tracker (e.g.
    // SessionGoalTracker) is registered via setGoalTracker.
    private IGoalTracker goalTracker = NoOpGoalTracker.noOp();
    // Plan 212 (L3-8): sustainer defaults to NoOpSustainer so the ReAct
    // loop's exit decision point is wired out-of-the-box without changing
    // the pre-plan-212 zero-sustain behaviour (NoOpSustainer unconditionally
    // returns STOP, so the loop exits the first time it would naturally stop).
    // A functional sustainer (e.g. SisypheanSustainer) is registered via
    // setSustainer.
    private ISustainer sustainer = NoOpSustainer.noOp();
    // Plan 214 (L2-13a): conflict strategy defaults to FailFastStrategy so
    // the ReAct loop's dispatch-path conflict detection is wired
    // out-of-the-box. FailFastStrategy is a functional default (not an
    // insecure pass-through): single-session executions never see a conflict
    // (the registry contains no other-session intents), so the default is
    // zero-regression; multi-session executions get fail-fast cross-session
    // write-conflict detection. Conflict detection is a coordination
    // concern (not a security boundary), so no insecure-default WARN is
    // emitted. A functional strategy (e.g. a future
    // CoordinationBusStrategy) is registered via setConflictStrategy.
    private IConflictStrategy conflictStrategy = FailFastStrategy.failFast();
    // Plan 214 (L2-13a): write-intent registry defaults to the in-process
    // InMemoryWriteIntentRegistry. The dispatch path registers a write
    // intent before allowing a tool call with a path argument; the strategy
    // is consulted only when another session has an active intent on the
    // same path. Cross-process registry (DB-backed) is a successor that
    // depends on the L4-8 Actor Runtime.
    private IWriteIntentRegistry writeIntentRegistry = new InMemoryWriteIntentRegistry();
    // Plan 217 (L4-6): plugin contribution registry. Defaults to
    // NoOpContributionRegistry (empty registry — register is an explicit
    // false, queries return empty, zero behaviour regression). Integrators
    // wire a functional registry (e.g. InMemoryContributionRegistry) and
    // register contributions via register(...) to enable assembly-time
    // resolution. The engine resolves HOOK contributions into the hook
    // registry in resolveExecutor, and the executor resolves PROMPT
    // contributions into the system prompt in its setup phase. The other
    // five contribution types are queryable but not auto-resolved by the
    // engine in this version (explicit successor each). Contributions are
    // incremental capability (NoOp → system runs normally), so no
    // insecure-default WARN is emitted (consistent with IMailbox /
    // IUsageRecorder adjudication).
    private IContributionRegistry contributionRegistry = NoOpContributionRegistry.noOp();
    // Plan 218 (L4-8): optional Actor Runtime container. Defaults to
    // NoOpActorRuntime (isEnabled() returns false — the engine skips the
    // Actor path entirely, zero behaviour regression). When a functional
    // runtime is wired (e.g. InMemoryActorRuntime via setActorRuntime), the
    // three execution entry points (doExecute / resumeSession / restoreSession)
    // additionally register an AgentActor at supplyAsync-lambda entry and
    // destroy it in the finally block — the Actor runs an observation-only
    // mailbox consumption loop on a dedicated single thread, NOT replacing the
    // ReAct executor. Actor is an execution container/observer, not an engine
    // replacement. The Actor runtime is incremental capability (NoOp → engine
    // walks existing supplyAsync path), so no insecure-default WARN is emitted
    // (consistent with IMailbox / IContributionRegistry adjudication).
    private IActorRuntime actorRuntime = NoOpActorRuntime.noOp();
    // Plan 223 (L4-8-team-manager): optional team lifecycle manager. Defaults
    // to NoOpTeamManager (all write operations throw
    // UnsupportedOperationException, all read operations return empty —
    // Minimum Rules #24 No Silent No-Op) so engine behaviour is unchanged
    // unless a functional manager (e.g. InMemoryTeamManager) is explicitly
    // wired via setTeamManager. The engine does NOT call teamManager on its
    // execution path in the foundational slice — team creation and member
    // binding are driven by integrators/tools (successor team tools). The
    // TeamManager is incremental capability (NoOp → engine runs unchanged),
    // so no insecure-default WARN is emitted (consistent with IActorRuntime /
    // IMailbox / IContributionRegistry adjudication). Auto team binding
    // (creating a team from agent config's TeamSpec + binding member sessions
    // at the three entry points) is an explicit successor that depends on
    // TeamSpec XDSL configuration.
    private ITeamManager teamManager = NoOpTeamManager.noOp();
    // Plan 225 (L4-8-team-tools): optional team task store backing the
    // team-task-create tool and team-status task count. Defaults to
    // NoOpTeamTaskStore (createTask throws UnsupportedOperationException,
    // queries return empty — Minimum Rules #24 No Silent No-Op) so engine
    // behaviour is unchanged unless a functional store (e.g.
    // InMemoryTeamTaskStore) is explicitly wired via setTeamTaskStore. The
    // engine does NOT call teamTaskStore on its execution path — team tools
    // consume it at execution time. The TeamTaskStore is incremental
    // capability (NoOp → engine runs unchanged), so no insecure-default WARN
    // is emitted (consistent with teamManager / IActorRuntime adjudication).
    private ITeamTaskStore teamTaskStore = NoOpTeamTaskStore.noOp();
    // Plan 228 (L4-team-acl-enforcement): optional team ACL checker backing
    // the 4 team tool executors' permission check. Defaults to
    // NoOpTeamAclChecker (checkAccess always returns allow(null) —
    // Minimum Rules #24: an explicit allow decision, not a silent skip) so
    // engine behaviour is unchanged unless a functional checker (e.g.
    // DefaultTeamAclChecker enforcing the §5.1 default role matrix) is
    // explicitly wired via setTeamAclChecker. The engine does NOT call
    // teamAclChecker on its dispatch path — the 4 team tools consult it at
    // execution time via AgentToolExecuteContext.getTeamAclChecker(). The
    // TeamAclChecker is incremental capability (NoOp → engine runs
    // unchanged), so no insecure-default WARN is emitted (consistent with
    // teamManager / teamTaskStore adjudication).
    private ITeamAclChecker teamAclChecker = NoOpTeamAclChecker.noOp();
    // Plan 219 (L4-7): sandbox backend — the Layer 4 defense-in-depth chain
    // tail (design §7.1 / §8). Defaults to NoOpSandboxBackend (host
    // ProcessBuilder execution — Layer 1 designable baseline, design §7.1
    // "Noop | 无隔离（默认）"). A functional backend (e.g.
    // DockerSandboxBackend) is registered via setSandboxBackend. The
    // backend is platform-level isolation infrastructure provided for
    // high-risk tool executors to consume (shell-exec / code-exec
    // IToolExecutor successors); the engine does not call it on the
    // dispatch path itself. NoOp is NOT a fallback — it is the starting
    // state, and (unlike AutoApproveGate which was superseded by
    // DefaultApprovalGate) has never been superseded by a more-secure
    // shipped alternative, so setSandboxBackend does NOT call
    // warnIfInsecureDefaults (plan 219 Phase 1 Decision). If a future
    // DefaultSandboxBackend (host hardening such as seccomp/chroot) ships
    // and becomes the default, the WARN will be added at that time.
    private io.nop.ai.agent.security.ISandboxBackend sandboxBackend =
            io.nop.ai.agent.security.NoOpSandboxBackend.INSTANCE;
    // Plan 221 (L4-8-P4): cross-process session takeover lock. Defaults to
    // NoOpSessionTakeoverLock — single-process deployments keep relying on
    // the engine's in-process runningExecutions.putIfAbsent guard (plan
    // 197), so engine behaviour is unchanged unless a functional lock
    // (e.g. DbSessionTakeoverLock) is explicitly wired via
    // setSessionTakeoverLock. When a functional lock is wired, the three
    // execution entry points (doExecute / resumeSession / restoreSession)
    // call tryAcquire before putIfAbsent and release it on every cleanup
    // path (putIfAbsent-fail / outer catch / inner finally) via the
    // fault-tolerant releaseLockQuietly helper. restorePendingSessions
    // additionally consults isHeld to skip sessions already being
    // processed by another instance. The takeover lock is incremental
    // capability (NoOp → engine walks existing supplyAsync path), so no
    // insecure-default WARN is emitted (consistent with IActorRuntime /
    // IMailbox / IContributionRegistry adjudication).
    private ISessionTakeoverLock sessionTakeoverLock = NoOpSessionTakeoverLock.noOp();
    // Plan 221 (L4-8-P4): engine instance identity. Generated once at
    // construction via UUID.randomUUID(). Used as the ownerId argument to
    // sessionTakeoverLock.tryAcquire / release / tryRenew so that
    // conditional release only frees this engine's own locks. Immutable
    // after construction.
    private final String instanceId = UUID.randomUUID().toString();
    // Plan 221 (L4-8-P4): lease duration in milliseconds for the takeover
    // lock. Default = 30 minutes (1_800_000 ms). Integrators tune via
    // setLockLeaseMs (e.g. align with the agent's maxWallClockMinutes).
    // Passive TTL expiry: when the lock holder crashes, the lease
    // auto-expires and another instance can preempt — no background sweeper
    // thread is needed.
    private long lockLeaseMs = 1_800_000L;
    // Plan 222 (L4-8-P4-RecoveryDaemon): RecoveryManager daemon for
    // continuous periodic stale-lock cleanup + orphan-session detection.
    // Defaults to NoOpRecoveryManager — single-process deployments keep
    // relying on the one-shot startup restorePendingSessions scan, so
    // engine behaviour is unchanged unless a functional manager (e.g.
    // ScheduledRecoveryManager) is explicitly wired via
    // setRecoveryManager. The engine does NOT call
    // recoveryManager.start()/stop() — per IAgentEngine's design contract
    // (IAgentEngine.java:166-171) lifecycle management is a
    // deployment-layer decision, not an engine-layer contract. Integrators
    // wire the manager and call start()/stop() from the deployment layer
    // (e.g. after app startup / before app shutdown). The RecoveryManager
    // is incremental capability (NoOp → engine walks existing path), so
    // no insecure-default WARN is emitted (consistent with
    // IActorRuntime / ISessionTakeoverLock adjudication).
    private IRecoveryManager recoveryManager = NoOpRecoveryManager.noOp();
    // Plan 236 (L4-blockedBy-resolution-engine): optional team-task auto-
    // scheduling daemon that continuously sweeps dependency-ready team tasks
    // and auto-claims + auto-dispatches them, closing the "unattended multi-
    // agent orchestration" loop. Defaults to NoOpTeamTaskSchedulerDaemon
    // (start/stop are no-ops, scanOnce returns an all-zero result — zero
    // behaviour regression) so engine behaviour is unchanged unless a
    // functional daemon (e.g. TeamTaskSchedulerDaemon) is explicitly wired
    // via setTeamTaskSchedulerDaemon. The engine does NOT call
    // daemon.start()/stop() — per the same design contract as
    // IRecoveryManager / IAgentEngine (lifecycle management is a
    // deployment-layer decision). Integrators wire the daemon and call
    // start()/stop() from the deployment layer (e.g. after app startup /
    // before app shutdown). The daemon is incremental capability (NoOp →
    // engine runs unchanged), so no insecure-default WARN is emitted
    // (consistent with recoveryManager / teamManager / teamTaskStore
    // adjudication).
    private ITeamTaskSchedulerDaemon teamTaskSchedulerDaemon = NoOpTeamTaskSchedulerDaemon.noOp();
    // Plan 192: max token budget for budgeted working-memory auto-injection into
    // the system prompt. Shipped default gives memory a reasonable share of the
    // context without dominating it. A value <= 0 disables injection (explicit
    // opt-out / backward-compatible escape hatch).
    private int memoryInjectionBudgetTokens = 1024;

    // Plan 271 (finding 14-04): dedicated executor for the three engine entry
    // points (doExecute / resumeSession / restoreSession) so they no longer
    // share ForkJoinPool.commonPool() (default ~3-7 threads). Multiple
    // concurrent agents quickly exhausted the commonPool, causing cross-JVM
    // functional starvation. The shipped default is a virtual-thread-per-task
    // executor (Java 21) — unbounded, daemon, cheap to block — so concurrent
    // agent executions never starve each other. Integrators may override via
    // setAgentExecutor (e.g. a fixed-size pool for resource-constrained
    // deployments). The same executor is propagated to the ReAct executor for
    // wrapping the synchronous LLM call with a wall-clock timeout (finding
    // 14-03); virtual threads guarantee no self-deadlock when a task running on
    // this executor dispatches the LLM-call wrapper back to the same executor.
    private ExecutorService agentExecutor;

    // Plan 271 (finding 14-01): wall-clock timeout (ms) for a call-agent
    // sub-agent execution. On timeout the sub-agent's session is cancelled via
    // engine.cancelSession(forced=true) so its LLM/DB resources are released
    // rather than left running as a zombie. The default mirrors the
    // CallAgentExecutor's historical default (60s).
    private long callAgentTimeoutMs = 60_000L;

    // Plan 271 (finding 14-03): wall-clock timeout (ms) for a single LLM call
    // inside the ReAct loop. A value <= 0 disables the timeout (backward-
    // compatible escape hatch). The shipped default (120s) is generous enough
    // for slow first-token latencies while still bounding a permanently hung
    // connection so the agent session / worker thread / takeover lock are not
    // blocked indefinitely.
    private long llmTimeoutMs = 120_000L;

    // Plan 271 (finding 14-03): wall-clock timeout (ms) for a single tool call
    // inside the ReAct dispatch fanout. A value <= 0 disables the timeout
    // (backward-compatible escape hatch). The shipped default (300s) tolerates
    // long-running tools (e.g. shell-exec / code-exec) while still bounding a
    // permanently hung tool so the agent session is not blocked indefinitely.
    private long toolTimeoutMs = 300_000L;

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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, this.securityLevelResolver, this.permissionMatrix,
                this.denialLedger, this.postDenialGuard);
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
     * Emit a WARN when the resolved security components are insecure-default
     * instances. Makes security downgrades visible rather than silent (design
     * §4.6 / §4.7 / §4.8 / §4.9).
     *
     * <p><b>Always-checked components</b> (have secure defaults — checked on
     * every call, both at construction and at setter time):
     * <ul>
     *   <li>{@code AllowAllToolAccessChecker} / {@code AllowAllPathAccessChecker}
     *       (plan 193 — Layer 1)</li>
     *   <li>{@code NoOpAuditLogger} (plan 194 — Layer 1 audit)</li>
     *   <li>{@code AutoApproveGate} (plan 199 — Layer 3 approval gate;
     *       default switched to {@code DefaultApprovalGate})</li>
     *   <li>{@code NoOpSecurityLevelResolver} (plan 200 — Layer 2;
     *       default switched to {@code DefaultSecurityLevelResolver})</li>
     *   <li>{@code PassThroughPermissionMatrix} (plan 200 — Layer 2;
     *       default switched to {@code DefaultPermissionMatrix})</li>
     *   <li>{@code NoOpDenialLedger} (plan 200 — Layer 3;
     *       default switched to {@code DefaultDenialLedger})</li>
     *   <li>{@code PassThroughPostDenialGuard} (plan 200 — Layer 3;
     *       default switched to {@code DefaultPostDenialGuard})</li>
     * </ul>
     *
     * <p>Construction passes the resolved instances for all components (the
     * Default* defaults do not trigger WARN). Each setter passes the non-null
     * value it just set for its own component and {@code null} for the other
     * Layer 2/3 components (so calling {@code setSecurityLevelResolver} does
     * not produce spurious WARNs about the matrix/ledger/guard that the
     * caller never touched — noise control).
     *
     * <p>Fail-loud: every WARN is unconditionally emitted via {@code LOG.warn}
     * when an insecure instance is detected — never silently swallowed or
     * written as an empty body.
     */
    private static void warnIfInsecureDefaults(IToolAccessChecker toolChecker,
                                               IPathAccessChecker pathChecker,
                                               IAuditLogger auditLogger,
                                               IApprovalGate approvalGate,
                                               ISecurityLevelResolver securityLevelResolver,
                                               IPermissionMatrix permissionMatrix,
                                               IDenialLedger denialLedger,
                                               IPostDenialGuard postDenialGuard) {
        // --- Always-checked: components with secure defaults ---
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
        if (approvalGate instanceof AutoApproveGate) {
            LOG.warn("DefaultAgentEngine wired with AutoApproveGate: "
                    + "ALL operations including RESTRICTED are unconditionally auto-approved — "
                    + "the defense-in-depth RESTRICTED deny provided by the default "
                    + "DefaultApprovalGate is bypassed. This is an insecure downgrade of the "
                    + "Layer 3 approval gate. To restore secure-by-default behaviour, do not pass "
                    + "an AutoApproveGate to setApprovalGate — the default already uses "
                    + "DefaultApprovalGate (denies RESTRICTED, approves STANDARD/ELEVATED).");
        }

        // --- Layer 2/3 NoOp/PassThrough: always-checked (plan 200 migrated to Default* defaults) ---
        if (securityLevelResolver instanceof NoOpSecurityLevelResolver) {
            LOG.warn("DefaultAgentEngine wired with NoOpSecurityLevelResolver: "
                    + "all operations resolve to STANDARD — no security-level classification is "
                    + "performed. RESTRICTED/ELEVATED levels are never produced, so the approval "
                    + "gate's defense-in-depth deny and the permission matrix's level checks are "
                    + "ineffective. To restore secure-by-default behaviour, do not pass a "
                    + "NoOpSecurityLevelResolver to setSecurityLevelResolver — the default already "
                    + "uses DefaultSecurityLevelResolver.");
        }
        if (permissionMatrix instanceof PassThroughPermissionMatrix) {
            LOG.warn("DefaultAgentEngine wired with PassThroughPermissionMatrix: "
                    + "all channels allow all security levels — no channel-based permission "
                    + "restrictions are enforced. To restore secure-by-default behaviour, do not "
                    + "pass a PassThroughPermissionMatrix to setPermissionMatrix — the default "
                    + "already uses DefaultPermissionMatrix.");
        }
        if (denialLedger instanceof NoOpDenialLedger) {
            LOG.warn("DefaultAgentEngine wired with NoOpDenialLedger: "
                    + "denials are not counted and no sessions are paused on threshold — "
                    + "repeated security denials do not trigger autonomous-execution pause. "
                    + "To restore secure-by-default behaviour, do not pass a NoOpDenialLedger "
                    + "to setDenialLedger — the default already uses DefaultDenialLedger.");
        }
        if (postDenialGuard instanceof PassThroughPostDenialGuard) {
            LOG.warn("DefaultAgentEngine wired with PassThroughPostDenialGuard: "
                    + "blind retries of denied actions are not detected or blocked — "
                    + "the agent can repeatedly attempt the same denied operation. "
                    + "To restore secure-by-default behaviour, do not pass a "
                    + "PassThroughPostDenialGuard to setPostDenialGuard — the default already "
                    + "uses DefaultPostDenialGuard.");
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
     *
     * <p>Plan 224 (L4-8-call-agent-async): when a functional (non-NoOp)
     * messenger is registered, this setter additionally registers an engine-level
     * call-agent request handler on {@link AgentMessageTopics#callAgentTopic()}.
     * The registration is idempotent: any previously-registered call-agent
     * subscription is cancelled before the new handler is registered, so
     * repeated {@code setMessenger} calls never double-register. When the
     * resolved messenger is NoOp (including {@code setMessenger(null)}), no
     * handler is registered and any existing subscription is cancelled.
     */
    public void setMessenger(IAgentMessenger messenger) {
        IAgentMessenger resolved = messenger != null ? messenger : NoOpAgentMessenger.noOp();
        this.messenger = resolved;
        registerCallAgentHandler(resolved);
    }

    /**
     * Idempotently register (or unregister) the engine-level call-agent request
     * handler. Cancels any existing {@code callAgentSubscription} first, then
     * registers a fresh handler when {@code messenger} is functional (not a
     * {@link NoOpAgentMessenger}). NoOp messenger → no registration (zero
     * regression for the shipped default).
     */
    private void registerCallAgentHandler(IAgentMessenger messenger) {
        IMessageSubscription existing = this.callAgentSubscription;
        if (existing != null) {
            try {
                existing.cancel();
            } catch (Exception e) {
                LOG.warn("DefaultAgentEngine: failed to cancel existing call-agent subscription", e);
            }
            this.callAgentSubscription = null;
        }
        if (messenger instanceof NoOpAgentMessenger) {
            return;
        }
        String topic = AgentMessageTopics.callAgentTopic();
        IMessageSubscription subscription = messenger.registerHandler(topic, this::handleCallAgentRequest);
        this.callAgentSubscription = subscription;
        LOG.debug("DefaultAgentEngine: registered call-agent request handler on topic={}", topic);
    }

    /**
     * Engine-level call-agent request handler (plan 224). Receives a
     * {@link CallAgentRequestPayload} carried in a REQUEST envelope, executes
     * the requested sub-agent via {@link #execute(AgentMessageRequest)} with the
     * payload's timeout, and returns a {@link CallAgentResponsePayload}. The
     * messenger adapter routes the non-null return value as a RESPONSE to the
     * requester's reply topic.
     *
     * <p><b>Fail-safe</b>: the entire {@code engine.execute().orTimeout().join()}
     * chain is wrapped in try/catch. Any exception (sub-agent failure,
     * {@code CompletionException(TimeoutException)} on timeout, fork/execution
     * error) is caught and returned as a {@code RESPONSE(status="failure")}
     * rather than propagating to the messenger dispatch layer —
     * {@code LocalAgentMessenger} swallows handler exceptions and would leave
     * the requester hanging until its own timeout otherwise.
     */
    private Object handleCallAgentRequest(AgentMessageEnvelope envelope) {
        Object payload = envelope.getPayload();
        if (!(payload instanceof CallAgentRequestPayload)) {
            LOG.warn("nop.ai.agent.call-agent.handler.unexpected-payload: payloadClass={}",
                    payload == null ? "null" : payload.getClass().getName());
            return new CallAgentResponsePayload(
                    "failure", null, "",
                    "call-agent handler: unexpected payload type: "
                            + (payload == null ? "null" : payload.getClass().getName()));
        }
        CallAgentRequestPayload req = (CallAgentRequestPayload) payload;
        // Plan 271 (finding 14-01): resolve the child session id upfront so
        // that on timeout the handler can call cancelSession to release the
        // sub-agent's LLM/DB resources (not just abandon the Future). When
        // req.getResolvedSessionId() is null (create-new mode), generate a
        // UUID — engine.execute would generate one anyway via resolveSessionId,
        // so this is behavior-preserving.
        String childSessionId = req.getResolvedSessionId();
        if (childSessionId == null || childSessionId.isEmpty()) {
            childSessionId = UUID.randomUUID().toString();
        }
        try {
            AgentMessageRequest execRequest = new AgentMessageRequest(
                    req.getTargetAgentId(),
                    req.getInput(),
                    childSessionId,
                    req.getParentConstraintMetadata());
            CompletableFuture<AgentExecutionResult> future = this.execute(execRequest);
            AgentExecutionResult result = future.orTimeout(req.getTimeoutMs(), TimeUnit.MILLISECONDS).join();
            String finalMessage = extractFinalAssistantMessage(result);
            String sessionId = result.getSessionId() != null
                    ? result.getSessionId()
                    : childSessionId;
            boolean success = result.getStatus() == AgentExecStatus.completed;
            String status = success ? "success" : "failure";
            String error = success ? null
                    : (result.getError() != null ? result.getError()
                            : "sub-agent did not complete: status=" + result.getStatus());
            return new CallAgentResponsePayload(status, sessionId, finalMessage, error);
        } catch (Exception e) {
            // Plan 271 (finding 14-01): on timeout, cancel the sub-agent so its
            // execution does not continue as a zombie consuming LLM/DB
            // resources. .orTimeout above completes the Future exceptionally
            // with a TimeoutException (wrapped in CompletionException by join);
            // without this cancel the underlying engine.execute Future keeps
            // running. cancelSession failures are logged but never mask the
            // original timeout error.
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof java.util.concurrent.TimeoutException) {
                try {
                    this.cancelSession(childSessionId,
                            "call-agent handler timeout after " + req.getTimeoutMs() + "ms", true);
                } catch (RuntimeException cancelEx) {
                    LOG.warn("nop.ai.agent.call-agent.handler.cancel-failed: childSessionId={}",
                            childSessionId, cancelEx);
                }
            }
            LOG.warn("nop.ai.agent.call-agent.handler.execution-failed: targetAgentId={}, correlationId={}",
                    req.getTargetAgentId(), envelope.getCorrelationId(), e);
            return new CallAgentResponsePayload(
                    "failure", childSessionId, "",
                    "call-agent handler execution failed: agentId=" + req.getTargetAgentId()
                            + ", error=" + e);
        }
    }

    /**
     * Extract the last assistant message content from an execution result's
     * message history. Mirrors {@code CallAgentExecutor.extractFinalMessage}
     * (kept here because the engine does not depend on the {@code tool}
     * package). Returns an empty string when there is no assistant message.
     */
    private static String extractFinalAssistantMessage(AgentExecutionResult result) {
        List<ChatMessage> messages = result.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatAssistantMessage) {
                String content = msg.getContent();
                return content != null ? content : "";
            }
        }
        return "";
    }

    /**
     * Return the {@link IAgentMessenger} wired into this engine, or the
     * {@link NoOpAgentMessenger} default if none was explicitly set.
     */
    public IAgentMessenger getMessenger() {
        return messenger;
    }

    /**
     * Plan 216 (L4-5): wire an optional per-session deferred-ack mailbox
     * factory. When non-null, the engine creates a mailbox for each session on
     * first execution (via {@code factory.apply(sessionId)}) and registers a
     * {@link MailboxMessageHandler} on the session's inbox topic
     * ({@code agent.{sessionId}.inbox}) so ASYNC messages delivered via the
     * messenger are buffered in the mailbox for deferred-ack consumption. The
     * mailbox is reused across re-executions of the same session (per-session
     * dedup via {@code computeIfAbsent}).
     *
     * <p>Optional: when null (the shipped default), no mailbox is created and
     * engine behaviour is unchanged (messages go through the existing
     * synchronous handler path, zero regression). Mailbox buffering is not a
     * security component, so no insecure-default WARN is emitted.
     */
    public void setMailboxFactory(Function<String, IMailbox> mailboxFactory) {
        this.mailboxFactory = mailboxFactory;
    }

    /**
     * Return the per-session mailbox factory wired into this engine, or
     * {@code null} if none was set (the shipped default).
     */
    public Function<String, IMailbox> getMailboxFactory() {
        return mailboxFactory;
    }

    /**
     * Return the mailbox created for the given session, or {@code null} if no
     * mailbox has been created for it (no factory wired, or the session has
     * not yet executed). Test/inspection accessor.
     */
    public IMailbox getSessionMailbox(String sessionId) {
        return sessionMailboxes.get(sessionId);
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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, null, this.permissionMatrix, null, null);
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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, this.securityLevelResolver, null, null, null);
    }

    /**
     * Return the {@link ISecurityLevelResolver} wired into this engine, or the
     * {@link DefaultSecurityLevelResolver} default if none was explicitly set.
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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, null, null, null, null);
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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, null, null, this.denialLedger, null);
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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, null, null, null, this.postDenialGuard);
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
        warnIfInsecureDefaults(this.toolAccessChecker, this.pathAccessChecker, this.auditLogger,
                this.approvalGate, null, null, null, null);
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
     * {@link NoOpUsageRecorder} (usage data discarded — pass-through). When
     * explicitly set to {@code null} the recorder falls back to
     * {@link NoOpUsageRecorder} so the accumulation point always has a non-null
     * sink.
     */
    public void setUsageRecorder(IUsageRecorder usageRecorder) {
        this.usageRecorder = usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp();
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
        this.retryPolicy = retryPolicy != null ? retryPolicy : NoRetryPolicy.noRetry();
    }

    public IRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Plan 210 (L3-1): wire a functional {@link ICircuitBreaker} consulted at
     * the ReAct loop's single-LLM-call retry loop outer layer (design
     * {@code nop-ai-agent-reliability.md} §3.3 / §5.1). Before entering the
     * retry loop the breaker is asked whether the primary model may be called;
     * per-attempt failures and the eventual success are recorded back. A
     * functional breaker (e.g. ThresholdBreaker) trips on consecutive failures.
     * Optional: when null, falls back to {@link AlwaysClosed} (unconditional
     * allow + explicit no-op recording — backward compatible, zero-regression)
     * so the ReAct loop always has a non-null breaker. Circuit-breaking is not
     * a security component, so no insecure-default WARN is emitted.
     */
    public void setCircuitBreaker(ICircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : AlwaysClosed.alwaysClosed();
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

    /**
     * Plan 221 (L4-8-P4): the unique identity of this engine instance —
     * used as the {@code ownerId} argument to
     * {@link ISessionTakeoverLock#tryAcquire} /
     * {@link ISessionTakeoverLock#release} /
     * {@link ISessionTakeoverLock#tryRenew}. Generated once at
     * construction via {@code UUID.randomUUID()} and immutable afterwards,
     * so conditional release only ever frees this engine's own locks.
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Plan 221 (L4-8-P4): fault-tolerant lock release — wraps
     * {@link ISessionTakeoverLock#release} in try-catch. A release failure
     * (e.g. DB connection lost) is logged at WARN and swallowed so that
     * session-state persist / handle cleanup is never blocked by a
     * transient lock-store failure. The lease/TTL guarantees the stale
     * lock auto-expires, so a single failed release is bounded.
     *
     * <p>Under the shipped {@link NoOpSessionTakeoverLock} default this is
     * a no-op (release returns true unconditionally), so the call adds no
     * overhead in single-process deployments.
     */
    private void releaseLockQuietly(String sessionId, String ownerId) {
        try {
            sessionTakeoverLock.release(sessionId, ownerId);
        } catch (RuntimeException e) {
            LOG.warn("DefaultAgentEngine: failed to release takeover lock for sessionId={}, "
                    + "ownerId={} (the lease will auto-expire via TTL): {}", sessionId, ownerId,
                    e.toString());
        }
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
     * Plan 271 (finding 14-04): lazily initialize and return the dedicated
     * agent executor. The first call creates a virtual-thread-per-task
     * executor (the shipped default); a subsequent {@link #setAgentExecutor}
     * overrides it. The three engine entry points (doExecute / resumeSession /
     * restoreSession) and the ReAct LLM-call timeout wrapper all consume this
     * executor so the engine no longer shares {@code ForkJoinPool.commonPool()}
     * and concurrent agents do not starve each other.
     */
    synchronized ExecutorService getAgentExecutor() {
        if (agentExecutor == null) {
            // Plan 271 (finding 14-04): dedicated cached thread pool with
            // daemon threads replaces ForkJoinPool.commonPool() (default ~3-7
            // threads) so concurrent agent executions do not starve each other.
            // Cached pools grow on demand and reuse idle threads, providing
            // effectively-unbounded concurrency for blocking agent work (each
            // agent blocks on LLM/DB calls). Virtual threads (Java 21) would be
            // ideal but the module targets Java 11. Integrators may override via
            // setAgentExecutor (e.g. a fixed-size pool for resource-constrained
            // deployments — note a fixed pool risks self-deadlock if the ReAct
            // LLM-call timeout wrapper dispatches back to the same saturated
            // pool, so a cached/virtual-thread executor is recommended).
            agentExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "nop-ai-agent-exec");
                t.setDaemon(true);
                return t;
            });
        }
        return agentExecutor;
    }

    /**
     * Plan 271 (finding 14-04): override the dedicated agent executor (e.g.
     * with a fixed-size pool for resource-constrained deployments, or a
     * direct/synchronous executor for tests). The supplied executor is used
     * as-is; the caller owns its lifecycle. Must be non-null.
     */
    public void setAgentExecutor(ExecutorService agentExecutor) {
        this.agentExecutor = Objects.requireNonNull(agentExecutor, "agentExecutor must not be null");
    }

    /**
     * Plan 271 (finding 14-01): wall-clock timeout (ms) for a call-agent
     * sub-agent execution. Must be positive.
     */
    public void setCallAgentTimeoutMs(long callAgentTimeoutMs) {
        if (callAgentTimeoutMs <= 0) {
            throw new NopAiAgentException("callAgentTimeoutMs must be positive, got: " + callAgentTimeoutMs);
        }
        this.callAgentTimeoutMs = callAgentTimeoutMs;
    }

    public long getCallAgentTimeoutMs() {
        return callAgentTimeoutMs;
    }

    /**
     * Plan 271 (finding 14-03): wall-clock timeout (ms) for a single LLM call
     * inside the ReAct loop. A value {@code <= 0} disables the timeout
     * (backward-compatible escape hatch).
     */
    public void setLlmTimeoutMs(long llmTimeoutMs) {
        this.llmTimeoutMs = llmTimeoutMs;
    }

    public long getLlmTimeoutMs() {
        return llmTimeoutMs;
    }

    /**
     * Plan 271 (finding 14-03): wall-clock timeout (ms) for a single tool call
     * inside the ReAct dispatch fanout. A value {@code <= 0} disables the
     * timeout (backward-compatible escape hatch).
     */
    public void setToolTimeoutMs(long toolTimeoutMs) {
        this.toolTimeoutMs = toolTimeoutMs;
    }

    public long getToolTimeoutMs() {
        return toolTimeoutMs;
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
        // Plan 232: cancelSession is synchronous and touches the session store.
        // It has no Principal source in the foundational slice, so the tenant
        // context is null = all data visible. The set/clear structure is
        // present for uniformity (a future principal source only changes the
        // captured value).
        ThreadLocalTenantResolver.set(null);
        try {
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
        } finally {
            ThreadLocalTenantResolver.clear();
        }
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
        // Plan 232: forkSession is synchronous but touches the session store,
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
        } finally {
            ThreadLocalTenantResolver.clear();
        }
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

    /**
     * Plan 232 (L4-multi-tenant-isolation): null-safe extraction of the
     * tenantId from a request's {@link io.nop.ai.agent.security.Principal}.
     * Returns {@code null} when the request, principal, or tenantId is absent
     * — the explicit "no tenant context" signal (all data visible, backward
     * compatible).
     */
    private static String resolveTenantId(AgentMessageRequest request) {
        if (request == null || request.getPrincipal() == null) {
            return null;
        }
        return request.getPrincipal().getTenantId();
    }

    private CompletableFuture<AgentExecutionResult> doExecute(AgentMessageRequest request, String sessionId) {
        // Plan 232: capture the tenantId in the synchronous phase (null-safe)
        // so the supplyAsync lambda body can set the thread-local tenant
        // context on the worker thread before any DB store operation.
        String tenantId = resolveTenantId(request);
        AgentModel agentModel = loadAgentModel(request.getAgentName());
        // Plan 231: synchronous fail-fast precheck — if the agent declares a
        // team but no functional ITeamManager is wired, surface the
        // misconfiguration before entering the async block.
        precheckTeamDeclarations(agentModel);
        AgentSession session = sessionStore.getOrCreate(sessionId, request.getAgentName());
        // Plan 270 finding 13-12: capture the tenantId onto the session so
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
        ensureSessionMailbox(sessionId);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // Plan 197 (AUDIT-14-01): Pre-register the CancelHandle in the
        // synchronous phase (before supplyAsync) so that cancelSession can
        // find it during the async-enqueue window (after execute() returns
        // but before the supplyAsync lambda starts running). putIfAbsent is
        // the atomic dedup guard — a non-null return means another execution
        // is already registered for this session, so we fail-fast instead of
        // silently overwriting the existing handle.
        //
        // Plan 221 (L4-8-P4): cross-process takeover lock. tryAcquire is
        // called BEFORE putIfAbsent — if another JVM instance is already
        // restoring/executing this session, fail-fast (裁定 4 路径 a/b).
        // tryAcquire + putIfAbsent are wrapped together so the catch path
        // can release the lock when putIfAbsent fails (裁定 5 路径 1).
        CancelHandle handle = new CancelHandle(ctx, null);
        try {
            if (!sessionTakeoverLock.tryAcquire(sessionId, instanceId, lockLeaseMs)) {
                throw new NopAiAgentException(
                        "doExecute failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                throw new NopAiAgentException(
                        "doExecute failed: session already executing: sessionId=" + sessionId);
            }
        } catch (RuntimeException e) {
            releaseLockQuietly(sessionId, instanceId);
            throw e;
        }

        try {
            // Plan 271 (finding 14-04): use the dedicated agent executor instead
            // of ForkJoinPool.commonPool() so concurrent agents do not starve
            // each other (commonPool defaults to ~3-7 threads JVM-wide).
            return CompletableFuture.supplyAsync(() -> {
                // Plan 232 (L4-multi-tenant-isolation): set the thread-local
                // tenant context on the worker thread BEFORE any DB store
                // operation. Standard ThreadLocal does not cross the
                // supplyAsync boundary, so the capture from the synchronous
                // phase must be re-applied here. Cleared in the finally below
                // so the pooled worker thread does not leak tenant context.
                ThreadLocalTenantResolver.set(tenantId);
                try {
                session.setStatus(AgentExecStatus.running);

                // Plan 197: bind the execution thread now that the lambda is
                // running. cancelSession(forced=true) reads this volatile field.
                handle.thread = Thread.currentThread();

                // Plan 218 (L4-8): opt-in Actor registration. The engine gates
                // on isEnabled() (NoOp default returns false → skipped, no
                // exception-based control flow). When enabled, createActor
                // registers an AgentActor that runs a mailbox consumption loop
                // on a dedicated thread. The Actor is a container/observer,
                // not a replacement for the ReAct executor.
                // Plan 220 (L4-8-steering): bind the ctx steering queue to the
                // Actor immediately after createActor returns and before
                // execute(ctx), so the consumption loop can inject polled
                // messages into the ReAct reasoning context.
                if (actorRuntime.isEnabled()) {
                    AgentActor actor = actorRuntime.createActor(sessionId, request.getAgentName());
                    actor.setSteeringQueue(ctx.getSteeringQueue());
                }

                // Plan 231: declarative team auto-bind (lead and/or member).
                // Runs after createActor so the actorId is available.
                autoBindTeam(agentModel, sessionId, request.getAgentName());

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
                    // Plan 214 (L2-13a): release this session's write intents
                    // so finished sessions do not block future sessions from
                    // writing the same files. Safe to call on every exit path
                    // (release of an unknown/empty session is a no-op).
                    writeIntentRegistry.releaseSession(sessionId);
                    // Plan 218 (L4-8): destroy the Actor registered at lambda
                    // entry. The actorId is reverse-looked-up via sessionId
                    // (no CancelHandle or AgentExecutionContext modification).
                    if (actorRuntime.isEnabled()) {
                        actorRuntime.getActorBySession(sessionId)
                                .ifPresent(a -> actorRuntime.destroyActor(a.getActorId()));
                    }
                    // Plan 221 (L4-8-P4): release the takeover lock (裁定 5
                    // 路径 3 — inner finally). Fault-tolerant: a failed
                    // release only LOG.warn (the lease auto-expires via TTL).
                    releaseLockQuietly(sessionId, instanceId);
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
                } finally {
                    // Plan 232: clear the worker-thread tenant context so the
                    // pooled thread does not leak tenant state to the next task.
                    ThreadLocalTenantResolver.clear();
                }
        }, getAgentExecutor());
        } catch (RuntimeException e) {
            // Plan 197: if supplyAsync itself fails to submit the task
            // (e.g. RejectedExecutionException), clean up the pre-registered
            // handle so a subsequent execute() is not permanently blocked.
            runningExecutions.remove(sessionId, handle);
            // Plan 221 (L4-8-P4): release the takeover lock (裁定 5 路径 2
            // — outer catch / supplyAsync submission failure). Same
            // fault-tolerant releaseLockQuietly as the inner finally.
            releaseLockQuietly(sessionId, instanceId);
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

        // Plan 270 finding 13-12: resumeSession has no request/Principal
        // source, so the tenant context must be re-established from the
        // persisted session. Without this the ledger's reset/clear SQL would
        // run with tenant=null and DELETE every tenant's denial rows for this
        // sessionId (cross-tenant data destruction). Capture the session's
        // tenantId and scope the count/reset to it, restoring the caller's
        // context afterward so the synchronous phase never leaks tenant state.
        String sessionTenantId = session.getTenantId();
        String previousTenant = ThreadLocalTenantResolver.current();
        ThreadLocalTenantResolver.set(sessionTenantId);
        try {
            // Capture the pre-reset denial count for the audit event before clearing.
            int preResetDenialCount = denialLedger.getDenialCount(sessionId);

            // Clear the pause by resetting the ledger (design §6.2 sticky
            // recovery). With the tenant context now set, the ledger's reset
            // SQL includes the tenant WHERE — only this tenant's denials are
            // cleared.
            denialLedger.reset(sessionId);

            // Transition the session back to running before re-execution.
            session.setStatus(AgentExecStatus.running);

            Map<String, Object> resumePayload = new HashMap<>();
            resumePayload.put("approver", approver != null ? approver : "");
            resumePayload.put("reason", reason != null ? reason : "");
            resumePayload.put("preResetDenialCount", preResetDenialCount);
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESUMED,
                    sessionId, agentName, resumePayload));
        } finally {
            ThreadLocalTenantResolver.set(previousTenant);
        }

        // Re-execute the session as a transparent continuation: rebuild the
        // context from the agent model + the existing conversation history (NO
        // new user message is appended — resume continues where the paused
        // execution left off, letting the LLM re-plan from the last denied
        // tool-call error response rather than starting a new turn).
        AgentModel agentModel = loadAgentModel(agentName);
        // Plan 231: synchronous fail-fast precheck (see doExecute).
        precheckTeamDeclarations(agentModel);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        // Resolve the executor with the engine's own checkers (no parent
        // constraint applies on resume — resume is a top-level recovery action,
        // not a sub-agent call). Per-agent path rules are still honoured so the
        // resumed execution is consistent with a normal top-level execution.
        IToolAccessChecker effectiveToolAccessChecker = this.toolAccessChecker;
        IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
        ensureSessionMailbox(sessionId);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // Plan 197 (AUDIT-14-01): Pre-register CancelHandle in the synchronous
        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        //
        // Plan 221 (L4-8-P4): cross-process takeover lock (see doExecute for
        // full rationale — tryAcquire before putIfAbsent, release on every
        // cleanup path).
        CancelHandle handle = new CancelHandle(ctx, null);
        try {
            if (!sessionTakeoverLock.tryAcquire(sessionId, instanceId, lockLeaseMs)) {
                throw new NopAiAgentException(
                        "resumeSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                throw new NopAiAgentException(
                        "resumeSession failed: session already executing: sessionId=" + sessionId);
            }
        } catch (RuntimeException e) {
            releaseLockQuietly(sessionId, instanceId);
            throw e;
        }

        try {
            // Plan 271 (finding 14-04): use the dedicated agent executor (see doExecute).
            return CompletableFuture.supplyAsync(() -> {
                // Plan 232 + Plan 270 finding 13-12: set/clear tenant context
                // on the worker thread. resumeSession has no request/Principal
                // source, so the tenant context is re-established from the
                // persisted session (captured above as sessionTenantId) — NOT
                // forced to null, which would make any tenant-scoped DB
                // operation on this thread see all tenants' data.
                ThreadLocalTenantResolver.set(sessionTenantId);
                try {
                handle.thread = Thread.currentThread();

                // Plan 218 (L4-8): opt-in Actor registration (see doExecute).
                // Plan 220 (L4-8-steering): bind the ctx steering queue to the
                // Actor (see doExecute).
                if (actorRuntime.isEnabled()) {
                    AgentActor actor = actorRuntime.createActor(sessionId, agentName);
                    actor.setSteeringQueue(ctx.getSteeringQueue());
                }

                // Plan 231: declarative team auto-bind (lead and/or member).
                // Runs after createActor so the actorId is available.
                autoBindTeam(agentModel, sessionId, agentName);

                AgentExecutionResult result;
                try {
                    result = executor.execute(ctx).toCompletableFuture().join();
                } finally {
                    // Plan 197: value-comparison remove — only remove our own handle.
                    runningExecutions.remove(sessionId, handle);
                    session.setStatus(ctx.getStatus());
                    // Plan 214 (L2-13a): release this session's write intents
                    // (mirrors doExecute / restoreSession finally cleanup).
                    writeIntentRegistry.releaseSession(sessionId);
                    // Plan 218 (L4-8): destroy the Actor (see doExecute).
                    if (actorRuntime.isEnabled()) {
                        actorRuntime.getActorBySession(sessionId)
                                .ifPresent(a -> actorRuntime.destroyActor(a.getActorId()));
                    }
                    // Plan 221 (L4-8-P4): release the takeover lock (裁定 5
                    // 路径 3 — inner finally).
                    releaseLockQuietly(sessionId, instanceId);
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
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
        }, getAgentExecutor());
        } catch (RuntimeException e) {
            // Plan 197: clean up pre-registered handle if supplyAsync fails.
            runningExecutions.remove(sessionId, handle);
            // Plan 221 (L4-8-P4): release the takeover lock (裁定 5 路径 2).
            releaseLockQuietly(sessionId, instanceId);
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
        // Plan 231: synchronous fail-fast precheck (see doExecute).
        precheckTeamDeclarations(agentModel);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        IToolAccessChecker effectiveToolAccessChecker = this.toolAccessChecker;
        IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
        ensureSessionMailbox(sessionId);
        IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // Plan 197 (AUDIT-14-01): Pre-register CancelHandle in the synchronous
        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        //
        // Plan 221 (L4-8-P4): cross-process takeover lock (see doExecute for
        // full rationale — tryAcquire before putIfAbsent, release on every
        // cleanup path).
        CancelHandle handle = new CancelHandle(ctx, null);
        try {
            if (!sessionTakeoverLock.tryAcquire(sessionId, instanceId, lockLeaseMs)) {
                throw new NopAiAgentException(
                        "restoreSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                throw new NopAiAgentException(
                        "restoreSession failed: session already executing: sessionId=" + sessionId);
            }
        } catch (RuntimeException e) {
            releaseLockQuietly(sessionId, instanceId);
            throw e;
        }

        try {
            // Plan 271 (finding 14-04): use the dedicated agent executor (see doExecute).
            return CompletableFuture.supplyAsync(() -> {
                // Plan 232: set/clear tenant context on the worker thread.
                // restoreSession has no Principal source in the foundational
                // slice (no request parameter), so the tenant context is null
                // = all data visible (recovery-path semantics).
                ThreadLocalTenantResolver.set(null);
                try {
                handle.thread = Thread.currentThread();

                // Plan 218 (L4-8): opt-in Actor registration (see doExecute).
                // Plan 220 (L4-8-steering): bind the ctx steering queue to the
                // Actor (see doExecute).
                if (actorRuntime.isEnabled()) {
                    AgentActor actor = actorRuntime.createActor(sessionId, agentName);
                    actor.setSteeringQueue(ctx.getSteeringQueue());
                }

                // Plan 231: declarative team auto-bind (lead and/or member).
                // Runs after createActor so the actorId is available.
                autoBindTeam(agentModel, sessionId, agentName);

                AgentExecutionResult result;
                try {
                    result = executor.execute(ctx).toCompletableFuture().join();
                } finally {
                    // Plan 197: value-comparison remove — only remove our own handle.
                    runningExecutions.remove(sessionId, handle);
                    session.setStatus(ctx.getStatus());
                    // Plan 214 (L2-13a): release this session's write intents
                    // (mirrors doExecute / resumeSession finally cleanup).
                    writeIntentRegistry.releaseSession(sessionId);
                    // Plan 218 (L4-8): destroy the Actor (see doExecute).
                    if (actorRuntime.isEnabled()) {
                        actorRuntime.getActorBySession(sessionId)
                                .ifPresent(a -> actorRuntime.destroyActor(a.getActorId()));
                    }
                    // Plan 221 (L4-8-P4): release the takeover lock (裁定 5
                    // 路径 3 — inner finally).
                    releaseLockQuietly(sessionId, instanceId);
                }

            // Plan 183 Phase 1: replaceMessages unifies the post-execution
            // sync with the intra-execution persistence path.
            session.replaceMessages(ctx.getMessages());

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();
            sessionStore.save(session);

            return result;
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
        }, getAgentExecutor());
        } catch (RuntimeException e) {
            // Plan 197: clean up pre-registered handle if supplyAsync fails.
            runningExecutions.remove(sessionId, handle);
            // Plan 221 (L4-8-P4): release the takeover lock (裁定 5 路径 2).
            releaseLockQuietly(sessionId, instanceId);
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
                // Plan 221 (L4-8-P4): skip sessions already being processed by
                // another instance. isHeld returns true iff an active (non-
                // expired) lease exists for this sessionId regardless of
                // owner — a true return means another JVM instance is
                // handling this session, so add to skipped (not failed).
                if (sessionTakeoverLock.isHeld(sessionId)) {
                    skipped.add(new SessionRestoreSummary.SkipEntry(
                            sessionId, status, "locked by another instance"));
                    continue;
                }
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
            resolveHookContributions(hookRegistry);
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
                    .usageRecorder(this.usageRecorder)
                    .modelSwitchedMessageWriter(this.modelSwitchedMessageWriter)
                    .budgetProvider(this.budgetProvider)
                    .retryPolicy(this.retryPolicy)
                    .circuitBreaker(this.circuitBreaker)
                    .goalTracker(this.goalTracker)
                    .sustainer(this.sustainer)
                    .conflictStrategy(this.conflictStrategy)
                    .writeIntentRegistry(this.writeIntentRegistry)
                    .contributionRegistry(this.contributionRegistry)
                    .sandboxBackend(this.sandboxBackend)
                    .teamManager(this.teamManager)
                    .teamTaskStore(this.teamTaskStore)
                    .teamAclChecker(this.teamAclChecker)
                    // Plan 271 (finding 14-03 / 14-04): propagate the wall-clock
                    // LLM/tool timeouts and the dedicated executor (used to wrap
                    // the synchronous chatService.call with a timeout) to the
                    // ReAct executor.
                    .llmTimeoutMs(this.llmTimeoutMs)
                    .toolTimeoutMs(this.toolTimeoutMs)
                    .timeoutExecutor(getAgentExecutor())
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

    /**
     * Plan 217 (L4-6): assembly-time HOOK contribution resolution. Iterate
     * every {@link ContributionType#HOOK} contribution in the wired registry
     * (ascending priority order), and register its payload into the given
     * {@code hookRegistry} so the ReAct loop fires the hook at the
     * corresponding {@link io.nop.ai.agent.hook.AgentLifecyclePoint}.
     *
     * <p>Payload contract (plan 217 裁定 5): the payload must be a
     * {@link HookPayload}. A HOOK contribution whose payload is not a
     * {@code HookPayload} is logged at WARN and skipped — fail-visible, not
     * a silent no-op (Minimum Rules #24). A single bad contribution does
     * not abort the rest of the batch — the remaining HOOK contributions
     * are registered normally.
     *
     * <p>This resolution runs after {@code DefaultHookRegistry.fromAgentModel}
     * so static (XDSL-declared) hooks fire first (priority 0 by default) —
     * the registry contributions layer on top. With the shipped
     * {@link NoOpContributionRegistry} default the loop iterates an empty
     * list, so behaviour is unchanged.
     */
    private void resolveHookContributions(io.nop.ai.agent.hook.IHookRegistry hookRegistry) {
        List<Contribution> hookContributions = contributionRegistry.getContributions(ContributionType.HOOK);
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

    /**
     * Plan 216 (L4-5): ensure a per-session deferred-ack mailbox exists and is
     * wired to the session's inbox topic before execution. Idempotent per
     * session: the first call creates the mailbox (via the registered
     * factory) and registers a {@link MailboxMessageHandler} on
     * {@code agent.{sessionId}.inbox}; subsequent calls reuse the existing
     * mailbox (computeIfAbsent dedup — the handler is registered only once).
     *
     * <p>No-op when no factory is wired ({@code mailboxFactory == null}) —
     * zero behaviour regression for the shipped default.
     */
    private void ensureSessionMailbox(String sessionId) {
        if (mailboxFactory == null) {
            return;
        }
        sessionMailboxes.computeIfAbsent(sessionId, sid -> {
            IMailbox mailbox = mailboxFactory.apply(sid);
            if (mailbox == null) {
                LOG.warn("DefaultAgentEngine: mailboxFactory returned null for sessionId={}, "
                        + "no mailbox will be created for this session", sid);
                return null;
            }
            String inboxTopic = AgentMessageTopics.inboxTopic(sid);
            MailboxMessageHandler handler = new MailboxMessageHandler(mailbox);
            IMessageSubscription subscription = messenger.registerHandler(inboxTopic, handler);
            if (subscription != null) {
                sessionMailboxSubscriptions.put(sid, subscription);
            }
            LOG.debug("DefaultAgentEngine: created mailbox for sessionId={}, registered handler on topic={}",
                    sid, inboxTopic);
            return mailbox;
        });
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

    // ============================================================
    // Plan 231 (L4-team-auto-binding): declarative team auto-bind
    // ============================================================
    //
    // A lead agent's <team> element declares the team structure; a member
    // agent's <team-member> element declares its membership. When a
    // functional ITeamManager is wired, the three execution entry points
    // (doExecute / resumeSession / restoreSession) auto-bind the declared
    // team/member without any integrator code. The shipped default
    // (NoOpTeamManager + no <team>/<team-member> declarations) leaves engine
    // behaviour unchanged (zero regression).
    //
    // Two-phase design (Design Decision #7):
    //   1. Synchronous fail-fast precheck (precheckTeamDeclarations): run
    //      right after loadAgentModel, before supplyAsync. Surfaces a
    //      NoOp+declaration misconfiguration early.
    //   2. Async-block binding (autoBindTeam): run inside supplyAsync after
    //      actorRuntime.createActor(), because bindMemberSession needs a
    //      non-null actorId which only becomes available then.

    /**
     * Synchronous fail-fast precheck (Plan 231, Design Decision #6/#7). If
     * the agent declares {@code <team>} or {@code <team-member>} but the
     * wired {@code teamManager} is a {@link NoOpTeamManager} (no functional
     * manager), throw {@link NopAiAgentException} before entering the async
     * execution block. This surfaces the deployment misconfiguration early
     * rather than failing inside the async block or silently skipping the
     * binding (Minimum Rules #24 — No Silent No-Op). When neither
     * declaration is present, this method touches no teamManager state (zero
     * regression).
     */
    private void precheckTeamDeclarations(AgentModel agentModel) {
        boolean hasTeamDecl = agentModel.getTeam() != null;
        boolean hasMemberDecl = agentModel.getTeamMember() != null;
        if ((hasTeamDecl || hasMemberDecl) && teamManager instanceof NoOpTeamManager) {
            throw new NopAiAgentException(
                    "Agent declares <team>/<team-member> but no functional ITeamManager "
                            + "is wired; call setTeamManager(InMemoryTeamManager/DbTeamManager) "
                            + "to enable declarative team binding. agentName=" + agentModel.getName());
        }
    }

    /**
     * Async-block auto-bind (Plan 231). Called from each entry point's
     * {@code supplyAsync} lambda after {@code actorRuntime.createActor()}
     * (the {@code actorId} is only available then). Idempotently binds the
     * lead session ({@code <team>}) and/or member session
     * ({@code <team-member>}). Both paths are opt-in: if neither declaration
     * is present, this method touches no teamManager state (zero regression).
     */
    private void autoBindTeam(AgentModel agentModel, String sessionId, String agentName) {
        TeamModel teamDecl = agentModel.getTeam();
        if (teamDecl != null) {
            autoBindLead(teamDecl, sessionId, agentName);
        }
        TeamMemberRefModel memberDecl = agentModel.getTeamMember();
        if (memberDecl != null) {
            autoBindMember(memberDecl, sessionId);
        }
    }

    /**
     * Lead-side auto-bind (Plan 231, Design Decisions #3/#4). Convert the
     * declared {@code <team>} into a {@link TeamSpec}, idempotently create
     * the team (probe {@code getTeamBySession} first to avoid re-creating on
     * resume/restore), and bind the lead session. The converter guarantees
     * {@code leadAgentName} is in the roster with {@code role=LEAD}, so the
     * first binding transitions the team {@code CREATED → ACTIVE}. A
     * {@code false} return from {@code bindMemberSession} fails fast
     * (Design Decision #8 — No Silent No-Op).
     */
    private void autoBindLead(TeamModel teamDecl, String sessionId, String agentName) {
        String actorId = resolveActorId(sessionId);
        String leadAgentName = teamDecl.getLeadAgentName();

        // Idempotent create: probe the session index first so resume/restore
        // do not re-create the team (createTeam generates a fresh UUID and is
        // not itself idempotent).
        java.util.Optional<Team> existing = teamManager.getTeamBySession(sessionId);
        String teamId;
        if (existing.isPresent()) {
            teamId = existing.get().getTeamId();
        } else {
            TeamSpec spec = TeamModelConverter.toTeamSpec(teamDecl, agentName);
            teamId = teamManager.createTeam(spec).getTeamId();
        }

        boolean bound = teamManager.bindMemberSession(teamId, leadAgentName, sessionId, actorId);
        if (!bound) {
            throw new NopAiAgentException(
                    "Auto-bind failed: lead member '" + leadAgentName
                            + "' could not be bound to team '" + teamId
                            + "' (not in roster or team not in a bindable state). sessionId="
                            + sessionId);
        }
    }

    /**
     * Member-side auto-bind (Plan 231, Design Decisions #5/#8). Resolve the
     * team by {@code teamName} among ACTIVE teams (a CREATED-only team means
     * the lead has not yet bound/activated it — members must not bind to an
     * unactivated team), then idempotently bind the member session. Missing
     * ACTIVE team → fail-fast; {@code bindMemberSession} returning
     * {@code false} → fail-fast (No Silent No-Op).
     */
    private void autoBindMember(TeamMemberRefModel memberDecl, String sessionId) {
        String actorId = resolveActorId(sessionId);
        String teamName = memberDecl.getTeamName();
        String memberName = memberDecl.getMemberName();

        // getActiveTeams() returns CREATED+ACTIVE; an explicit ACTIVE filter
        // is required so members only bind to an activated team.
        Team matched = null;
        int activeCount = 0;
        for (Team team : teamManager.getActiveTeams()) {
            if (teamName.equals(team.getSpec().getTeamName())
                    && team.getStatus() == TeamStatus.ACTIVE) {
                activeCount++;
                if (matched == null) {
                    matched = team;
                }
            }
        }
        if (matched == null) {
            throw new NopAiAgentException(
                    "Auto-bind failed: member declares <team-member teamName='" + teamName
                            + "'> but no ACTIVE team with that name was found "
                            + "(ensure the lead agent has executed and bound/activated the team). "
                            + "sessionId=" + sessionId);
        }
        if (activeCount > 1) {
            LOG.warn("Auto-bind: multiple ACTIVE teams named '{}' found; binding to the first "
                    + "(teamId={}). Cross-process teamName uniqueness arbitration is a successor (Non-Goal).",
                    teamName, matched.getTeamId());
        }

        // Idempotent: skip the bind if the member is already bound.
        java.util.Optional<TeamMember> already = teamManager.getMember(matched.getTeamId(), memberName);
        if (already.isPresent() && already.get().isBound()) {
            return;
        }

        boolean bound = teamManager.bindMemberSession(matched.getTeamId(), memberName, sessionId, actorId);
        if (!bound) {
            throw new NopAiAgentException(
                    "Auto-bind failed: member '" + memberName
                            + "' declares <team-member> but is not in the lead's team roster, "
                            + "or the team is not in a bindable state. teamName=" + teamName
                            + ", sessionId=" + sessionId);
        }
    }

    /**
     * Resolve the {@code actorId} to pass to {@code bindMemberSession}. When
     * a functional actorRuntime is enabled, use the Actor's UUID actorId;
     * otherwise (NoOp shipped default) fall back to the sessionId. The
     * {@code actorId} is an opaque association tag on {@link TeamMember} —
     * team binding/routing semantics depend on {@code sessionId}, not on a
     * live Actor, so the sessionId is a legitimate stand-in when no Actor
     * runtime is configured (Design Decision #7).
     */
    private String resolveActorId(String sessionId) {
        if (actorRuntime.isEnabled()) {
            java.util.Optional<AgentActor> actor = actorRuntime.getActorBySession(sessionId);
            if (actor.isPresent()) {
                return actor.get().getActorId();
            }
        }
        return sessionId;
    }
}
