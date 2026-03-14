/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.util;

import io.nop.stream.fraud.model.TransactionEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class to generate test transactions for fraud detection demo.
 * Each method generates specific scenario transactions with configurable timestamps.
 */
public class MockTransactionGenerator {

    /**
     * Generate normal transactions for a user.
     *
     * @param userId       User ID
     * @param count        Number of transactions to generate
     * @param startTimestamp Starting timestamp (milliseconds)
     * @param intervalMs   Interval between transactions (milliseconds)
     * @return List of normal transactions
     */
    public static List<TransactionEvent> generateNormal(String userId, int count, long startTimestamp, long intervalMs) {
        List<TransactionEvent> events = new ArrayList<>();
        long timestamp = startTimestamp;

        for (int i = 0; i < count; i++) {
            TransactionEvent event = new TransactionEvent(
                    UUID.randomUUID().toString(),
                    userId,
                    new BigDecimal("50.00").add(new BigDecimal(Math.random() * 100)),
                    "New York",
                    timestamp,
                    "PURCHASE"
            );
            events.add(event);
            timestamp += intervalMs;
        }

        return events;
    }

    /**
     * Generate rapid consecutive large transactions (> $1000) within 30 seconds.
     * This triggers RapidTransactionPattern.
     *
     * @param userId       User ID
     * @param startTimestamp Starting timestamp (milliseconds)
     * @return List of rapid fraud transactions
     */
    public static List<TransactionEvent> generateRapidFraud(String userId, long startTimestamp) {
        List<TransactionEvent> events = new ArrayList<>();

        // Transaction 1: $1500 at timestamp
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("1500.00"),
                "New York",
                startTimestamp,
                "PURCHASE"
        ));

        // Transaction 2: $2000 at timestamp + 15 seconds
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("2000.00"),
                "New York",
                startTimestamp + 15000,
                "PURCHASE"
        ));

        return events;
    }

    /**
     * Generate normal transactions + 1 unusual amount transaction (>10x average).
     * This triggers UnusualAmountPattern.
     *
     * @param userId       User ID
     * @param startTimestamp Starting timestamp (milliseconds)
     * @return List of transactions with unusual amount
     */
    public static List<TransactionEvent> generateUnusualAmount(String userId, long startTimestamp) {
        List<TransactionEvent> events = new ArrayList<>();
        long timestamp = startTimestamp;

        // Generate 5 normal transactions with average ~$100
        for (int i = 0; i < 5; i++) {
            events.add(new TransactionEvent(
                    UUID.randomUUID().toString(),
                    userId,
                    new BigDecimal("80.00").add(new BigDecimal(Math.random() * 40)),
                    "New York",
                    timestamp,
                    "PURCHASE"
            ));
            timestamp += 60000; // 1 minute apart
        }

        // Add 1 unusual transaction (>10x average = >$1000)
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("2500.00"),
                "New York",
                timestamp,
                "PURCHASE"
        ));

        return events;
    }

    /**
     * Generate transactions from different cities within 1 hour.
     * This triggers GeographicAnomalyPattern.
     *
     * @param userId       User ID
     * @param startTimestamp Starting timestamp (milliseconds)
     * @return List of transactions with geographic anomaly
     */
    public static List<TransactionEvent> generateGeographicAnomaly(String userId, long startTimestamp) {
        List<TransactionEvent> events = new ArrayList<>();

        // Transaction 1: New York
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("150.00"),
                "New York",
                startTimestamp,
                "PURCHASE"
        ));

        // Transaction 2: Los Angeles 30 minutes later
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("200.00"),
                "Los Angeles",
                startTimestamp + 1800000,
                "PURCHASE"
        ));

        return events;
    }

    /**
     * Generate login → password change → withdrawal sequence within 15 minutes.
     * This triggers AccountTakeoverPattern.
     *
     * @param userId       User ID
     * @param startTimestamp Starting timestamp (milliseconds)
     * @return List of events for account takeover scenario
     */
    public static List<TransactionEvent> generateAccountTakeover(String userId, long startTimestamp) {
        List<TransactionEvent> events = new ArrayList<>();

        // Event 1: Login
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                BigDecimal.ZERO,
                "New York",
                startTimestamp,
                "LOGIN"
        ));

        // Event 2: Password change 2 minutes later
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                BigDecimal.ZERO,
                "New York",
                startTimestamp + 120000,
                "PASSWORD_CHANGE"
        ));

        // Event 3: Withdrawal 5 minutes after password change
        events.add(new TransactionEvent(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("3000.00"),
                "New York",
                startTimestamp + 420000,
                "WITHDRAWAL"
        ));

        return events;
    }
}
