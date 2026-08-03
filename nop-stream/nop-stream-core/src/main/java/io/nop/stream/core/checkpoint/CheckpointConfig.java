/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

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
    public static final boolean DEFAULT_ASYNC_SNAPSHOT_ENABLED = true;
    public static final int DEFAULT_ASYNC_SNAPSHOT_THREAD_POOL_SIZE = 1;
    public static final boolean DEFAULT_UNALIGNED_CHECKPOINT_ENABLED = true;

    private boolean checkpointEnabled = true;
    private long checkpointInterval = DEFAULT_CHECKPOINT_INTERVAL;
    private long checkpointTimeout = DEFAULT_CHECKPOINT_TIMEOUT;
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
     * Stage 43: validates the unaligned-checkpoint configuration invariant:
     * when {@link #unalignedCheckpointEnabled} is {@code true},
     * {@link #unalignedThreshold} must be strictly less than
     * {@link #barrierAlignmentTimeout}. Fail-fast on misconfiguration so a
     * nonsensical threshold (e.g. greater than the absolute alignment failure
     * bound) is rejected at config load rather than causing confusing runtime
     * behavior.
     *
     * @throws IllegalArgumentException if the invariant is violated
     */
    public void validateUnalignedConfig() {
        if (unalignedCheckpointEnabled && unalignedThreshold >= barrierAlignmentTimeout) {
            throw new IllegalArgumentException(
                    "Invalid checkpoint config: unalignedThreshold (" + unalignedThreshold
                            + "ms) must be < barrierAlignmentTimeout (" + barrierAlignmentTimeout
                            + "ms) when unalignedCheckpointEnabled=true");
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

        public Builder processingGuarantee(ProcessingGuarantee guarantee) {
            config.setProcessingGuarantee(guarantee);
            return this;
        }

        public Builder jobTerminationMode(JobTerminationMode mode) {
            config.setJobTerminationMode(mode);
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
