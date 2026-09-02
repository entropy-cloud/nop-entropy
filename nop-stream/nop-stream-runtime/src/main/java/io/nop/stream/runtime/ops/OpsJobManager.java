/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

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
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.metrics.CheckpointHistoryEntry;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
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

    private final Map<String, JobCoordinator> jobs = new ConcurrentHashMap<>();
    private final Map<String, Long> terminalAt = new ConcurrentHashMap<>();
    private ScheduledExecutorService governanceSweeper;

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
        this.messageService = messageService;
        this.clusterRegistry = clusterRegistry;
        this.checkpointStorage = checkpointStorage;
        this.rpcTargets = new LinkedHashMap<>(taskRpcServices);
        this.governanceConfig = governanceConfig;
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
            throw new IllegalArgumentException("jobId is required for job submission");
        }
        if (jobs.containsKey(jobId)) {
            throw new IllegalStateException("Job '" + jobId + "' is already hosted in this process");
        }
        if (spec.getPipelineFactoryClass() == null || spec.getPipelineFactoryClass().isBlank()) {
            throw new IllegalArgumentException("pipelineFactoryClass is required for job submission");
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

        coordinator.start();
        coordinator.assignTasks();
        if (artifacts.getCheckpointIntervalMs() != null && artifacts.getCheckpointIntervalMs() > 0) {
            coordinator.startPeriodicCheckpoints(artifacts.getCheckpointIntervalMs());
        }

        jobs.put(jobId, coordinator);
        terminalAt.remove(jobId);
        LOG.info("OpsJobManager submitted job {} (factory={}, vertices={})",
                jobId, spec.getPipelineFactoryClass(), artifacts.getJobGraph().getVertices().size());
        return coordinator;
    }

    private ClusterPipelineFactory.PipelineArtifacts buildArtifacts(JobSubmissionSpec spec, String jobId) {
        String factoryClass = spec.getPipelineFactoryClass();
        ClusterLaunchConfig config = ClusterLaunchConfig.parse(toArgArray(spec));
        try {
            Class<?> clazz = Class.forName(factoryClass);
            if (!ClusterPipelineFactory.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("pipelineFactoryClass " + factoryClass
                        + " does not implement " + ClusterPipelineFactory.class.getName());
            }
            ClusterPipelineFactory factory =
                    (ClusterPipelineFactory) clazz.getDeclaredConstructor().newInstance();
            ClusterPipelineFactory.PipelineArtifacts artifacts = factory.buildPipeline(jobId, config);
            if (artifacts == null || artifacts.getJobGraph() == null) {
                throw new IllegalArgumentException("pipeline factory " + factoryClass
                        + " returned null artifacts/jobGraph for job " + jobId);
            }
            return artifacts;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build pipeline from factory "
                    + factoryClass + " for job " + jobId + ": " + e, e);
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
     * Stops a hosted job with CANCEL or DRAIN (explicit validation — other
     * modes are rejected with a clear error instead of being silently
     * accepted).
     */
    public synchronized JobCoordinator stop(String jobId, JobTerminationMode mode) {
        JobCoordinator coordinator = jobs.get(jobId);
        if (coordinator == null) {
            return null;
        }
        if (mode != JobTerminationMode.CANCEL && mode != JobTerminationMode.DRAIN) {
            throw new IllegalArgumentException("Unsupported stop mode '" + mode
                    + "' (REST stop supports CANCEL and DRAIN; use "
                    + JobTerminationMode.SUSPEND + "/" + JobTerminationMode.EXPORT_SAVEPOINT
                    + " via the coordinator RPC)");
        }
        coordinator.terminate(mode);
        terminalAt.put(jobId, System.currentTimeMillis());
        return coordinator;
    }

    /** Explicitly removes a terminal job record (governance also prunes by retention). */
    public synchronized boolean remove(String jobId) {
        JobCoordinator c = jobs.get(jobId);
        if (c != null && c.isRunning()) {
            throw new IllegalStateException("Job '" + jobId + "' is still running — stop it before removal");
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
            throw new IllegalStateException("governance sweeper already started");
        }
        governanceSweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stream-ops-governance");
            t.setDaemon(true);
            return t;
        });
        governanceSweeper.scheduleAtFixedRate(this::governanceSweep,
                governanceConfig.getCleanupIntervalMs(), governanceConfig.getCleanupIntervalMs(),
                TimeUnit.MILLISECONDS);
        LOG.info("OpsJobManager governance started (intervalMs={}, checkpointHistoryMax={}, "
                        + "checkpointHistoryRetentionMinutes={}, jobRecordRetentionMinutes={})",
                governanceConfig.getCleanupIntervalMs(),
                governanceConfig.getCheckpointHistoryMaxEntries(),
                governanceConfig.getCheckpointHistoryRetentionMinutes(),
                governanceConfig.getJobRecordRetentionMinutes());
    }

    /**
     * One governance pass (also invoked directly by tests): prunes the
     * checkpoint observation history beyond the entry cap and age window, and
     * removes terminal job records past the job-record retention window.
     */
    public synchronized int governanceSweep() {
        long now = System.currentTimeMillis();
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
