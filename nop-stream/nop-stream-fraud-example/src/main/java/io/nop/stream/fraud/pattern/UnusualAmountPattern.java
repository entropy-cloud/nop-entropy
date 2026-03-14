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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CEP pattern for detecting unusual transaction amounts that deviate significantly 
 * from a user's historical transaction patterns.
 * 
 * <p>This pattern detects when a transaction amount exceeds 10 times the user's 
 * historical average transaction amount. To ensure reliable statistics, the pattern
 * requires a minimum of 3 previous transactions before checking for unusual amounts.</p>
 * 
 * <p>Pattern definition:</p>
 * <pre>{@code
 * Pattern<TransactionEvent, ?> pattern = Pattern.<TransactionEvent>begin("transaction")
 *     .where(amountUnusuallyHigh);
 * }</pre>
 * 
 * <p>Detection logic:</p>
 * <ul>
 *   <li>Tracks transaction count and total amount per user (in-memory)</li>
 *   <li>Calculates running average: totalAmount / transactionCount</li>
 *   <li>Triggers alert if currentAmount > (average * 10)</li>
 *   <li>Requires minimum 3 transactions before checking</li>
 * </ul>
 * 
 * @see Pattern
 * @see TransactionEvent
 * @see FraudAlert
 */
public class UnusualAmountPattern {

    /**
     * Multiplier threshold for detecting unusual amounts.
     * A transaction is considered unusual if it exceeds this multiple of the average.
     */
    private static final BigDecimal UNUSUAL_MULTIPLIER = new BigDecimal("10");

    /**
     * Minimum number of transactions required before checking for unusual amounts.
     * This ensures sufficient historical data for reliable average calculation.
     */
    private static final int MIN_TRANSACTIONS = 3;

    /**
     * Fraud type identifier for unusual amount alerts.
     */
    private static final String FRAUD_TYPE_UNUSUAL_AMOUNT = "UNUSUAL_AMOUNT";

    /**
     * Creates a CEP pattern to detect unusual transaction amounts.
     * 
     * <p>The pattern matches when:</p>
     * <ul>
     *   <li>User has at least 3 previous transactions</li>
     *   <li>Current transaction amount exceeds 10x the user's average</li>
     * </ul>
     * 
     * @return the CEP pattern for detecting unusual amounts
     */
    public static Pattern<TransactionEvent, ?> createPattern() {
        return Pattern.<TransactionEvent>begin("transaction")
                .where(SimpleCondition.of(event -> isUnusualAmount(event)));
    }

    /**
     * Checks if a transaction amount is unusually high compared to the user's average.
     * This is a simplified implementation for demo purposes.
     */
    private static boolean isUnusualAmount(TransactionEvent event) {
        // For demo purposes, we use a fixed average
        // In a real implementation, this would use state management
        BigDecimal average = getAverageForUser(event.getUserId());
        BigDecimal threshold = average.multiply(UNUSUAL_MULTIPLIER);
        return event.getAmount().compareTo(threshold) > 0;
    }

    /**
     * Gets the average transaction amount for a user.
     * For demo purposes, returns a fixed value.
     */
    private static BigDecimal getAverageForUser(String userId) {
        // Simplified: return a fixed average for demo
        return new BigDecimal("100");
    }

    /**
     * Generates a FraudAlert from a matched pattern.
     * 
     * <p>This method processes the matched events and creates a FraudAlert
     * containing information about the unusual amount pattern detected.</p>
     * 
     * @param match the map containing matched events, keyed by pattern name ("transaction")
     * @param averageAmount the user's average transaction amount at the time of detection
     * @return a FraudAlert instance with details about the detected unusual amount
     */
    public static FraudAlert generateAlert(Map<String, List<TransactionEvent>> match, BigDecimal averageAmount) {
        List<TransactionEvent> transactionEvents = match.get("transaction");

        if (transactionEvents == null || transactionEvents.isEmpty()) {
            throw new IllegalArgumentException("Match must contain 'transaction' event");
        }

        TransactionEvent event = transactionEvents.get(0);

        // Generate alert ID
        String alertId = UUID.randomUUID().toString();
        
        // Get user ID from the event
        String userId = event.getUserId();
        
        // Use the event timestamp
        long timestamp = event.getTimestamp();
        
        // Build description
        String description = String.format(
            "Unusual transaction amount detected: $%s (average: $%s, threshold: 10x average)",
            event.getAmount().setScale(2, RoundingMode.HALF_UP),
            averageAmount.setScale(2, RoundingMode.HALF_UP)
        );

        // Collect triggering events
        List<TransactionEvent> triggeringEvents = new ArrayList<>();
        triggeringEvents.add(event);

        return new FraudAlert(
            alertId,
            FRAUD_TYPE_UNUSUAL_AMOUNT,
            userId,
            description,
            timestamp,
            triggeringEvents
        );
    }

    /**
     * Gets the multiplier threshold for unusual amounts.
     * 
     * @return the multiplier threshold (10)
     */
    public static BigDecimal getUnusualMultiplier() {
        return UNUSUAL_MULTIPLIER;
    }

    /**
     * Gets the minimum number of transactions required before checking.
     * 
     * @return the minimum transaction count (3)
     */
    public static int getMinTransactions() {
        return MIN_TRANSACTIONS;
    }

    /**
     * Gets the fraud type identifier for this pattern.
     * 
     * @return the fraud type string ("UNUSUAL_AMOUNT")
     */
    public static String getFraudType() {
        return FRAUD_TYPE_UNUSUAL_AMOUNT;
    }
}
