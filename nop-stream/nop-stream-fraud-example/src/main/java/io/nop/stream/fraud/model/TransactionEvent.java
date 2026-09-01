/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import io.nop.api.core.annotations.data.DataBean;

/**
 * TransactionEvent implements Serializable.
 *
 * <p>{@code @DataBean} (mutable POJO with no-arg constructor and setters) so the
 * JSON-based checkpoint serde can serialize AND restore values of this type —
 * transaction events are held in CEP shared-buffer state and keyed window state
 * of the S1/S2 scenario pipelines.
 */
@DataBean
public class TransactionEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String city;
    private long timestamp;
    private String eventType;

    public TransactionEvent() {
    }

    public TransactionEvent(String transactionId, String userId, BigDecimal amount, String city, long timestamp, String eventType) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.city = city;
        this.timestamp = timestamp;
        this.eventType = eventType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public String toString() {
        return "TransactionEvent{" +
                "transactionId='" + transactionId + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                ", city='" + city + '\'' +
                ", timestamp=" + timestamp +
                ", eventType='" + eventType + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionEvent that = (TransactionEvent) o;
        return timestamp == that.timestamp &&
                Objects.equals(transactionId, that.transactionId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(city, that.city) &&
                Objects.equals(eventType, that.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, userId, amount, city, timestamp, eventType);
    }
}
