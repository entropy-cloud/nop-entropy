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
import io.nop.stream.fraud.model.TransactionEvent;

/**
 * A {@link TransactionEvent} enriched with the user's keyed-state transaction history
 * (S1 scenario, composite-scenario-design.md §3.1.1 "keyed state 均值富化" layer).
 *
 * <p>The enrichment is produced by {@link UserHistoryEnricher} from keyed
 * {@code ValueState} (real per-user history — the de-stubbed successor of the fixed
 * $100 average in the standalone {@code UnusualAmountPattern} demo). CEP conditions
 * declared in {@code fraud-s1-cdc.stream.xml} stay pure per-event predicates over
 * these fields, so the pattern declarations contain no stateful logic.
 *
 * <p>{@code @DataBean}: instances live in CEP shared-buffer state and must survive
 * the JSON-based checkpoint serde round-trip.
 */
@DataBean
public class EnrichedTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    private TransactionEvent event;
    /**
     * Average of the user's PRIOR transactions (the current event excluded), or
     * {@code BigDecimal.ZERO} when the user has no history yet.
     */
    private BigDecimal avgAmount;
    /**
     * Number of PRIOR transactions of this user (current event excluded).
     */
    private long historyCount;
    /**
     * City of the user's immediately preceding transaction (arrival order), or
     * {@code null} for the user's first transaction. Used by the geographic-anomaly
     * pattern as a pure per-event predicate.
     */
    private String prevCity;

    public EnrichedTransaction() {
    }

    public EnrichedTransaction(TransactionEvent event, BigDecimal avgAmount,
                               long historyCount, String prevCity) {
        this.event = Objects.requireNonNull(event, "event");
        this.avgAmount = avgAmount != null ? avgAmount : BigDecimal.ZERO;
        this.historyCount = historyCount;
        this.prevCity = prevCity;
    }

    public TransactionEvent getEvent() {
        return event;
    }

    public void setEvent(TransactionEvent event) {
        this.event = event;
    }

    public BigDecimal getAvgAmount() {
        return avgAmount;
    }

    public void setAvgAmount(BigDecimal avgAmount) {
        this.avgAmount = avgAmount;
    }

    public long getHistoryCount() {
        return historyCount;
    }

    public void setHistoryCount(long historyCount) {
        this.historyCount = historyCount;
    }

    public String getPrevCity() {
        return prevCity;
    }

    public void setPrevCity(String prevCity) {
        this.prevCity = prevCity;
    }

    /**
     * True when the user has at least {@code UnusualAmountPattern#MIN_TRANSACTIONS}
     * prior transactions, i.e. the historical average is meaningful. Mirrors the
     * minimum-history gate of the unusual-amount fraud rule.
     */
    public boolean isAvgValid() {
        return historyCount >= 3;
    }

    // ---- delegates so XDSL keyExpr / CEP where-conditions can address the
    // ---- transaction fields directly (event.amount, event.userId, ...)

    public String getUserId() {
        return event.getUserId();
    }

    public BigDecimal getAmount() {
        return event.getAmount();
    }

    public String getCity() {
        return event.getCity();
    }

    public long getTimestamp() {
        return event.getTimestamp();
    }

    public String getEventType() {
        return event.getEventType();
    }

    public String getTransactionId() {
        return event.getTransactionId();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnrichedTransaction that = (EnrichedTransaction) o;
        return historyCount == that.historyCount
                && Objects.equals(event, that.event)
                && avgAmount != null && that.avgAmount != null
                && avgAmount.compareTo(that.avgAmount) == 0
                && Objects.equals(prevCity, that.prevCity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, avgAmount, historyCount, prevCity);
    }

    @Override
    public String toString() {
        return "EnrichedTransaction{event=" + event
                + ", avgAmount=" + avgAmount
                + ", historyCount=" + historyCount
                + ", prevCity=" + prevCity + '}';
    }
}
