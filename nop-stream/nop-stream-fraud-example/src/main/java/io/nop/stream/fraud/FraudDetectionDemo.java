/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud;

import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.stream.fraud.pattern.AccountTakeoverPattern;
import io.nop.stream.fraud.pattern.GeographicAnomalyPattern;
import io.nop.stream.fraud.pattern.RapidTransactionPattern;
import io.nop.stream.fraud.pattern.UnusualAmountPattern;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Demo class showcasing all 4 fraud detection patterns with mock data.
 * <p>
 * This demo demonstrates:
 * <ul>
 *   <li>Rapid Transaction Pattern - Multiple large transactions in a short time</li>
 *   <li>Unusual Amount Pattern - Transactions significantly above user's average</li>
 *   <li>Geographic Anomaly Pattern - Transactions from different cities in short time</li>
 *   <li>Account Takeover Pattern - Login, password change, withdrawal sequence</li>
 * </ul>
 */
public class FraudDetectionDemo {

    public static void main(String[] args) {
        System.out.println("=== Fraud Detection Demo ===\n");

        // Demo 1: Rapid Transactions
        System.out.println("1. Rapid Transaction Pattern:");
        System.out.println("   Detects 2+ large transactions (>$1000) within 30 seconds\n");
        List<TransactionEvent> rapid = createRapidTransactions();
        runPattern(RapidTransactionPattern.createPattern(), rapid, (match) -> {
            FraudAlert alert = RapidTransactionPattern.generateAlert(match);
            printAlert(alert);
        });

        // Demo 2: Unusual Amount
        System.out.println("\n2. Unusual Amount Pattern:");
        System.out.println("   Detects transactions >10x user's historical average (requires 3+ prior transactions)\n");
        List<TransactionEvent> unusual = createUnusualAmountTransactions();
        runUnusualAmountPattern(unusual);

        // Demo 3: Geographic Anomaly
        System.out.println("\n3. Geographic Anomaly Pattern:");
        System.out.println("   Detects transactions from different cities within 1 hour\n");
        List<TransactionEvent> geo = createGeographicAnomalyTransactions();
        runPattern(GeographicAnomalyPattern.createPattern(), geo, (match) -> {
            FraudAlert alert = GeographicAnomalyPattern.generateAlert(match);
            printAlert(alert);
        });

        // Demo 4: Account Takeover
        System.out.println("\n4. Account Takeover Pattern:");
        System.out.println("   Detects login -> password change -> withdrawal within 15 minutes\n");
        List<TransactionEvent> takeover = createAccountTakeoverTransactions();
        runPattern(AccountTakeoverPattern.createPattern(), takeover, (match) -> {
            FraudAlert alert = AccountTakeoverPattern.generateAlert(match);
            printAlert(alert);
        });

        System.out.println("\n=== Demo Complete ===");
    }

    /**
     * Creates test data for rapid transaction pattern.
     * Two large transactions within 30 seconds.
     */
    private static List<TransactionEvent> createRapidTransactions() {
        List<TransactionEvent> events = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Add some normal transactions first (below threshold)
        events.add(new TransactionEvent("txn-normal-1", "user-alice",
                new BigDecimal("100"), "New York", now - 60000, "PURCHASE"));
        events.add(new TransactionEvent("txn-normal-2", "user-alice",
                new BigDecimal("200"), "New York", now - 45000, "PURCHASE"));

        // Add two rapid large transactions (fraud pattern)
        events.add(new TransactionEvent("txn-001", "user-alice",
                new BigDecimal("1500"), "New York", now - 15000, "PURCHASE"));
        events.add(new TransactionEvent("txn-002", "user-alice",
                new BigDecimal("2000"), "New York", now - 5000, "PURCHASE"));

        return events;
    }

    /**
     * Creates test data for unusual amount pattern.
     * Normal transactions followed by an unusually large one.
     */
    private static List<TransactionEvent> createUnusualAmountTransactions() {
        List<TransactionEvent> events = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Normal transactions with average around $100
        events.add(new TransactionEvent("txn-avg-1", "user-bob",
                new BigDecimal("80"), "Chicago", now - 300000, "PURCHASE"));
        events.add(new TransactionEvent("txn-avg-2", "user-bob",
                new BigDecimal("120"), "Chicago", now - 240000, "PURCHASE"));
        events.add(new TransactionEvent("txn-avg-3", "user-bob",
                new BigDecimal("100"), "Chicago", now - 180000, "PURCHASE"));

        // Unusual transaction - 15x the average (should trigger alert)
        events.add(new TransactionEvent("txn-unusual", "user-bob",
                new BigDecimal("1500"), "Chicago", now - 120000, "PURCHASE"));

        return events;
    }

    /**
     * Creates test data for geographic anomaly pattern.
     * Transactions from different cities within a short time.
     */
    private static List<TransactionEvent> createGeographicAnomalyTransactions() {
        List<TransactionEvent> events = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Transaction in Los Angeles
        events.add(new TransactionEvent("geo-1", "user-charlie",
                new BigDecimal("500"), "Los Angeles", now - 1800000, "PURCHASE"));

        // Transaction in Miami (different city) within 1 hour
        events.add(new TransactionEvent("geo-2", "user-charlie",
                new BigDecimal("750"), "Miami", now - 1200000, "PURCHASE"));

        return events;
    }

    /**
     * Creates test data for account takeover pattern.
     * Login followed by password change and withdrawal.
     */
    private static List<TransactionEvent> createAccountTakeoverTransactions() {
        List<TransactionEvent> events = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Suspicious sequence: login -> password change -> withdrawal
        events.add(new TransactionEvent("evt-login", "user-david",
                BigDecimal.ZERO, "Seattle", now - 600000, "LOGIN"));
        events.add(new TransactionEvent("evt-change", "user-david",
                BigDecimal.ZERO, "Seattle", now - 540000, "CHANGE_PASSWORD"));
        events.add(new TransactionEvent("evt-withdraw", "user-david",
                new BigDecimal("3000"), "Seattle", now - 480000, "WITHDRAWAL"));

        return events;
    }

    /**
     * Runs a CEP pattern against a list of events.
     */
    private static void runPattern(Pattern<TransactionEvent, ?> pattern,
                                   List<TransactionEvent> events,
                                   AlertGenerator alertGenerator) {
        NFA<TransactionEvent> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(null, null);

        NFAState nfaState = nfa.createInitialNFAState();
        SharedBuffer<TransactionEvent> partialMatches = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        for (TransactionEvent event : events) {
            Collection<Map<String, List<TransactionEvent>>> matches = consumeEvent(
                    nfa, partialMatches, nfaState, event, event.getTimestamp());

            for (Map<String, List<TransactionEvent>> match : matches) {
                alertGenerator.generate(match);
            }

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        nfa.close();
    }

    /**
     * Runs the unusual amount pattern.
     */
    private static void runUnusualAmountPattern(List<TransactionEvent> events) {
        Pattern<TransactionEvent, ?> pattern = UnusualAmountPattern.createPattern();

        NFA<TransactionEvent> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(null, null);

        NFAState nfaState = nfa.createInitialNFAState();
        SharedBuffer<TransactionEvent> partialMatches = new SharedBuffer<>(
                new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        for (TransactionEvent event : events) {
            Collection<Map<String, List<TransactionEvent>>> matches = consumeEvent(
                    nfa, partialMatches, nfaState, event, event.getTimestamp());

            for (Map<String, List<TransactionEvent>> match : matches) {
                // Use a fixed average for demo purposes
                BigDecimal average = new BigDecimal("100");
                FraudAlert alert = UnusualAmountPattern.generateAlert(match, average);
                printAlert(alert);
            }

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        nfa.close();
    }

    /**
     * Processes a single event through the NFA.
     */
    private static Collection<Map<String, List<TransactionEvent>>> consumeEvent(
            NFA<TransactionEvent> nfa,
            SharedBuffer<TransactionEvent> partialMatches,
            NFAState nfaState,
            TransactionEvent event,
            long timestamp) {

        try (SharedBufferAccessor<TransactionEvent> sharedBufferAccessor = partialMatches.getAccessor()) {
            Tuple2<
                    Collection<Map<String, List<TransactionEvent>>>,
                    Collection<Tuple2<Map<String, List<TransactionEvent>>, Long>>>
                    pendingMatchesAndTimeout =
                    nfa.advanceTime(
                            sharedBufferAccessor,
                            nfaState,
                            timestamp,
                            AfterMatchSkipStrategy.noSkip());

            Collection<Map<String, List<TransactionEvent>>> pendingMatches = pendingMatchesAndTimeout.f0;

            Collection<Map<String, List<TransactionEvent>>> matchedPatterns =
                    nfa.process(
                            sharedBufferAccessor,
                            nfaState,
                            event,
                            timestamp,
                            AfterMatchSkipStrategy.noSkip(),
                            null);
            matchedPatterns.addAll(pendingMatches);
            return matchedPatterns;
        }
    }

    /**
     * Prints a fraud alert to the console.
     */
    private static void printAlert(FraudAlert alert) {
        System.out.println("   *** FRAUD ALERT ***");
        System.out.println("   Alert ID: " + alert.getAlertId());
        System.out.println("   Type: " + alert.getFraudType());
        System.out.println("   User: " + alert.getUserId());
        System.out.println("   Description: " + alert.getDescription());
        System.out.println("   Triggering Events: " + alert.getTriggeringEvents().size());
        for (TransactionEvent event : alert.getTriggeringEvents()) {
            System.out.println("      - " + event.getTransactionId() +
                    ": $" + event.getAmount() + " in " + event.getCity() +
                    " (" + event.getEventType() + ")");
        }
    }

    /**
     * Functional interface for generating alerts from matches.
     */
    @FunctionalInterface
    private interface AlertGenerator {
        void generate(Map<String, List<TransactionEvent>> match);
    }
}
