/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import io.nop.stream.core.common.state.backend.IStateBackend;

/**
 * Checkpoint 配置类。
 */
public class CheckpointConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_CHECKPOINT_INTERVAL = 60000L;
    public static final long DEFAULT_CHECKPOINT_TIMEOUT = 600000L;
    public static final long DEFAULT_BARRIER_ALIGNMENT_TIMEOUT = 30000L;
    public static final long DEFAULT_UNALIGNED_THRESHOLD = 1000L;
    public static final long DEFAULT_MIN_PAUSE = 500L;
    public static final int DEFAULT_MAX_CONCURRENT_CHECKPOINTS = 1;
    public static final int DEFAULT_MAX_RETAINED_CHECKPOINTS = 5;
    public static final int DEFAULT_MAX_CONSECUTIVE_CHECKPOINT_FAILURES = 3;
    public static final int DEFAULT_MAX_RESTARTS_PER_REGION = 3;
    /**
     * CRA 8.43 R4-03 W-09 (MA-18=C8-010): default declared serialization rate
     * constant for the snapshot-budget coupling guard — 1000ms per thousand
     * entries. This is a PUBLICLY DECLARED calibration point (honest-source
     * discipline, 2535-B.2 alignment): real serialization rate varies with row
     * width and value types, so deployments should calibrate the constant for
     * their workload; it is not a fabricated silent conversion factor.
     */
    public static final int DEFAULT_SNAPSHOT_SERIALIZE_MS_PER_THOUSAND_ENTRIES = 1000;
    /**
     * CRA 8.43 R4-03 W-09: the declared-scale snapshot estimate may occupy at
     * most this fraction of {@link #checkpointTimeout} (the rest is left for
     * durable writes, transfer and ACK). A declared state scale whose
     * estimated snapshot time exceeds this share is rejected at config load
     * instead of entering the "checkpoint timeout &rarr; repeated failures
     * &rarr; FAIL &rarr; restart &rarr; repeat" loop.
     */
    public static final double SNAPSHOT_BUDGET_TIMEOUT_SHARE = 0.5;
    /**
     * R5 ST-01 contract: with async snapshot enabled and the memory state
     * backend, keyed state values/keys are defensively copied on the task
     * thread inside snapshotState, so operator mutations after the barrier do
     * not leak into the persisted snapshot. The copy cost is paid on the task
     * thread (same order as a synchronous snapshot); large-state jobs should
     * prefer the RocksDB backend, whose snapshots read from immutable SSTs.
     */
    public static final boolean DEFAULT_ASYNC_SNAPSHOT_ENABLED = true;
    public static final int DEFAULT_ASYNC_SNAPSHOT_THREAD_POOL_SIZE = 1;
    public static final boolean DEFAULT_UNALIGNED_CHECKPOINT_ENABLED = true;

    private boolean checkpointEnabled = true;
    private long checkpointInterval = DEFAULT_CHECKPOINT_INTERVAL;
    private long checkpointTimeout = DEFAULT_CHECKPOINT_TIMEOUT;
    /**
     * WI10 (plan 2426): named termination-budget knob (SAVEPOINT / terminal /
     * final checkpoint bounded-retry trigger budget). Nullable by design —
     * {@code null} resolves to {@link #checkpointTimeout}, which keeps every
     * existing caller's behavior byte-identical (the field unifies the CONCEPT
     * across driver faces; per-face default values are adjudicated in WI11 on
     * differential evidence, per the WI9 §4.3 1c ruling).
     */
    private Long terminationCheckpointTimeoutMs;
    private long barrierAlignmentTimeout = DEFAULT_BARRIER_ALIGNMENT_TIMEOUT;
    private long minPause = DEFAULT_MIN_PAUSE;
    private int maxConcurrentCheckpoints = DEFAULT_MAX_CONCURRENT_CHECKPOINTS;
    private int maxRetainedCheckpoints = DEFAULT_MAX_RETAINED_CHECKPOINTS;
    private int maxConsecutiveCheckpointFailures = DEFAULT_MAX_CONSECUTIVE_CHECKPOINT_FAILURES;
    private boolean asyncSnapshotEnabled = DEFAULT_ASYNC_SNAPSHOT_ENABLED;
    private int asyncSnapshotThreadPoolSize = DEFAULT_ASYNC_SNAPSHOT_THREAD_POOL_SIZE;
    private ProcessingGuarantee processingGuarantee = ProcessingGuarantee.STRICT_EXACTLY_ONCE;
    private String storageType = "local";
    private Map<String, String> storageConfig = new HashMap<>();
    private String jobId = java.util.UUID.randomUUID().toString();
    private String pipelineId = "1";
    private JobTerminationMode jobTerminationMode = JobTerminationMode.CANCEL;

    /**
     * WI9 (plan 2333): how a SUSPEND → resubmit cycle treats the data that
     * arrived while the job was stopped. Default {@link SuspendDataPolicy#REPLAY_GAP}
     * is the pre-contract behavior (replay from the restored offset).
     *
     * <p>The policy is read from the CheckpointConfig of the RESUBMITTED
     * execution and is deliberately NOT persisted in the EpochManifest — it is
     * a recovery-time decision, so every resubmission must carry it
     * explicitly.
     */
    private SuspendDataPolicy suspendDataPolicy = SuspendDataPolicy.REPLAY_GAP;

    /**
     * C4 (plan 369): transport persistence declaration tier. See
     * {@link TransportPersistence}. Default {@link TransportPersistence#DURABLE}
     * is the conservative choice: an undeclared transport must never silently
     * unlock the unaligned output-side semantics that depend on it.
     */
    private TransportPersistence transportPersistence = TransportPersistence.DURABLE;

    /**
     * Stage 43 (unaligned checkpoint): when {@code true} (default), a checkpoint
     * whose barrier alignment does not complete within {@link #unalignedThreshold}
     * switches to unaligned mode (captures in-flight channel data and completes
     * immediately) instead of waiting until {@link #barrierAlignmentTimeout} and
     * failing the task. When {@code false}, the legacy behavior is preserved
     * (alignment timeout → task FAILED → recovery).
     */
    private boolean unalignedCheckpointEnabled = DEFAULT_UNALIGNED_CHECKPOINT_ENABLED;

    /**
     * Stage 43 (unaligned checkpoint): aligned→unaligned mode-switch threshold in
     * ms. Must be strictly less than {@link #barrierAlignmentTimeout} when
     * {@link #unalignedCheckpointEnabled} is {@code true} (validated by
     * {@link #validateUnalignedConfig()}, fail-fast on misconfiguration).
     */
    private long unalignedThreshold = DEFAULT_UNALIGNED_THRESHOLD;

    /**
     * Stage 44 successor 5 (per-region restart limit configurability): per-region
     * restart budget for the LOCAL supervision loop path
     * ({@code SupervisionLoop.run}). When a region-scoped restart is attempted
     * (consumer-only or drainable producer-region downstream of a materialization
     * boundary), the supervision loop allows at most this many restarts per region
     * within one {@code SupervisionLoop.run()} invocation. When the budget is
     * exhausted the loop throws {@code ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED}
     * which surfaces to the caller for global recovery.
     *
     * <p>Default is {@value #DEFAULT_MAX_RESTARTS_PER_REGION}. Set to {@code 0} to
     * disable scoped restart entirely (first region failure surfaces immediately
     * for global recovery). Negative values are rejected at config time
     * (fail-fast, see {@link #setMaxRestartsPerRegion(int)}).
     *
     * <p><b>Counter lifecycle</b>: this budget is enforced by an in-memory counter
     * scoped to a single {@code SupervisionLoop.run()} invocation (LOCAL execution
     * path). It is independent of the DISTRIBUTED path's
     * {@code JobCoordinator.restartCount} (the two paths are fully separated —
     * LOCAL: {@code StreamExecutionEnvironment.execute()} →
     * {@code GraphModelCheckpointExecutor.executeWithCheckpoint()} →
     * {@code SupervisionLoop.run()}; DISTRIBUTED: {@code EmbeddedDistributedExecutor}
     * → {@code JobCoordinator.globalRecovery()}). There is no "cycle reset" hazard
     * because the LOCAL path does not re-invoke {@code run()} across
     * {@code globalRecovery()} cycles.
     */
    private int maxRestartsPerRegion = DEFAULT_MAX_RESTARTS_PER_REGION;

    /**
     * CRA 8.43 R4-03 W-09 (MA-18=C8-010): user-DECLARED upper bound of the
     * keyed-state entry count this job's snapshot must serialize. {@code 0}
     * (the default) means "not declared" and BYPASSES the snapshot-budget
     * coupling guard — the framework never fabricates a state size the user
     * did not declare (opt-in envelope, honest-source discipline). This single
     * declared scale covers BOTH snapshot paths (full materialization scales
     * with the entry count; the incremental path's SST re-hash cost scales
     * with the same underlying state volume) — the guard uses it as the
     * conservative upper envelope, never as a precise prediction.
     */
    private long snapshotDeclaredEntries = 0L;

    /**
     * CRA 8.43 R4-03 W-09: declared serialization rate calibration constant
     * (ms per thousand entries). Publicly declared default — calibrate per
     * workload; see {@link #DEFAULT_SNAPSHOT_SERIALIZE_MS_PER_THOUSAND_ENTRIES}.
     */
    private int snapshotSerializeMsPerThousandEntries = DEFAULT_SNAPSHOT_SERIALIZE_MS_PER_THOUSAND_ENTRIES;

    private IStateBackend stateBackend;

    public CheckpointConfig() {
    }

    public boolean isCheckpointEnabled() {
        return checkpointEnabled;
    }

    public void setCheckpointEnabled(boolean checkpointEnabled) {
        this.checkpointEnabled = checkpointEnabled;
    }

    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    public void setCheckpointInterval(long checkpointInterval) {
        this.checkpointInterval = checkpointInterval;
    }

    public long getCheckpointTimeout() {
        return checkpointTimeout;
    }

    /** WI10 (plan 2426): nullable termination-budget override (null = checkpointTimeout). */
    public Long getTerminationCheckpointTimeoutMs() {
        return terminationCheckpointTimeoutMs;
    }

    /** WI10 (plan 2426): nullable termination-budget override. */
    public void setTerminationCheckpointTimeoutMs(Long terminationCheckpointTimeoutMs) {
        this.terminationCheckpointTimeoutMs = terminationCheckpointTimeoutMs;
    }

    /**
     * WI10 (plan 2426): the effective SAVEPOINT/terminal/final trigger budget —
     * the explicit termination knob when set, otherwise the general checkpoint
     * timeout (pre-WI10 behavior, byte-identical).
     */
    public long resolveTerminationCheckpointTimeout() {
        return terminationCheckpointTimeoutMs != null
                ? terminationCheckpointTimeoutMs
                : checkpointTimeout;
    }

    public void setCheckpointTimeout(long checkpointTimeout) {
        this.checkpointTimeout = checkpointTimeout;
    }

    public long getBarrierAlignmentTimeout() {
        return barrierAlignmentTimeout;
    }

    public void setBarrierAlignmentTimeout(long barrierAlignmentTimeout) {
        this.barrierAlignmentTimeout = barrierAlignmentTimeout;
    }

    /**
     * Stage 43: whether aligned→unaligned fallback is enabled (see class javadoc
     * on {@link #unalignedCheckpointEnabled}).
     */
    public boolean isUnalignedCheckpointEnabled() {
        return unalignedCheckpointEnabled;
    }

    public void setUnalignedCheckpointEnabled(boolean unalignedCheckpointEnabled) {
        this.unalignedCheckpointEnabled = unalignedCheckpointEnabled;
    }

    /**
     * Stage 43: aligned→unaligned mode-switch threshold in ms (see class javadoc
     * on {@link #unalignedThreshold}).
     */
    public long getUnalignedThreshold() {
        return unalignedThreshold;
    }

    public void setUnalignedThreshold(long unalignedThreshold) {
        this.unalignedThreshold = unalignedThreshold;
    }

    /**
     * Stage 44 successor 5: per-region restart budget for the LOCAL supervision
     * loop path (see class javadoc on {@link #maxRestartsPerRegion}).
     */
    public int getMaxRestartsPerRegion() {
        return maxRestartsPerRegion;
    }

    /**
     * Stage 44 successor 5: sets the per-region restart budget. Fail-fast on
     * negative values (No-Silent-No-Op #24 — a nonsensical budget must be
     * rejected at config load rather than silently treated as 0 or default).
     *
     * @param maxRestartsPerRegion the per-region restart upper bound; must be {@code >= 0}
     * @throws IllegalArgumentException if {@code maxRestartsPerRegion < 0}
     */
    public void setMaxRestartsPerRegion(int maxRestartsPerRegion) {
        if (maxRestartsPerRegion < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid checkpoint config: maxRestartsPerRegion (" + maxRestartsPerRegion
                            + ") must be >= 0");
        }
        this.maxRestartsPerRegion = maxRestartsPerRegion;
    }

    /** CRA 8.43 R4-03 W-09: the declared state-scale upper bound (0 = not declared, guard bypassed). */
    public long getSnapshotDeclaredEntries() {
        return snapshotDeclaredEntries;
    }

    /** CRA 8.43 R4-03 W-09: fail-fast on a nonsensical negative declaration (0 = undeclared bypass). */
    public void setSnapshotDeclaredEntries(long snapshotDeclaredEntries) {
        if (snapshotDeclaredEntries < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "Invalid checkpoint config: snapshotDeclaredEntries (" + snapshotDeclaredEntries
                            + ") must be >= 0 (0 = not declared, snapshot-budget guard bypassed)");
        }
        this.snapshotDeclaredEntries = snapshotDeclaredEntries;
    }

    /** CRA 8.43 R4-03 W-09: the declared serialization rate calibration constant (ms per 1000 entries). */
    public int getSnapshotSerializeMsPerThousandEntries() {
        return snapshotSerializeMsPerThousandEntries;
    }

    /** CRA 8.43 R4-03 W-09: fail-fast on a non-positive rate constant (a zero rate would zero the estimate). */
    public void setSnapshotSerializeMsPerThousandEntries(int snapshotSerializeMsPerThousandEntries) {
        if (snapshotSerializeMsPerThousandEntries <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "Invalid checkpoint config: snapshotSerializeMsPerThousandEntries ("
                            + snapshotSerializeMsPerThousandEntries + ") must be > 0");
        }
        this.snapshotSerializeMsPerThousandEntries = snapshotSerializeMsPerThousandEntries;
    }

    /**
     * CRA 8.43 R4-03 W-09 (MA-18=C8-010+C8-013, adjudication D.2): declared-scale
     * snapshot-budget coupling guard ({@code validateUnalignedConfig}-shaped
     * sibling — the same fail-fast-at-config-load discipline). When the user
     * DECLARES a state scale ({@code snapshotDeclaredEntries > 0}), the
     * estimated snapshot time (declared entries &times; declared rate) must fit
     * inside {@link #SNAPSHOT_BUDGET_TIMEOUT_SHARE} of {@link #checkpointTimeout};
     * a larger declaration is rejected typed here instead of surfacing as the
     * unbounded "checkpoint timeout &rarr; repeated failure &rarr; FAIL &rarr;
     * restart &rarr; repeat" loop at runtime. An undeclared scale (0, the
     * default) bypasses the guard — no fabricated estimate.
     *
     * <p>Two-path coverage: both the full-JSON path and the incremental
     * (default) path scale monotonically with the declared state volume, so
     * the single declared envelope is checked against the conservative budget
     * share; with the incremental path default plus the new-only hash policy
     * the real cost sits well under this upper bound.
     */
    public void validateSnapshotBudget() {
        if (snapshotDeclaredEntries <= 0) {
            return;
        }
        double estimatedSnapshotMs =
                snapshotDeclaredEntries * (snapshotSerializeMsPerThousandEntries / 1000.0);
        double budgetMs = checkpointTimeout * SNAPSHOT_BUDGET_TIMEOUT_SHARE;
        if (estimatedSnapshotMs > budgetMs) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "Invalid checkpoint config: declared snapshot state scale (snapshotDeclaredEntries="
                            + snapshotDeclaredEntries + ") at the declared serialization rate ("
                            + snapshotSerializeMsPerThousandEntries + "ms per 1000 entries) needs ~"
                            + (long) estimatedSnapshotMs + "ms, above the snapshot budget share ("
                            + SNAPSHOT_BUDGET_TIMEOUT_SHARE + " x checkpointTimeout=" + checkpointTimeout
                            + "ms = " + (long) budgetMs + "ms). Reduce the state size, raise "
                            + "checkpointTimeout, calibrate snapshotSerializeMsPerThousandEntries for your "
                            + "workload, or keep the incremental default path with the new-only hash policy "
                            + "(nop.stream.rocksdb.snapshot-hash-policy=new-only) whose real cost sits well "
                            + "under this envelope");
        }
    }

    /**
     * Stage 43: validates the unaligned-checkpoint configuration invariant:
     * when {@link #unalignedCheckpointEnabled} is {@code true},
     * {@link #unalignedThreshold} must be strictly less than
     * {@link #barrierAlignmentTimeout}. Fail-fast on misconfiguration so a
     * nonsensical threshold (e.g. greater than the absolute alignment failure
     * bound) is rejected at config load rather than causing confusing runtime
     * behavior.
     *
     * <p>C4 (plan 369): also enforces the capability entry "output-side
     * in-flight data × transport persistence". An unaligned checkpoint captures
     * ONLY the input-side in-flight data; the output side relies on the
     * transport backend holding the in-flight records durably until they are
     * replayed (checkpoint-design §2.11.5). With a NON_DURABLE transport the
     * output-side in-flight data is silently lost on recovery, so the
     * combination "non-durable transport × unaligned output" is rejected at
     * startup with a typed error instead of corrupting exactly-once semantics
     * at recovery time. The default DURABLE declaration never triggers this
     * check (existing configurations are unaffected).
     *
     * @throws IllegalArgumentException if the invariant is violated
     * @throws StreamException ERR_STREAM_UNSUPPORTED when
     *         {@code unalignedCheckpointEnabled=true} is combined with
     *         {@code transportPersistence=NON_DURABLE}
     */
    public void validateUnalignedConfig() {
        if (unalignedCheckpointEnabled && unalignedThreshold >= barrierAlignmentTimeout) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid checkpoint config: unalignedThreshold (" + unalignedThreshold
                            + "ms) must be < barrierAlignmentTimeout (" + barrierAlignmentTimeout
                            + "ms) when unalignedCheckpointEnabled=true");
        }
        if (unalignedCheckpointEnabled && transportPersistence == TransportPersistence.NON_DURABLE) {
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_DETAIL,
                    "Invalid checkpoint config: unalignedCheckpointEnabled=true requires a durable "
                            + "transport, but transportPersistence=NON_DURABLE is declared. The unaligned "
                            + "mode persists only the INPUT-side in-flight data; the OUTPUT side relies "
                            + "on the transport holding in-flight records (checkpoint-design §2.11.5). "
                            + "Either declare a durable transport or disable unaligned checkpoints "
                            + "(unalignedCheckpointEnabled=false).");
        }
    }

    public long getMinPause() {
        return minPause;
    }

    public void setMinPause(long minPause) {
        this.minPause = minPause;
    }

    public int getMaxConcurrentCheckpoints() {
        return maxConcurrentCheckpoints;
    }

    public void setMaxConcurrentCheckpoints(int maxConcurrentCheckpoints) {
        this.maxConcurrentCheckpoints = maxConcurrentCheckpoints;
    }

    public int getMaxRetainedCheckpoints() {
        return maxRetainedCheckpoints;
    }

    public void setMaxRetainedCheckpoints(int maxRetainedCheckpoints) {
        this.maxRetainedCheckpoints = maxRetainedCheckpoints;
    }

    public int getMaxConsecutiveCheckpointFailures() {
        return maxConsecutiveCheckpointFailures;
    }

    public void setMaxConsecutiveCheckpointFailures(int maxConsecutiveCheckpointFailures) {
        this.maxConsecutiveCheckpointFailures = maxConsecutiveCheckpointFailures;
    }

    /**
     * Whether checkpoint persistence (storeCheckPoint + storeEpochManifest) is offloaded
     * from the coordinator ACK thread to a dedicated persist executor. When {@code true}
     * (default), the ACK thread submits the persist task and returns immediately so that
     * storage I/O does not block abort handling, timeout scheduling and trigger bookkeeping.
     * When {@code false}, the pre-async behavior is preserved (persistence runs inline on
     * the ACK caller thread under the coordinator monitor).
     */
    public boolean isAsyncSnapshotEnabled() {
        return asyncSnapshotEnabled;
    }

    public void setAsyncSnapshotEnabled(boolean asyncSnapshotEnabled) {
        this.asyncSnapshotEnabled = asyncSnapshotEnabled;
    }

    /**
     * Size of the dedicated persist thread pool used when {@link #isAsyncSnapshotEnabled()}
     * is {@code true}. Defaults to {@code 1}. Stage 45 lifted the task-side
     * single-in-flight restriction, so {@code maxConcurrentCheckpoints > 1} is now
     * honored end-to-end (see {@code checkpoint-design.md} §2.8.1).
     */
    public int getAsyncSnapshotThreadPoolSize() {
        return asyncSnapshotThreadPoolSize;
    }

    public void setAsyncSnapshotThreadPoolSize(int asyncSnapshotThreadPoolSize) {
        this.asyncSnapshotThreadPoolSize = asyncSnapshotThreadPoolSize;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Map<String, String> getStorageConfig() {
        return storageConfig;
    }

    public void setStorageConfig(Map<String, String> storageConfig) {
        this.storageConfig = storageConfig != null ? storageConfig : new HashMap<>();
    }

    public void setStorageProperty(String key, String value) {
        storageConfig.put(key, value);
    }

    public String getStorageProperty(String key) {
        return storageConfig.get(key);
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public ProcessingGuarantee getProcessingGuarantee() {
        return processingGuarantee;
    }

    public void setProcessingGuarantee(ProcessingGuarantee processingGuarantee) {
        this.processingGuarantee = processingGuarantee != null ? processingGuarantee : ProcessingGuarantee.STRICT_EXACTLY_ONCE;
    }

    public JobTerminationMode getJobTerminationMode() {
        return jobTerminationMode;
    }

    public void setJobTerminationMode(JobTerminationMode jobTerminationMode) {
        this.jobTerminationMode = jobTerminationMode != null ? jobTerminationMode : JobTerminationMode.CANCEL;
    }

    /**
     * WI9 (plan 2333): the SUSPEND downtime-gap policy in effect for this
     * submission (see {@link SuspendDataPolicy}). Never null.
     */
    public SuspendDataPolicy getSuspendDataPolicy() {
        return suspendDataPolicy;
    }

    public void setSuspendDataPolicy(SuspendDataPolicy suspendDataPolicy) {
        this.suspendDataPolicy = suspendDataPolicy != null ? suspendDataPolicy : SuspendDataPolicy.REPLAY_GAP;
    }

    /**
     * C4 (plan 369): the declared persistence tier of the data-plane transport
     * that carries this job's in-flight records (see {@link TransportPersistence}).
     */
    public TransportPersistence getTransportPersistence() {
        return transportPersistence;
    }

    public void setTransportPersistence(TransportPersistence transportPersistence) {
        this.transportPersistence = transportPersistence != null
                ? transportPersistence : TransportPersistence.DURABLE;
    }

    /**
     * C4 (plan 369): persistence tier of the data-plane transport, declared by
     * the deployment wiring that actually knows the transport (the core default
     * is the conservative {@link #DURABLE}; the runtime executors fill the
     * declaration from the injected {@code IMessageService}).
     *
     * <ul>
     *   <li>{@link #DURABLE} — in-flight records survive a process/transport
     *       failure long enough to be replayed (persistent broker: JDBC
     *       polling, Kafka, Pulsar, DB-backed queues; also the LOCAL in-JVM
     *       by-reference model, where capture is synchronous and a JVM failure
     *       recovers from the checkpoint plane regardless).</li>
     *   <li>{@link #NON_DURABLE} — in-flight records are lost when the
     *       transport fails (in-memory broker such as
     *       {@code LocalMessageService} spanning separate processes). Output-side
     *       in-flight data of an unaligned checkpoint cannot be relied on, so
     *       this tier is incompatible with
     *       {@code unalignedCheckpointEnabled=true} (validated by
     *       {@code validateUnalignedConfig()}).</li>
     * </ul>
     */
    public enum TransportPersistence {
        DURABLE,
        NON_DURABLE
    }

    public IStateBackend getStateBackend() {
        return stateBackend;
    }

    public void setStateBackend(IStateBackend stateBackend) {
        this.stateBackend = stateBackend;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CheckpointConfig config = new CheckpointConfig();

        public Builder checkpointEnabled(boolean enabled) {
            config.setCheckpointEnabled(enabled);
            return this;
        }

        public Builder checkpointInterval(long interval) {
            config.setCheckpointInterval(interval);
            return this;
        }

        public Builder checkpointTimeout(long timeout) {
            config.setCheckpointTimeout(timeout);
            return this;
        }

        public Builder barrierAlignmentTimeout(long timeout) {
            config.setBarrierAlignmentTimeout(timeout);
            return this;
        }

        public Builder unalignedCheckpointEnabled(boolean enabled) {
            config.setUnalignedCheckpointEnabled(enabled);
            return this;
        }

        public Builder unalignedThreshold(long threshold) {
            config.setUnalignedThreshold(threshold);
            return this;
        }

        /**
         * Stage 44 successor 5: per-region restart budget (see
         * {@link #setMaxRestartsPerRegion(int)}).
         */
        public Builder maxRestartsPerRegion(int max) {
            config.setMaxRestartsPerRegion(max);
            return this;
        }

        /**
         * CRA 8.43 R4-03 W-09: declared state-scale upper bound for the
         * snapshot-budget coupling guard (see
         * {@link #setSnapshotDeclaredEntries(long)}).
         */
        public Builder snapshotDeclaredEntries(long entries) {
            config.setSnapshotDeclaredEntries(entries);
            return this;
        }

        /**
         * CRA 8.43 R4-03 W-09: declared serialization rate calibration
         * constant (see {@link #setSnapshotSerializeMsPerThousandEntries(int)}).
         */
        public Builder snapshotSerializeMsPerThousandEntries(int msPerThousand) {
            config.setSnapshotSerializeMsPerThousandEntries(msPerThousand);
            return this;
        }

        public Builder minPause(long minPause) {
            config.setMinPause(minPause);
            return this;
        }

        public Builder maxConcurrentCheckpoints(int max) {
            config.setMaxConcurrentCheckpoints(max);
            return this;
        }

        public Builder maxRetainedCheckpoints(int max) {
            config.setMaxRetainedCheckpoints(max);
            return this;
        }

        public Builder maxConsecutiveCheckpointFailures(int max) {
            config.setMaxConsecutiveCheckpointFailures(max);
            return this;
        }

        public Builder asyncSnapshotEnabled(boolean enabled) {
            config.setAsyncSnapshotEnabled(enabled);
            return this;
        }

        public Builder asyncSnapshotThreadPoolSize(int size) {
            config.setAsyncSnapshotThreadPoolSize(size);
            return this;
        }

        public Builder storageType(String type) {
            config.setStorageType(type);
            return this;
        }

        public Builder storageConfig(Map<String, String> config) {
            this.config.setStorageConfig(config);
            return this;
        }

        public Builder storageProperty(String key, String value) {
            config.setStorageProperty(key, value);
            return this;
        }

        public Builder jobId(String jobId) {
            config.setJobId(jobId);
            return this;
        }

        public Builder pipelineId(String pipelineId) {
            config.setPipelineId(pipelineId);
            return this;
        }

        public Builder terminationCheckpointTimeoutMs(Long terminationCheckpointTimeoutMs) {
            config.setTerminationCheckpointTimeoutMs(terminationCheckpointTimeoutMs);
            return this;
        }

        public Builder processingGuarantee(ProcessingGuarantee guarantee) {
            config.setProcessingGuarantee(guarantee);
            return this;
        }

        public Builder jobTerminationMode(JobTerminationMode mode) {
            config.setJobTerminationMode(mode);
            return this;
        }

        /**
         * WI9 (plan 2333): SUSPEND downtime-gap policy (see
         * {@link SuspendDataPolicy}).
         */
        public Builder suspendDataPolicy(SuspendDataPolicy policy) {
            config.setSuspendDataPolicy(policy);
            return this;
        }

        /**
         * C4 (plan 369): transport persistence declaration tier (see
         * {@link TransportPersistence}).
         */
        public Builder transportPersistence(TransportPersistence persistence) {
            config.setTransportPersistence(persistence);
            return this;
        }

        public Builder stateBackend(IStateBackend stateBackend) {
            config.setStateBackend(stateBackend);
            return this;
        }

        public CheckpointConfig build() {
            return config;
        }
    }
}
