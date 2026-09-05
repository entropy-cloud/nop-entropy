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

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;

/**
 * S1 window aggregation: per (userId, tumbling window, pattern) alert count and
 * total triggering amount, emitted as {@link AlertSummaryRow} for the 2PC JDBC sink.
 *
 * <p>The window bounds are recovered from the minimum alert timestamp in the window:
 * a tumbling assigner places every element of one window instance inside
 * {@code [start, start + size)}, so {@code floor(minTs / size) * size} is exactly the
 * window start. The {@code windowSizeMs} constructor constant must match the
 * assigner bean registered under {@code windowFnId} in the scenario XDSL.
 */
public class AlertCountAggregate implements AggregateFunction<FraudAlert, AlertCountAggregate.Acc, AlertSummaryRow> {

    private static final long serialVersionUID = 1L;

    private final long windowSizeMs;

    public AlertCountAggregate(long windowSizeMs) {
        if (windowSizeMs <= 0) {
            throw new IllegalArgumentException("windowSizeMs must be positive: " + windowSizeMs);
        }
        this.windowSizeMs = windowSizeMs;
    }

    public long getWindowSizeMs() {
        return windowSizeMs;
    }

    /**
     * Accumulator state: held in keyed window state, must survive the JSON-based
     * checkpoint serde round-trip ({@code @DataBean} mutable POJO).
     */
    @io.nop.api.core.annotations.data.DataBean
    public static class Acc implements Serializable {
        private static final long serialVersionUID = 1L;

        long count;
        BigDecimal totalAmount = BigDecimal.ZERO;
        long minTimestamp = Long.MAX_VALUE;
        String userId;
        String pattern;

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public long getMinTimestamp() {
            return minTimestamp;
        }

        public void setMinTimestamp(long minTimestamp) {
            this.minTimestamp = minTimestamp;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        void add(FraudAlert alert) {
            count++;
            BigDecimal amount = alertAmount(alert);
            if (amount != null) {
                totalAmount = totalAmount.add(amount);
            }
            minTimestamp = Math.min(minTimestamp, alert.getTimestamp());
            userId = alert.getUserId();
            pattern = alert.getFraudType();
        }

        private static BigDecimal alertAmount(FraudAlert alert) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean any = false;
            for (TransactionEvent event : alert.getTriggeringEvents()) {
                if (event.getAmount() != null) {
                    sum = sum.add(event.getAmount());
                    any = true;
                }
            }
            return any ? sum : null;
        }

        Acc merge(Acc other) {
            count += other.count;
            totalAmount = totalAmount.add(other.totalAmount);
            minTimestamp = Math.min(minTimestamp, other.minTimestamp);
            userId = userId != null ? userId : other.userId;
            pattern = pattern != null ? pattern : other.pattern;
            return this;
        }
    }

    @Override
    public Acc createAccumulator() {
        return new Acc();
    }

    @Override
    public Acc add(FraudAlert value, Acc accumulator) {
        accumulator.add(value);
        return accumulator;
    }

    @Override
    public AlertSummaryRow getResult(Acc accumulator) {
        if (accumulator.count == 0) {
            throw new IllegalStateException("getResult called on an empty alert accumulator");
        }
        long windowStart = Math.floorDiv(accumulator.minTimestamp, windowSizeMs) * windowSizeMs;
        return new AlertSummaryRow(windowStart, windowStart + windowSizeMs,
                accumulator.userId, accumulator.pattern, accumulator.count, accumulator.totalAmount);
    }

    @Override
    public Acc merge(Acc a, Acc b) {
        return a.merge(b);
    }
}
