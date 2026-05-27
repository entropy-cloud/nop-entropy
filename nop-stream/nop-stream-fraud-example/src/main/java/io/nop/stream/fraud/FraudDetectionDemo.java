/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.nop.commons.tuple.Tuple2;

import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
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
import io.nop.stream.fraud.util.MockTransactionGenerator;

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

    private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionDemo.class);

    public static void main(String[] args) {
        try {
            System.out.println("=== Fraud Detection Demo ===\n");

            // Demo 1: Rapid Transactions
            System.out.println("1. Rapid Transaction Pattern:");
            System.out.println("   Detects 2+ large transactions (>$1000) within 30 seconds\n");
            List<TransactionEvent> rapid = new ArrayList<>();
            long baseTime = System.currentTimeMillis();
            // Add some normal transactions first (below threshold)
            rapid.addAll(MockTransactionGenerator.generateNormal("user-alice", 2, baseTime - 60000, 15000));
            // Add rapid fraud transactions
            rapid.addAll(MockTransactionGenerator.generateRapidFraud("user-alice", baseTime - 15000));
            runPattern(RapidTransactionPattern.createPattern(), rapid, (match) -> {
                FraudAlert alert = RapidTransactionPattern.generateAlert(match);
                printAlert(alert);
            });

            // Demo 2: Unusual Amount
            System.out.println("\n2. Unusual Amount Pattern:");
            System.out.println("   Detects transactions >10x user's historical average (requires 3+ prior transactions)\n");
            List<TransactionEvent> unusual = MockTransactionGenerator.generateUnusualAmount("user-bob", baseTime);
            runUnusualAmountPattern(unusual);

            // Demo 3: Geographic Anomaly
            System.out.println("\n3. Geographic Anomaly Pattern:");
            System.out.println("   Detects transactions from different cities within 1 hour\n");
            List<TransactionEvent> geo = MockTransactionGenerator.generateGeographicAnomaly("user-charlie", baseTime - 1800000);
            runPattern(GeographicAnomalyPattern.createPattern(), geo, (match) -> {
                FraudAlert alert = GeographicAnomalyPattern.generateAlert(match);
                printAlert(alert);
            });

            // Demo 4: Account Takeover
            System.out.println("\n4. Account Takeover Pattern:");
            System.out.println("   Detects login -> password change -> withdrawal within 15 minutes\n");
            List<TransactionEvent> takeover = MockTransactionGenerator.generateAccountTakeover("user-david", baseTime - 600000);
            runPattern(AccountTakeoverPattern.createPattern(), takeover, (match) -> {
                FraudAlert alert = AccountTakeoverPattern.generateAlert(match);
                printAlert(alert);
            });

            System.out.println("\n=== Demo Complete ===");
        } catch (Exception e) {
            LOG.error("Demo failed", e);
        }
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
