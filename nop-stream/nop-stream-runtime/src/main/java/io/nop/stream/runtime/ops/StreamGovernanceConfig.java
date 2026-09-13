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
import java.util.function.Function;

/**
 * Item 16 (P-REQ-11): lifecycle governance configuration — retention of the
 * checkpoint observation history and terminal job records, plus the sweep
 * cadence. Defaults follow the runbook (history 100 entries / 1440 minutes,
 * job records 1440 minutes, sweep every 5 minutes); durable checkpoint
 * retention stays governed by {@code CheckpointConfig.maxRetainedCheckpoints}
 * (storage-plane, deliberately decoupled from this observation-plane config).
 */
public class StreamGovernanceConfig {

    public static final String KEY_CHECKPOINT_HISTORY_MAX_ENTRIES =
            "nop.stream.ops.checkpoint-history.max-entries";
    public static final String KEY_CHECKPOINT_HISTORY_RETENTION_MINUTES =
            "nop.stream.ops.checkpoint-history.retention-minutes";
    public static final String KEY_JOB_RECORD_RETENTION_MINUTES =
            "nop.stream.ops.job-record.retention-minutes";
    public static final String KEY_CLEANUP_INTERVAL_MS =
            "nop.stream.ops.governance.cleanup-interval-ms";

    public static final int DEFAULT_CHECKPOINT_HISTORY_MAX_ENTRIES = 100;
    public static final long DEFAULT_CHECKPOINT_HISTORY_RETENTION_MINUTES = 1440L;
    public static final long DEFAULT_JOB_RECORD_RETENTION_MINUTES = 1440L;
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 300_000L;

    private int checkpointHistoryMaxEntries = DEFAULT_CHECKPOINT_HISTORY_MAX_ENTRIES;
    private long checkpointHistoryRetentionMinutes = DEFAULT_CHECKPOINT_HISTORY_RETENTION_MINUTES;
    private long jobRecordRetentionMinutes = DEFAULT_JOB_RECORD_RETENTION_MINUTES;
    private long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;

    public static StreamGovernanceConfig fromProperties(Function<String, String> props) {
        StreamGovernanceConfig config = new StreamGovernanceConfig();
        String max = props.apply(KEY_CHECKPOINT_HISTORY_MAX_ENTRIES);
        if (max != null && !max.isBlank()) {
            config.setCheckpointHistoryMaxEntries(Integer.parseInt(max.trim()));
        }
        String retention = props.apply(KEY_CHECKPOINT_HISTORY_RETENTION_MINUTES);
        if (retention != null && !retention.isBlank()) {
            config.setCheckpointHistoryRetentionMinutes(Long.parseLong(retention.trim()));
        }
        String jobRetention = props.apply(KEY_JOB_RECORD_RETENTION_MINUTES);
        if (jobRetention != null && !jobRetention.isBlank()) {
            config.setJobRecordRetentionMinutes(Long.parseLong(jobRetention.trim()));
        }
        String interval = props.apply(KEY_CLEANUP_INTERVAL_MS);
        if (interval != null && !interval.isBlank()) {
            config.setCleanupIntervalMs(Long.parseLong(interval.trim()));
        }
        return config;
    }

    public int getCheckpointHistoryMaxEntries() {
        return checkpointHistoryMaxEntries;
    }

    public void setCheckpointHistoryMaxEntries(int checkpointHistoryMaxEntries) {
        this.checkpointHistoryMaxEntries = Math.max(1, checkpointHistoryMaxEntries);
    }

    public long getCheckpointHistoryRetentionMinutes() {
        return checkpointHistoryRetentionMinutes;
    }

    public void setCheckpointHistoryRetentionMinutes(long minutes) {
        if (minutes < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "retention minutes must be >= 0: " + minutes);
        }
        this.checkpointHistoryRetentionMinutes = minutes;
    }

    public long getJobRecordRetentionMinutes() {
        return jobRecordRetentionMinutes;
    }

    public void setJobRecordRetentionMinutes(long minutes) {
        if (minutes < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "retention minutes must be >= 0: " + minutes);
        }
        this.jobRecordRetentionMinutes = minutes;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        if (cleanupIntervalMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "cleanup interval must be positive: " + cleanupIntervalMs);
        }
        this.cleanupIntervalMs = cleanupIntervalMs;
    }
}
