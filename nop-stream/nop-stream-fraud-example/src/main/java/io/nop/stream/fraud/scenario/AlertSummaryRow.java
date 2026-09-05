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

    // Item 14 (distributed): non-final + no-arg ctor + setters so the row
    // survives the data-plane codec's JSON round trip (StreamElementCodec
    // stringifies typed records on cross-JVM topics; an immutable row without
    // a default constructor fails BeanCopier reconstruction and the receiving
    // channel aborts). Value semantics are preserved by the field-based
    // equals/hashCode; instances are not mutated after entering result sets.
    private long windowStart;
    private long windowEnd;
    private String userId;
    private String pattern;
    private long alertCount;
    private BigDecimal totalAmount;

    public AlertSummaryRow() {
        this(0L, 0L, "", "", 0L, BigDecimal.ZERO);
    }

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

    public void setWindowStart(long windowStart) {
        this.windowStart = windowStart;
    }

    public void setWindowEnd(long windowEnd) {
        this.windowEnd = windowEnd;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setAlertCount(long alertCount) {
        this.alertCount = alertCount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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
