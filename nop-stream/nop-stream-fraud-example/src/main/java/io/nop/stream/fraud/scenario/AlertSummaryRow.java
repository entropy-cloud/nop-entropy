/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import io.nop.api.core.annotations.data.DataBean;

/**
 * S1 sink row: per-user per-window per-pattern alert aggregation. The natural key
 * (windowStart, windowEnd, userId, pattern) is the primary key of the
 * {@code fraud_alerts_summary} H2 table written by {@code JdbcTwoPhaseCommitSink}.
 */
@DataBean
public class AlertSummaryRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long windowStart;
    private final long windowEnd;
    private final String userId;
    private final String pattern;
    private final long alertCount;
    private final BigDecimal totalAmount;

    public AlertSummaryRow(long windowStart, long windowEnd, String userId, String pattern,
                           long alertCount, BigDecimal totalAmount) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.alertCount = alertCount;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    public String getUserId() {
        return userId;
    }

    public String getPattern() {
        return pattern;
    }

    public long getAlertCount() {
        return alertCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertSummaryRow that = (AlertSummaryRow) o;
        return windowStart == that.windowStart
                && windowEnd == that.windowEnd
                && alertCount == that.alertCount
                && Objects.equals(userId, that.userId)
                && Objects.equals(pattern, that.pattern)
                && totalAmount.compareTo(that.totalAmount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowStart, windowEnd, userId, pattern, alertCount,
                totalAmount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return userId + "|" + pattern + "|" + windowStart + "|" + windowEnd + "|"
                + alertCount + "|" + totalAmount.toPlainString();
    }
}
