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

public class TransactionEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String transactionId;
    private final String userId;
    private final BigDecimal amount;
    private final String city;
    private final long timestamp;
    private final String eventType;

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

    public String getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCity() {
        return city;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
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