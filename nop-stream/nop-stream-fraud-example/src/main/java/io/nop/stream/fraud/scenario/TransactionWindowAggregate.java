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
import io.nop.stream.fraud.model.TransactionEvent;

/**
 * S2 window aggregation: per (userId, tumbling window) transaction count and total
 * amount, emitted as {@link TxSummaryRow} for the exactly-once file sink.
 *
 * <p>Window bounds are recovered from the minimum event timestamp in the window
 * (see {@link AlertCountAggregate} for the invariant). {@code windowSizeMs} must
 * match the assigner bean registered in {@code fraud-s2-file.stream.xml}.
 */
public class TransactionWindowAggregate
        implements AggregateFunction<TransactionEvent, TransactionWindowAggregate.Acc, TxSummaryRow> {

    private static final long serialVersionUID = 1L;

    private final long windowSizeMs;

    public TransactionWindowAggregate(long windowSizeMs) {
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

        void add(TransactionEvent event) {
            count++;
            if (event.getAmount() != null) {
                totalAmount = totalAmount.add(event.getAmount());
            }
            minTimestamp = Math.min(minTimestamp, event.getTimestamp());
            userId = event.getUserId();
        }

        Acc merge(Acc other) {
            count += other.count;
            totalAmount = totalAmount.add(other.totalAmount);
            minTimestamp = Math.min(minTimestamp, other.minTimestamp);
            userId = userId != null ? userId : other.userId;
            return this;
        }
    }

    @Override
    public Acc createAccumulator() {
        return new Acc();
    }

    @Override
    public Acc add(TransactionEvent value, Acc accumulator) {
        accumulator.add(value);
        return accumulator;
    }

    @Override
    public TxSummaryRow getResult(Acc accumulator) {
        if (accumulator.count == 0) {
            throw new IllegalStateException("getResult called on an empty accumulator");
        }
        long windowStart = Math.floorDiv(accumulator.minTimestamp, windowSizeMs) * windowSizeMs;
        return new TxSummaryRow(accumulator.userId, windowStart, windowStart + windowSizeMs,
                accumulator.count, accumulator.totalAmount);
    }

    @Override
    public Acc merge(Acc a, Acc b) {
        return a.merge(b);
    }
}
