/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.ledger;

import java.util.function.Function;

/**
 * R01 族级保留策略 SSOT（`ai-dev/design/concurrency/ledger-retention-policy.md`
 * §3.1 七项参数 / §3.2 键名 / §3.4 成员传导行）在 nop-stream-runtime 的载体配置。
 * 本类零参数发明：缺省值与校验规则逐项取自该 SSOT——
 * <ul>
 *   <li>{@code retention-days}：缺省 30；0 = 显式禁用周期清理；负值 = 装配期 fail-fast。</li>
 *   <li>{@code cleanup-interval}：缺省 1h；下限 60s，低于下限装配期 fail-fast。</li>
 *   <li>{@code delete-batch-size}：缺省 1000；&le;0 装配期 fail-fast。</li>
 *   <li>键形：{@code nop.stream.ledger.<face>.retention-days / .cleanup-interval / .delete-batch-size}。</li>
 * </ul>
 * face token 由成员传导行钉死：分配账本 = {@code assignment}（plan 2510 裁定⑦），
 * msg_queue = {@code msgqueue}（plan 2510 裁定⑧）。
 */
public class LedgerRetentionConfig {

    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 3_600_000L;
    public static final long MIN_CLEANUP_INTERVAL_MS = 60_000L;
    public static final int DEFAULT_DELETE_BATCH_SIZE = 1000;

    private final String face;
    private int retentionDays = DEFAULT_RETENTION_DAYS;
    private long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;
    private int deleteBatchSize = DEFAULT_DELETE_BATCH_SIZE;

    private LedgerRetentionConfig(String face) {
        this.face = face;
    }

    /** face=assignment（C1-006 分配账本，键 {@code nop.stream.ledger.assignment.*}）。 */
    public static LedgerRetentionConfig assignmentFace() {
        return new LedgerRetentionConfig("assignment");
    }

    /** face=msgqueue（C8-003 nop_stream_msg_queue，键 {@code nop.stream.ledger.msgqueue.*}）。 */
    public static LedgerRetentionConfig msgqueueFace() {
        return new LedgerRetentionConfig("msgqueue");
    }

    /** 从属性源读取（生产：System properties；测试：显式 map）。非法值装配期 fail-fast。 */
    public static LedgerRetentionConfig fromProperties(String face, Function<String, String> props) {
        LedgerRetentionConfig config = new LedgerRetentionConfig(face);
        String retention = props.apply(config.key("retention-days"));
        if (retention != null && !retention.isBlank()) {
            config.setRetentionDays(Integer.parseInt(retention.trim()));
        }
        String interval = props.apply(config.key("cleanup-interval"));
        if (interval != null && !interval.isBlank()) {
            config.setCleanupIntervalMs(Long.parseLong(interval.trim()));
        }
        String batch = props.apply(config.key("delete-batch-size"));
        if (batch != null && !batch.isBlank()) {
            config.setDeleteBatchSize(Integer.parseInt(batch.trim()));
        }
        return config;
    }

    private String key(String suffix) {
        return "nop.stream.ledger." + face + "." + suffix;
    }

    public String getFace() {
        return face;
    }

    /** 保留期（天）；0 = 显式禁用周期清理。 */
    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        if (retentionDays < 0) {
            throw new IllegalArgumentException(
                    "nop.stream.ledger." + face + ".retention-days must be >= 0 (0 = disable periodic cleanup): "
                            + retentionDays);
        }
        this.retentionDays = retentionDays;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        if (cleanupIntervalMs < MIN_CLEANUP_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    "nop.stream.ledger." + face + ".cleanup-interval must be >= " + MIN_CLEANUP_INTERVAL_MS
                            + "ms: " + cleanupIntervalMs);
        }
        this.cleanupIntervalMs = cleanupIntervalMs;
    }

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(int deleteBatchSize) {
        if (deleteBatchSize <= 0) {
            throw new IllegalArgumentException(
                    "nop.stream.ledger." + face + ".delete-batch-size must be > 0: " + deleteBatchSize);
        }
        this.deleteBatchSize = deleteBatchSize;
    }
}
