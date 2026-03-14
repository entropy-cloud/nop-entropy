/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.pattern;

import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CEP pattern for detecting account takeover fraud patterns.
 * 
 * <p>This pattern detects when a user performs a suspicious sequence of actions:
 * login followed immediately by password change followed by withdrawal,
 * which may indicate that an attacker has gained access to the account.</p>
 * 
 * <p>Pattern definition:</p>
 * <pre>{@code
 * Pattern<TransactionEvent, ?> pattern = Pattern.<TransactionEvent>begin("login")
 *     .where(eventType == "LOGIN")
 *     .next("change")
 *     .where(eventType == "CHANGE_PASSWORD")
 *     .next("withdraw")
 *     .where(eventType == "WITHDRAWAL")
 *     .within(Duration.ofMinutes(15));
 * }</pre>
 * 
 * @see Pattern
 * @see TransactionEvent
 * @see FraudAlert
 */
public class AccountTakeoverPattern {

    /**
     * Time window in minutes for detecting account takeover patterns.
     * Login, password change, and withdrawal within this window trigger an alert.
     */
    private static final long TIME_WINDOW_MINUTES = 15;

    /**
     * Fraud type identifier for account takeover alerts.
     */
    private static final String FRAUD_TYPE_ACCOUNT_TAKEOVER = "ACCOUNT_TAKEOVER";

    /**
     * Creates a CEP pattern to detect login → password change → withdrawal sequences within a short time window.
     * 
     * <p>The pattern matches when:</p>
     * <ul>
     *   <li>First event is a LOGIN from a user</li>
     *   <li>Second event is a CHANGE_PASSWORD from the same user (strict contiguity)</li>
     *   <li>Third event is a WITHDRAWAL from the same user (strict contiguity)</li>
     *   <li>All three events occur within 15 minutes</li>
     * </ul>
     * 
     * @return the CEP pattern for detecting account takeover
     */
    public static Pattern<TransactionEvent, ?> createPattern() {
        return Pattern.<TransactionEvent>begin("login")
                .where(new IterativeCondition<TransactionEvent>() {
                    private static final long serialVersionUID = 1L;
                    
                    @Override
                    public boolean filter(TransactionEvent value, Context<TransactionEvent> ctx) throws Exception {
                        return "LOGIN".equals(value.getEventType());
                    }
                })
                .next("change")
                .where(new IterativeCondition<TransactionEvent>() {
                    private static final long serialVersionUID = 1L;
                    
                    @Override
                    public boolean filter(TransactionEvent value, Context<TransactionEvent> ctx) throws Exception {
                        // Get the previously matched event from "login" pattern
                        Iterable<TransactionEvent> loginEvents = ctx.getEventsForPattern("login");
                        
                        // Check if this is a CHANGE_PASSWORD event from the same user
                        if (!"CHANGE_PASSWORD".equals(value.getEventType())) {
                            return false;
                        }
                        
                        // Verify same user
                        for (TransactionEvent loginEvent : loginEvents) {
                            return value.getUserId().equals(loginEvent.getUserId());
                        }
                        
                        return false;
                    }
                })
                .next("withdraw")
                .where(new IterativeCondition<TransactionEvent>() {
                    private static final long serialVersionUID = 1L;
                    
                    @Override
                    public boolean filter(TransactionEvent value, Context<TransactionEvent> ctx) throws Exception {
                        // Get the previously matched event from "change" pattern
                        Iterable<TransactionEvent> changeEvents = ctx.getEventsForPattern("change");
                        
                        // Check if this is a WITHDRAWAL event from the same user
                        if (!"WITHDRAWAL".equals(value.getEventType())) {
                            return false;
                        }
                        
                        // Verify same user
                        for (TransactionEvent changeEvent : changeEvents) {
                            return value.getUserId().equals(changeEvent.getUserId());
                        }
                        
                        return false;
                    }
                })
                .within(Duration.ofMinutes(TIME_WINDOW_MINUTES));
    }

    /**
     * Generates a FraudAlert from a matched pattern.
     * 
     * <p>This method processes the matched events and creates a FraudAlert
     * containing information about the account takeover detected.</p>
     * 
     * @param match the map containing matched events, keyed by pattern name ("login", "change", "withdraw")
     * @return a FraudAlert instance with details about the detected account takeover
     */
    public static FraudAlert generateAlert(Map<String, List<TransactionEvent>> match) {
        List<TransactionEvent> loginEvents = match.get("login");
        List<TransactionEvent> changeEvents = match.get("change");
        List<TransactionEvent> withdrawEvents = match.get("withdraw");

        if (loginEvents == null || loginEvents.isEmpty() || 
            changeEvents == null || changeEvents.isEmpty() ||
            withdrawEvents == null || withdrawEvents.isEmpty()) {
            throw new IllegalArgumentException("Match must contain 'login', 'change', and 'withdraw' events");
        }

        TransactionEvent loginEvent = loginEvents.get(0);
        TransactionEvent changeEvent = changeEvents.get(0);
        TransactionEvent withdrawEvent = withdrawEvents.get(0);

        // Verify that all events are from the same user
        String userId = loginEvent.getUserId();
        if (!userId.equals(changeEvent.getUserId()) || !userId.equals(withdrawEvent.getUserId())) {
            throw new IllegalArgumentException("All events must be from the same user");
        }

        // Verify event types
        if (!"LOGIN".equals(loginEvent.getEventType())) {
            throw new IllegalArgumentException("First event must be LOGIN");
        }
        if (!"CHANGE_PASSWORD".equals(changeEvent.getEventType())) {
            throw new IllegalArgumentException("Second event must be CHANGE_PASSWORD");
        }
        if (!"WITHDRAWAL".equals(withdrawEvent.getEventType())) {
            throw new IllegalArgumentException("Third event must be WITHDRAWAL");
        }

        // Collect all triggering events
        List<TransactionEvent> triggeringEvents = new ArrayList<>();
        triggeringEvents.add(loginEvent);
        triggeringEvents.add(changeEvent);
        triggeringEvents.add(withdrawEvent);

        // Generate alert ID
        String alertId = UUID.randomUUID().toString();
        
        // Use the timestamp of the withdrawal event (the one that completed the pattern)
        long timestamp = withdrawEvent.getTimestamp();
        
        // Build description
        String description = String.format(
            "Account takeover detected: User %s logged in, changed password, and made a withdrawal " +
            "($%s) within %d minutes. Login in %s, password change in %s, withdrawal in %s. " +
            "Transaction IDs: %s -> %s -> %s",
            userId,
            withdrawEvent.getAmount(),
            TIME_WINDOW_MINUTES,
            loginEvent.getCity(),
            changeEvent.getCity(),
            withdrawEvent.getCity(),
            loginEvent.getTransactionId(),
            changeEvent.getTransactionId(),
            withdrawEvent.getTransactionId()
        );

        return new FraudAlert(
            alertId,
            FRAUD_TYPE_ACCOUNT_TAKEOVER,
            userId,
            description,
            timestamp,
            triggeringEvents
        );
    }

    /**
     * Gets the time window for detecting account takeover patterns.
     * 
     * @return the time window in minutes (15)
     */
    public static long getTimeWindowMinutes() {
        return TIME_WINDOW_MINUTES;
    }

    /**
     * Gets the fraud type identifier for this pattern.
     * 
     * @return the fraud type string ("ACCOUNT_TAKEOVER")
     */
    public static String getFraudType() {
        return FRAUD_TYPE_ACCOUNT_TAKEOVER;
    }
}
