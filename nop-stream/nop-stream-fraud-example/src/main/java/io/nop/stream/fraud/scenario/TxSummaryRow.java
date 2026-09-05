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
 * S2 aggregation output row: per-user per-window transaction count and total amount.
 * {@link #toString()} is the deterministic line format written by
 * {@code FileTwoPhaseCommitSink} (which renders records via {@code toString()}).
 */
@DataBean
public class TxSummaryRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private long windowStart;
    private long windowEnd;
    private long count;
    private BigDecimal totalAmount;

    // Item 14 (distributed): no-arg ctor + setters for the data-plane codec's
    // JSON round trip (see AlertSummaryRow for the rationale).
    public TxSummaryRow() {
        this("", 0L, 0L, 0L, BigDecimal.ZERO);
    }

    public TxSummaryRow(String userId, long windowStart, long windowEnd,
                        long count, BigDecimal totalAmount) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.count = count;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    public String getUserId() {
        return userId;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    public long getCount() {
        return count;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setWindowStart(long windowStart) {
        this.windowStart = windowStart;
    }

    public void setWindowEnd(long windowEnd) {
        this.windowEnd = windowEnd;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TxSummaryRow that = (TxSummaryRow) o;
        return windowStart == that.windowStart
                && windowEnd == that.windowEnd
                && count == that.count
                && Objects.equals(userId, that.userId)
                && totalAmount.compareTo(that.totalAmount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, windowStart, windowEnd, count, totalAmount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return userId + "|" + windowStart + "|" + windowEnd + "|" + count + "|"
                + totalAmount.toPlainString();
    }
}
