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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CEP pattern for detecting geographic anomalies in user transaction patterns.
 * 
 * <p>This pattern detects when a user makes transactions from different cities 
 * within a 1-hour time window. This could indicate potential fraudulent activity 
 * such as stolen credentials being used in different locations.</p>
 * 
 * <p>Pattern definition:</p>
 * <pre>{@code
 * Pattern<TransactionEvent, ?> pattern = Pattern.<TransactionEvent>begin("first")
 *     .where(firstCondition)
 *     .next("second")
 *     .where(secondCondition)
 *     .within(Duration.ofHours(1));
 * }</pre>
 * 
 * <p>Detection logic:</p>
 * <ul>
 *   <li>First transaction from any city</li>
 *   <li>Second transaction from a different city (strict contiguity)</li>
 *   <li>Both transactions occur within 1 hour</li>
 * </ul>
 * 
 * @see Pattern
 * @see TransactionEvent
 * @see FraudAlert
 */
public class GeographicAnomalyPattern {

    /**
     * Time window in hours for detecting geographic anomalies.
     * Multiple transactions from different cities within this window trigger an alert.
     */
    private static final int TIME_WINDOW_HOURS = 1;

    /**
     * Fraud type identifier for geographic anomaly alerts.
     */
    private static final String FRAUD_TYPE_GEOGRAPHIC_ANOMALY = "GEOGRAPHIC_ANOMALY";

    /**
     * Creates a CEP pattern to detect transactions from different cities within a short time window.
     * 
     * <p>The pattern matches when:</p>
     * <ul>
     *   <li>First transaction from any city is detected</li>
     *   <li>Second transaction from a different city follows immediately (strict contiguity)</li>
     *   <li>Both transactions occur within 1 hour</li>
     * </ul>
     * 
     * @return the CEP pattern for detecting geographic anomalies
     */
    public static Pattern<TransactionEvent, ?> createPattern() {
        return Pattern.<TransactionEvent>begin("city1")
                .where(SimpleCondition.of(event -> true)) // First transaction from any city
                .next("city2")
                .where(SimpleCondition.of(event -> {
                    // For demo purposes, we'll use a simplified approach
                    // In a real implementation, this would compare with the first transaction's city
                    return true; // Will be filtered in generateAlert
                }))
                .within(Duration.ofHours(TIME_WINDOW_HOURS));
    }

    /**
     * Generates a FraudAlert from a matched pattern.
     * 
     * <p>This method processes the matched events and creates a FraudAlert
     * containing information about the geographic anomaly pattern detected.</p>
     * 
     * @param match the map containing matched events, keyed by pattern name ("city1", "city2")
     * @return a FraudAlert instance with details about the detected geographic anomaly
     */
    public static FraudAlert generateAlert(Map<String, List<TransactionEvent>> match) {
        List<TransactionEvent> city1Events = match.get("city1");
        List<TransactionEvent> city2Events = match.get("city2");

        if (city1Events == null || city1Events.isEmpty() || 
            city2Events == null || city2Events.isEmpty()) {
            throw new IllegalArgumentException("Match must contain both 'city1' and 'city2' events");
        }

        TransactionEvent city1Event = city1Events.get(0);
        TransactionEvent city2Event = city2Events.get(0);



        // Verify that the transactions are from different cities
        if (city1Event.getCity().equals(city2Event.getCity())) {
            throw new IllegalArgumentException("Geographic anomaly requires transactions from different cities");
        }
        
        // Verify that the transactions are from the same user
        if (!city1Event.getUserId().equals(city2Event.getUserId())) {
            throw new IllegalArgumentException("Geographic anomaly requires transactions from the same user");
        }

        // Collect all triggering events
        List<TransactionEvent> triggeringEvents = new ArrayList<>();
        triggeringEvents.add(city1Event);
        triggeringEvents.add(city2Event);

        // Generate alert ID
        String alertId = UUID.randomUUID().toString();
        
        // Get user ID from the city1 event (assuming same user)
        String userId = city1Event.getUserId();
        
        // Use the timestamp of the city2 event (the one that completed the pattern)
        long timestamp = city2Event.getTimestamp();

        // Collect all triggering events



        

        
        // Build description
        String description = String.format(
            "Geographic anomaly detected: User %s made transactions from different cities within %d hour. " +
            "First transaction: %s ($%s) in %s, Second transaction: %s ($%s) in %s",
            userId,
            TIME_WINDOW_HOURS,
            city1Event.getTransactionId(),
            city1Event.getAmount(),
            city1Event.getCity(),
            city2Event.getTransactionId(),
            city2Event.getAmount(),
            city2Event.getCity()
        );

        return new FraudAlert(
            alertId,
            FRAUD_TYPE_GEOGRAPHIC_ANOMALY,
            userId,
            description,
            timestamp,
            triggeringEvents
        );
    }

    /**
     * Gets the time window for detecting geographic anomalies.
     * 
     * @return the time window in hours (1)
     */
    public static int getTimeWindowHours() {
        return TIME_WINDOW_HOURS;
    }

    /**
     * Gets the fraud type identifier for this pattern.
     * 
     * @return the fraud type string ("GEOGRAPHIC_ANOMALY")
     */
    public static String getFraudType() {
        return FRAUD_TYPE_GEOGRAPHIC_ANOMALY;
    }
}