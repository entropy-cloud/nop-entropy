/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_JOB_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_ALREADY_HOSTED;
import io.nop.api.core.time.CoreMetrics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.message.IMessageService;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.util.NopStreamThreadFactory;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointHistoryEntry;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.ledger.LedgerRetentionConfig;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-5/11): multi-job ops manager hosted in the coordinator
 * process. Submit builds an in-process {@link JobCoordinator} through the
 * SAME wiring as the standalone launch path (factory artifacts → durable
 * restore id-advance → remote-deploy mode → distributed commit forwarder +
 * abort handler → start → assignTasks → deployTask), plus the item 16
 * governance sweep (bounded checkpoint history + terminal job records).
 */
public class OpsJobManager implements IOpsJobRegistry, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OpsJobManager.class);

    private final IMessageService messageService;
    private final ClusterRegistry clusterRegistry;
    private final LocalFileCheckpointStorage checkpointStorage;
    private final Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> rpcTargets;
    private final StreamGovernanceConfig governanceConfig;
    /** Item 16 (P-REQ-12): alert service registered on every submitted job. */
    private final io.nop.stream.runtime.alert.AlertService alertService;

    private final Map<String, JobCoordinator> jobs = new ConcurrentHashMap<>();
    private final Map<String, Long> terminalAt = new ConcurrentHashMap<>();

    /**
     * ST-6 裁定（2026-09-13，审计 plan 357）：保留引擎自建调度线程——流引擎是自包含运行时
     * （可独立于 nop-job 部署），ops 周期任务（治理清扫/指标上报）随引擎生命周期；宿主集成
     * nop-job 时可外置触发（successor 候选）。
     */
    private ScheduledExecutorService governanceSweeper;

    /**
     * C1-006 (plan 2510 adjudication 7, R01 member row): assignment-ledger
     * retention parameters (face=assignment, keys {@code nop.stream.ledger.assignment.*},
     * SSOT defaults). Retention-days=0 disables the periodic JDBC cleanup.
     */
    private LedgerRetentionConfig assignmentRetentionConfig = LedgerRetentionConfig.assignmentFace();

    /** Rule-23 evidence for the form-A cleanup schedule wiring. */
    private volatile boolean assignmentRetentionScheduled;

    public OpsJobManager(IMessageService messageService,
                         ClusterRegistry clusterRegistry,
                         LocalFileCheckpointStorage checkpointStorage,
                         Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> taskRpcServices) {
        this(messageService, clusterRegistry, checkpointStorage, taskRpcServices,
                new StreamGovernanceConfig());
    }

    public OpsJobManager(IMessageService messageService,
                         ClusterRegistry clusterRegistry,
                         LocalFileCheckpointStorage checkpointStorage,
                         Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> taskRpcServices,
                         StreamGovernanceConfig governanceConfig) {
        this(messageService, clusterRegistry, checkpointStorage, taskRpcServices,
                governanceConfig, defaultAlertService());
    }

    /**
     * Item 16 (P-REQ-12): full wiring — the alert service (routing JOB_FAILED
     * / RECOVERY_STARTED / JOB_DEGRADED to the configured channels) is
     * registered on every submitted job so fault events observed by this
     * process are delivered out-of-band.
     */
    public OpsJobManager(IMessageService messageService,
                         ClusterRegistry clusterRegistry,
                         LocalFileCheckpointStorage checkpointStorage,
                         Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> taskRpcServices,
                         StreamGovernanceConfig governanceConfig,
                         io.nop.stream.runtime.alert.AlertService alertService) {
        this.messageService = messageService;
        this.clusterRegistry = clusterRegistry;
        this.checkpointStorage = checkpointStorage;
        this.rpcTargets = new LinkedHashMap<>(taskRpcServices);
        this.governanceConfig = governanceConfig;
        this.alertService = alertService != null ? alertService : defaultAlertService();
    }

    private static io.nop.stream.runtime.alert.AlertService defaultAlertService() {
        return new io.nop.stream.runtime.alert.AlertService(
                java.util.List.of(new io.nop.stream.runtime.alert.LoggingAlertChannel()));
    }

    // ==================== submit / stop (P-REQ-5) ====================

    /**
     * Submits a job: builds and starts an in-process coordinator through the
     * standard launch wiring. Fail-fast on duplicate jobId, unknown factory
     * class, or factory construction failure — never falls back to a trivial
     * pipeline.
     */
    public synchronized JobCoordinator submit(JobSubmissionSpec spec) {
        String jobId = spec.getJobId();
        if (jobId == null || jobId.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "jobId is required for job submission");
        }
        if (jobs.containsKey(jobId)) {
            throw new StreamException(ERR_STREAM_JOB_ALREADY_HOSTED).param(ARG_JOB_ID, jobId);
        }
        if (spec.getPipelineFactoryClass() == null || spec.getPipelineFactoryClass().isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "pipelineFactoryClass is required for job submission");
        }

        ClusterPipelineFactory.PipelineArtifacts artifacts = buildArtifacts(spec, jobId);

        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig checkpointConfig = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(artifacts.getCheckpointIntervalMs() != null
                        ? artifacts.getCheckpointIntervalMs() : 1000L)
                .checkpointTimeout(artifacts.getCheckpointTimeoutMs() != null
                        && artifacts.getCheckpointTimeoutMs() > 0
                        ? artifacts.getCheckpointTimeoutMs() : 10_000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(artifacts.getMaxRetainedCheckpoints() != null
                        && artifacts.getMaxRetainedCheckpoints() > 0
                        ? artifacts.getMaxRetainedCheckpoints() : 3)
                .build();
        CheckpointCoordinator checkpointCoordinator = new CheckpointCoordinator(
                jobId, "pipeline-0", idCounter, checkpointStorage, checkpointConfig);
        checkpointCoordinator.setCheckpointHistoryMaxEntries(
                governanceConfig.getCheckpointHistoryMaxEntries());

        if (artifacts.getJobGraph().getStreamModel() != null) {
            checkpointCoordinator.setCurrentFingerprint(
                    artifacts.getJobGraph().getStreamModel().computeFingerprint());
        }

        // durable resume: advance the id counter past the latest durable epoch
        try {
            io.nop.stream.core.checkpoint.CompletedCheckpoint restored =
                    checkpointCoordinator.restoreFromCheckpoint();
            if (restored != null) {
                checkpointCoordinator.advanceCheckpointIdCounterAfterRestore(restored.getCheckpointId());
                LOG.info("OpsJobManager restored durable checkpoint {} for submitted job {}",
                        restored.getCheckpointId(), jobId);
            }
        } catch (Exception e) {
            LOG.warn("OpsJobManager failed to restore checkpoint view for job {} — continuing fresh", jobId, e);
        }

        JobCoordinator coordinator = new JobCoordinator(
                jobId, "coordinator-" + jobId, artifacts.getDeploymentPlan(),
                clusterRegistry, checkpointCoordinator, rpcTargets);
        coordinator.setRemoteDeployMode(true);
        coordinator.setJobGraph(artifacts.getJobGraph());
        coordinator.setCheckpointStoragePath(checkpointStorage.getBaseDir());
        if (artifacts.getPipelineSpec() != null) {
            coordinator.setPipelineSpec(artifacts.getPipelineSpec());
        }
        coordinator.registerDistributedCommitForwarder();
        coordinator.registerDistributedAbortHandler();
        // Item 16 (P-REQ-12): fault-semantic events flow to the alert channels.
        coordinator.addJobEventListener(alertService);

        try {
            coordinator.start();
            coordinator.assignTasks();
            postAssignTasks(coordinator, artifacts);
            jobs.put(jobId, coordinator);
            terminalAt.remove(jobId);
        } catch (RuntimeException e) {
            compensateFailedSubmit(coordinator, jobId);
            throw e;
        }
        LOG.info("OpsJobManager submitted job {} (factory={}, vertices={})",
                jobId, spec.getPipelineFactoryClass(), artifacts.getJobGraph().getVertices().size());
        return coordinator;
    }

    /**
     * Starts the periodic checkpoint driver for a freshly submitted job.
     * Overridable test seam (裁定⑪ 缝4「submit 注错桩」): throwing from an
     * override simulates a submit-window failure AFTER the coordinator is
     * started and tasks assigned, exercising the orphan compensation path.
     */
    protected void postAssignTasks(JobCoordinator coordinator, ClusterPipelineFactory.PipelineArtifacts artifacts) {
        if (artifacts.getCheckpointIntervalMs() != null && artifacts.getCheckpointIntervalMs() > 0) {
            coordinator.startPeriodicCheckpoints(artifacts.getCheckpointIntervalMs());
        }
    }

    /**
     * C4-009 (plan 2510 裁定④): submit-window failure compensation. Any failure
     * between {@code coordinator.start()} and the {@code jobs.put} registration
     * would leave an orphan coordinator (live failure detector / periodic
     * checkpoint scheduler / checkpoint commit fan-out executor pools, invisible
     * to governance). The compensation terminalizes the coordinator (stop()
     * shutdownNow's the failure detector and the periodic checkpoint driver and
     * shuts down the CheckpointCoordinator pool group) and guarantees zero
     * registry entries, so a re-submit of the same jobId is safe (no second live
     * coordinator). Compensation failures are logged ERROR — never swallowed.
     *
     * <p>HA fencing zero-overlap: this runs on the failed coordinator's own
     * terminalization path; no fencing token / epoch comparison logic is touched.
     */
    private void compensateFailedSubmit(JobCoordinator coordinator, String jobId) {
        LOG.error("OpsJobManager submit failed for job {} — compensating orphan coordinator "
                + "(terminal stop + zero registry residue) before rethrow", jobId);
        try {
            coordinator.stop();
        } catch (Exception stopFailure) {
            LOG.error("OpsJobManager compensation failed to stop orphan coordinator for job {} "
                    + "— live pool leak possible, requires investigation", jobId, stopFailure);
        }
        jobs.remove(jobId);
        terminalAt.remove(jobId);
    }

    /**
     * F-09a (plan 2026-09-04-1326-3): hardened factory-class loading. Two layers:
     * <ol>
     *   <li><b>Check-before-initialize</b>: {@code Class.forName(name, false, loader)}
     *       loads WITHOUT running static initializers; the {@link ClusterPipelineFactory}
     *       interface check happens FIRST, so an arbitrary FQCN from the request body can
     *       never trigger a static-initializer side effect (the class only initializes at
     *       {@code newInstance()}, after it has already passed the interface check and is
     *       therefore a genuine factory).</li>
     *   <li><b>Narrowed name guard (adjudicated option i)</b>: array descriptors and
     *       JDK/internal package prefixes are rejected typed up front. The full
     *       {@code ClassNameValidator.ALLOWED_PREFIXES} whitelist was deliberately NOT
     *       applied: {@code pipelineFactoryClass} is a documented user-extensible contract
     *       (owner doc nop-stream.md「REST 运维 API」) and third-party factories
     *       (com.mycompany.*) must keep working; no JDK class can implement the interface
     *       anyway, and the pre-check eliminates the initializer-execution surface.</li>
     * </ol>
     */
    private static final java.util.List<String> REJECTED_FACTORY_PREFIXES = java.util.List.of(
            "[", "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.");

    static void validateFactoryClassName(String factoryClass) {
        if (factoryClass == null || factoryClass.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "pipelineFactoryClass is required for job submission");
        }
        for (String prefix : REJECTED_FACTORY_PREFIXES) {
            if (factoryClass.startsWith(prefix)) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "pipelineFactoryClass '" + factoryClass
                        + "' is rejected: factory classes in JDK/internal packages or array "
                        + "descriptors are never valid ClusterPipelineFactory implementations "
                        + "(F-09a class-loading hardening)");
            }
        }
    }

    private ClusterPipelineFactory.PipelineArtifacts buildArtifacts(JobSubmissionSpec spec, String jobId) {
        String factoryClass = spec.getPipelineFactoryClass();
        validateFactoryClassName(factoryClass);
        ClusterLaunchConfig config = ClusterLaunchConfig.parse(toArgArray(spec));
        try {
            // F-09a: initialize=false — no static initializer runs before the interface
            // check below (previously Class.forName(factoryClass) ran it eagerly).
            Class<?> clazz = Class.forName(factoryClass, false,
                    OpsJobManager.class.getClassLoader());
            if (!ClusterPipelineFactory.class.isAssignableFrom(clazz)) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "pipelineFactoryClass " + factoryClass
                        + " does not implement " + ClusterPipelineFactory.class.getName());
            }
            ClusterPipelineFactory factory =
                    (ClusterPipelineFactory) clazz.getDeclaredConstructor().newInstance();
            ClusterPipelineFactory.PipelineArtifacts artifacts = factory.buildPipeline(jobId, config);
            if (artifacts == null || artifacts.getJobGraph() == null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "pipeline factory " + factoryClass
                        + " returned null artifacts/jobGraph for job " + jobId);
            }
            return artifacts;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL, "Failed to build pipeline from factory "
                    + factoryClass + " for job " + jobId + ": " + e);
        }
    }

    private static String[] toArgArray(JobSubmissionSpec spec) {
        List<String> args = new java.util.ArrayList<>();
        args.add("jobId=" + spec.getJobId());
        args.add("pipelineFactoryClass=" + spec.getPipelineFactoryClass());
        if (spec.getParams() != null) {
            spec.getParams().forEach((k, v) -> args.add(k + "=" + v));
        }
        return args.toArray(new String[0]);
    }

    /**
     * Stops a hosted job with CANCEL, DRAIN or SUSPEND (explicit validation —
     * other modes are rejected with a clear error instead of being silently
     * accepted).
     *
     * <p>WI9 (plan 2333): SUSPEND is allowed over REST — it takes a terminal
     * savepoint and finishes the job in a recoverable state. Recovery from a
     * REST SUSPEND re-submits the same factory with the same jobId against the
     * same checkpoint storage (the REST submit path 409s on a hosted jobId, so
     * recovery after a REST SUSPEND goes through the embedded/launch path or a
     * coordinator restart with an explicit storage path). EXPORT_SAVEPOINT
     * remains rejected: it keeps the job running and is only available via the
     * coordinator RPC.
     */
    public synchronized JobCoordinator stop(String jobId, JobTerminationMode mode) {
        JobCoordinator coordinator = jobs.get(jobId);
        if (coordinator == null) {
            return null;
        }
        if (mode != JobTerminationMode.CANCEL && mode != JobTerminationMode.DRAIN
                && mode != JobTerminationMode.SUSPEND) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Unsupported stop mode '" + mode
                    + "' (REST stop supports CANCEL, DRAIN and SUSPEND; "
                    + JobTerminationMode.EXPORT_SAVEPOINT
                    + " keeps the job running and is only available via the coordinator RPC)");
        }
        coordinator.terminate(mode);
        terminalAt.put(jobId, CoreMetrics.currentTimeMillis());
        // C1-006 (plan 2510 adjudication 7): the terminal transition point is the
        // single cleanup anchor - remove this terminal job's task assignment
        // ledger entries (InMemory prefix removal + JDBC job_id DELETE). Active
        // jobs keep the append-only attempt semantics (+1 pin untouched); failure
        // is logged ERROR, never fatal to the stop path.
        try {
            clusterRegistry.removeJobTaskAssignments(jobId);
        } catch (Exception e) {
            LOG.error("Failed to remove task assignments for terminal job {} "
                    + "(assignment-ledger cleanup, C1-006)", jobId, e);
        }
        return coordinator;
    }

    /** Explicitly removes a terminal job record (governance also prunes by retention). */
    public synchronized boolean remove(String jobId) {
        JobCoordinator c = jobs.get(jobId);
        if (c != null && c.isRunning()) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Job '" + jobId + "' is still running — stop it before removal");
        }
        boolean removed = jobs.remove(jobId) != null;
        terminalAt.remove(jobId);
        return removed;
    }

    // ==================== registry (IOpsJobRegistry) ====================

    @Override
    public Set<String> jobIds() {
        return jobs.keySet();
    }

    @Override
    public JobCoordinator coordinator(String jobId) {
        return jobs.get(jobId);
    }

    public Map<String, Long> getTerminalAt() {
        return terminalAt;
    }

    // ==================== governance (P-REQ-11) ====================

    /** Starts the periodic governance sweep. */
    public synchronized void startGovernance() {
        if (governanceSweeper != null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "governance sweeper already started");
        }
        governanceSweeper = Executors.newSingleThreadScheduledExecutor(
                NopStreamThreadFactory.named("stream-ops-governance"));
        governanceSweeper.scheduleAtFixedRate(this::governanceSweep,
                governanceConfig.getCleanupIntervalMs(), governanceConfig.getCleanupIntervalMs(),
                TimeUnit.MILLISECONDS);
        scheduleAssignmentRetentionCleanup();
        LOG.info("OpsJobManager governance started (intervalMs={}, checkpointHistoryMax={}, "
                        + "checkpointHistoryRetentionMinutes={}, jobRecordRetentionMinutes={})",
                governanceConfig.getCleanupIntervalMs(),
                governanceConfig.getCheckpointHistoryMaxEntries(),
                governanceConfig.getCheckpointHistoryRetentionMinutes(),
                governanceConfig.getJobRecordRetentionMinutes());
    }

    /**
     * C1-006 (plan 2510 adjudication 7, R01 form A): schedule the periodic
     * JDBC assignment-ledger cleanup on the OpsJobManager lifecycle hook
     * ({@link #startGovernance()}). Disabled by
     * {@code nop.stream.ledger.assignment.retention-days=0}.
     */
    private void scheduleAssignmentRetentionCleanup() {
        if (assignmentRetentionConfig.getRetentionDays() <= 0) {
            LOG.info("Assignment ledger retention cleanup disabled (retention-days=0)");
            return;
        }
        if (!(clusterRegistry instanceof JdbcClusterRegistry)) {
            // InMemory ledger cleanup happens at the terminal transition point;
            // process-lifetime entries have no cross-restart TTL face.
            return;
        }
        governanceSweeper.scheduleWithFixedDelay(this::runAssignmentRetentionCleanupGuarded,
                assignmentRetentionConfig.getCleanupIntervalMs(),
                assignmentRetentionConfig.getCleanupIntervalMs(),
                TimeUnit.MILLISECONDS);
        assignmentRetentionScheduled = true;
        LOG.info("Assignment ledger retention cleanup scheduled (retentionDays={}, intervalMs={}, deleteBatchSize={})",
                assignmentRetentionConfig.getRetentionDays(),
                assignmentRetentionConfig.getCleanupIntervalMs(),
                assignmentRetentionConfig.getDeleteBatchSize());
    }

    private void runAssignmentRetentionCleanupGuarded() {
        try {
            runAssignmentRetentionCleanupOnce();
        } catch (Exception e) {
            // R01 SSOT fail-fast envelope: cleanup failure = explicit ERROR, the
            // schedule stays armed (periodic-task insulation).
            LOG.error("nop.stream.ledger.assignment cleanup-fail: periodic assignment ledger cleanup "
                    + "failed - schedule stays armed", e);
        }
    }

    /**
     * One form-A cleanup pass (also invoked directly by tests): combined
     * predicate per adjudication 7 - terminal whitelist (in-process terminalAt,
     * non-active) OR assigned_at TTL fallback, with the active-job-set exclusion
     * as the hard protection against deleting live attempts.
     */
    public int runAssignmentRetentionCleanupOnce() {
        if (!(clusterRegistry instanceof JdbcClusterRegistry)) {
            return 0;
        }
        Set<String> activeJobs = new java.util.HashSet<>();
        for (Map.Entry<String, JobCoordinator> e : jobs.entrySet()) {
            if (e.getValue().isRunning()) {
                activeJobs.add(e.getKey());
            }
        }
        Set<String> terminalJobs = new java.util.HashSet<>(terminalAt.keySet());
        terminalJobs.removeAll(activeJobs);
        long ttlCutoff = 0;
        if (assignmentRetentionConfig.getRetentionDays() > 0) {
            ttlCutoff = CoreMetrics.currentTimeMillis()
                    - TimeUnit.DAYS.toMillis(assignmentRetentionConfig.getRetentionDays());
        }
        return ((JdbcClusterRegistry) clusterRegistry).cleanupTaskAssignments(
                terminalJobs, activeJobs, ttlCutoff, assignmentRetentionConfig.getDeleteBatchSize());
    }

    /** Rule-23 evidence: the form-A cleanup schedule is armed on the lifecycle hook. */
    public boolean isAssignmentRetentionScheduled() {
        return assignmentRetentionScheduled;
    }

    /** Test seam: override the assignment retention parameters before startGovernance(). */
    public void setAssignmentRetentionConfig(LedgerRetentionConfig config) {
        this.assignmentRetentionConfig = config;
    }

    /**
     * One governance pass (also invoked directly by tests): prunes the
     * checkpoint observation history beyond the entry cap and age window, and
     * removes terminal job records past the job-record retention window.
     */
    public synchronized int governanceSweep() {
        // R5 CC-07: periodic-task containment — an unexpected sweep exception
        // must not kill the scheduleAtFixedRate task permanently (the executor
        // silently suppresses subsequent runs after an uncaught throw, unlike
        // the heartbeat/detector tasks which guard per-run).
        try {
            return sweepOnce();
        } catch (Exception e) {
            LOG.warn("governance sweep failed unexpectedly — sweep stays scheduled", e);
            return 0;
        }
    }

    private int sweepOnce() {
        long now = CoreMetrics.currentTimeMillis();
        long historyCutoff = now - TimeUnit.MINUTES.toMillis(
                governanceConfig.getCheckpointHistoryRetentionMinutes());

        int prunedEntries = 0;
        for (JobCoordinator coordinator : jobs.values()) {
            List<CheckpointHistoryEntry> history =
                    coordinator.getCheckpointCoordinator().getCheckpointHistory();
            // history is newest-first: locate the first (newest) entry that is
            // older than the cutoff — every entry from that index on is expired
            int expiredFrom = -1;
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i).getRecordedAt() < historyCutoff) {
                    expiredFrom = i;
                    break;
                }
            }
            if (expiredFrom >= 0) {
                prunedEntries += coordinator.getCheckpointCoordinator()
                        .pruneOldestCheckpointHistory(history.size() - expiredFrom);
            } else if (history.size() > governanceConfig.getCheckpointHistoryMaxEntries()) {
                prunedEntries += coordinator.getCheckpointCoordinator()
                        .pruneOldestCheckpointHistory(
                                history.size() - governanceConfig.getCheckpointHistoryMaxEntries());
            }
        }

        long recordCutoff = now - TimeUnit.MINUTES.toMillis(
                governanceConfig.getJobRecordRetentionMinutes());
        int prunedRecords = 0;
        for (Map.Entry<String, Long> e : terminalAt.entrySet()) {
            if (e.getValue() < recordCutoff) {
                String jobId = e.getKey();
                JobCoordinator c = jobs.get(jobId);
                if (c != null && !c.isRunning()) {
                    jobs.remove(jobId);
                    terminalAt.remove(jobId);
                    prunedRecords++;
                }
            }
        }
        if (prunedEntries > 0 || prunedRecords > 0) {
            LOG.info("OpsJobManager governance sweep pruned {} history entries and {} terminal job records",
                    prunedEntries, prunedRecords);
        }
        return prunedEntries + prunedRecords;
    }

    @Override
    public synchronized void close() {
        if (governanceSweeper != null) {
            governanceSweeper.shutdownNow();
            governanceSweeper = null;
        }
        for (JobCoordinator coordinator : jobs.values()) {
            try {
                if (coordinator.isRunning()) {
                    coordinator.stop();
                }
            } catch (Exception e) {
                LOG.warn("Failed to stop coordinator {} during OpsJobManager close",
                        coordinator.getJobId(), e);
            }
        }
        jobs.clear();
        terminalAt.clear();
    }
}
