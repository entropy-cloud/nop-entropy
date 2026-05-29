package io.nop.stream.fraud.pattern;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.fraud.state.DemoKeyedStateStore;
import io.nop.stream.fraud.model.FraudAlert;
import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.commons.tuple.Tuple2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestUnusualAmountPattern {

    private List<String> results;

    @BeforeEach
    void setUp() {
        results = new ArrayList<>();
    }

    @Test
    void testPatternDetectsUnusualAmount() {
        Pattern<TransactionEvent, ?> pattern = UnusualAmountPattern.createPattern();
        NFA<TransactionEvent> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(null, null);

        NFAState nfaState = nfa.createInitialNFAState();
        SharedBuffer<TransactionEvent> buffer = new SharedBuffer<>(
                new DemoKeyedStateStore(), null, new SharedBufferCacheConfig());

        // Create a transaction above 10x the hardcoded $100 average = $1001
        TransactionEvent event = new TransactionEvent(
                "tx-1", "user-test", new BigDecimal("1500"), "NYC",
                System.currentTimeMillis(), "PURCHASE");

        processEvent(nfa, buffer, nfaState, event);

        assertFalse(results.isEmpty(), "Should detect unusual amount");
        assertTrue(results.get(0).contains("user-test"));
        nfa.close();
    }

    @Test
    void testPatternDoesNotDetectNormalAmount() {
        Pattern<TransactionEvent, ?> pattern = UnusualAmountPattern.createPattern();
        NFA<TransactionEvent> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(null, null);

        NFAState nfaState = nfa.createInitialNFAState();
        SharedBuffer<TransactionEvent> buffer = new SharedBuffer<>(
                new DemoKeyedStateStore(), null, new SharedBufferCacheConfig());

        // Create a transaction below 10x the hardcoded $100 average = $999
        TransactionEvent event = new TransactionEvent(
                "tx-2", "user-test", new BigDecimal("999"), "NYC",
                System.currentTimeMillis(), "PURCHASE");

        processEvent(nfa, buffer, nfaState, event);

        assertTrue(results.isEmpty(), "Should not detect normal amount");
        nfa.close();
    }

    @Test
    void testGenerateAlert() {
        TransactionEvent event = new TransactionEvent(
                "tx-3", "user-alert", new BigDecimal("2000"), "LA",
                System.currentTimeMillis(), "PURCHASE");

        Map<String, List<TransactionEvent>> match = Map.of("transaction", List.of(event));
        BigDecimal average = new BigDecimal("100");

        FraudAlert alert = UnusualAmountPattern.generateAlert(match, average);

        assertNotNull(alert.getAlertId());
        assertEquals("UNUSUAL_AMOUNT", alert.getFraudType());
        assertEquals("user-alert", alert.getUserId());
        assertEquals(1, alert.getTriggeringEvents().size());
        assertTrue(alert.getDescription().contains("$2000"));
    }

    @Test
    void testGetters() {
        assertEquals(new BigDecimal("10"), UnusualAmountPattern.getUnusualMultiplier());
        assertEquals(3, UnusualAmountPattern.getMinTransactions());
        assertEquals("UNUSUAL_AMOUNT", UnusualAmountPattern.getFraudType());
    }

    private void processEvent(NFA<TransactionEvent> nfa, SharedBuffer<TransactionEvent> buffer,
                              NFAState nfaState, TransactionEvent event) {
        try (SharedBufferAccessor<TransactionEvent> accessor = buffer.getAccessor()) {
            Tuple2<Collection<Map<String, List<TransactionEvent>>>,
                    Collection<Tuple2<Map<String, List<TransactionEvent>>, Long>>> result =
                    nfa.advanceTime(accessor, nfaState, event.getTimestamp(), AfterMatchSkipStrategy.noSkip());

            Collection<Map<String, List<TransactionEvent>>> matches = nfa.process(
                    accessor, nfaState, event, event.getTimestamp(), AfterMatchSkipStrategy.noSkip(), null);
            matches.addAll(result.f0);

            for (Map<String, List<TransactionEvent>> match : matches) {
                BigDecimal average = new BigDecimal("100");
                FraudAlert alert = UnusualAmountPattern.generateAlert(match, average);
                results.add(alert.getUserId() + ":" + alert.getFraudType());
            }

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
    }
}
