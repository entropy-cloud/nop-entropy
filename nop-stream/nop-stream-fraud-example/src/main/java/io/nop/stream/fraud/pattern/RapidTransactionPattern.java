/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.pattern;

import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CEP pattern for detecting rapid consecutive large transactions that may indicate fraud.
 * 
 * <p>This pattern detects when 2 or more transactions with amounts exceeding 1000
 * occur within a 30-second time window. This could indicate potential fraudulent activity
 * such as rapid unauthorized transactions.</p>
 * 
 * <p>Pattern definition:</p>
 * <pre>{@code
 * Pattern<TransactionEvent, ?> pattern = Pattern.<TransactionEvent>begin("first")
 *     .where(amountGreaterThan1000)
 *     .next("second")
 *     .where(amountGreaterThan1000)
 *     .within(Duration.ofSeconds(30));
 * }</pre>
 * 
 * @see Pattern
 * @see TransactionEvent
 * @see FraudAlert
 */
public class RapidTransactionPattern {

    /**
     * Amount threshold for considering a transaction as "large".
     * Transactions exceeding this amount are monitored for rapid consecutive occurrences.
     */
    private static final BigDecimal AMOUNT_THRESHOLD = new BigDecimal("1000");

    /**
     * Time window in seconds for detecting rapid transactions.
     * Multiple large transactions within this window trigger an alert.
     */
    private static final int TIME_WINDOW_SECONDS = 30;

    /**
     * Fraud type identifier for rapid transaction alerts.
     */
    private static final String FRAUD_TYPE_RAPID_TRANSACTION = "RAPID_TRANSACTION";

    /**
     * Creates a CEP pattern to detect rapid consecutive large transactions.
     * 
     * <p>The pattern matches when:</p>
     * <ul>
     *   <li>First transaction with amount > 1000 is detected</li>
     *   <li>Second transaction with amount > 1000 follows immediately (strict contiguity)</li>
     *   <li>Both transactions occur within 30 seconds</li>
     * </ul>
     * 
     * @return the CEP pattern for detecting rapid large transactions
     */
    public static Pattern<TransactionEvent, ?> createPattern() {
        return Pattern.<TransactionEvent>begin("first")
                .where(SimpleCondition.of(event -> event.getAmount().compareTo(AMOUNT_THRESHOLD) > 0))
                .next("second")
                .where(SimpleCondition.of(event -> event.getAmount().compareTo(AMOUNT_THRESHOLD) > 0))
                .within(Duration.ofSeconds(TIME_WINDOW_SECONDS));
    }

    /**
     * Generates a FraudAlert from a matched pattern.
     * 
     * <p>This method processes the matched events and creates a FraudAlert
     * containing information about the rapid transaction pattern detected.</p>
     * 
     * @param match the map containing matched events, keyed by pattern name ("first", "second")
     * @return a FraudAlert instance with details about the detected rapid transactions
     */
    public static FraudAlert generateAlert(Map<String, List<TransactionEvent>> match) {
        List<TransactionEvent> firstEvents = match.get("first");
        List<TransactionEvent> secondEvents = match.get("second");

        if (firstEvents == null || firstEvents.isEmpty() || 
            secondEvents == null || secondEvents.isEmpty()) {
            throw new IllegalArgumentException("Match must contain both 'first' and 'second' events");
        }

        TransactionEvent firstEvent = firstEvents.get(0);
        TransactionEvent secondEvent = secondEvents.get(0);

        // Collect all triggering events
        List<TransactionEvent> triggeringEvents = new ArrayList<>();
        triggeringEvents.add(firstEvent);
        triggeringEvents.add(secondEvent);

        // Generate alert ID
        String alertId = UUID.randomUUID().toString();
        
        // Get user ID from the first event (assuming same user)
        String userId = firstEvent.getUserId();
        
        // Use the timestamp of the second event (the one that completed the pattern)
        long timestamp = secondEvent.getTimestamp();
        
        // Build description
        String description = String.format(
            "Rapid consecutive large transactions detected: %s ($%s) followed by %s ($%s) within %d seconds",
            firstEvent.getTransactionId(),
            firstEvent.getAmount(),
            secondEvent.getTransactionId(),
            secondEvent.getAmount(),
            TIME_WINDOW_SECONDS
        );

        return new FraudAlert(
            alertId,
            FRAUD_TYPE_RAPID_TRANSACTION,
            userId,
            description,
            timestamp,
            triggeringEvents
        );
    }

    /**
     * Gets the amount threshold for large transactions.
     * 
     * @return the amount threshold (1000)
     */
    public static BigDecimal getAmountThreshold() {
        return AMOUNT_THRESHOLD;
    }

    /**
     * Gets the time window for detecting rapid transactions.
     * 
     * @return the time window in seconds (30)
     */
    public static int getTimeWindowSeconds() {
        return TIME_WINDOW_SECONDS;
    }

    /**
     * Gets the fraud type identifier for this pattern.
     * 
     * @return the fraud type string ("RAPID_TRANSACTION")
     */
    public static String getFraudType() {
        return FRAUD_TYPE_RAPID_TRANSACTION;
    }
}
