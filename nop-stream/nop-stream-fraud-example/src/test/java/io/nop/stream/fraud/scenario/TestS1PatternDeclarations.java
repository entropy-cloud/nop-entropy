/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.nop.commons.tuple.Tuple2;
import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.model.CepPatternModel;
import io.nop.stream.cep.model.builder.CepPatternBuilder;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.fraud.model.TransactionEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S1_STREAM_PATH;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused check that the four fraud patterns DECLARED in the S1 XDSL compile into
 * the intended NFA sequences and match exactly the fixture sequences (XDSL
 * pattern-declaration semantics, independent of the streaming engine).
 */
public class TestS1PatternDeclarations {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static List<Map<String, List<EnrichedTransaction>>> consumeAll(
            String patternName, List<EnrichedTransaction> events) throws Exception {
        CepPatternModel model = parseStreamXml(S1_STREAM_PATH).getPattern(patternName);
        Pattern<EnrichedTransaction, ?> pattern = new CepPatternBuilder().buildFromModel(model);
        NFA<EnrichedTransaction> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(null, null);
        NFAState state = nfa.createInitialNFAState();

        io.nop.stream.fraud.state.DemoKeyedStateStore store = new io.nop.stream.fraud.state.DemoKeyedStateStore();
        SharedBuffer<EnrichedTransaction> buffer =
                new SharedBuffer<>(store, null, new SharedBufferCacheConfig());

        List<Map<String, List<EnrichedTransaction>>> matches = new ArrayList<>();
        try (SharedBufferAccessor<EnrichedTransaction> accessor = buffer.getAccessor()) {
            for (EnrichedTransaction event : events) {
                Tuple2<Collection<Map<String, List<EnrichedTransaction>>>,
                        Collection<Tuple2<Map<String, List<EnrichedTransaction>>, Long>>> pending =
                        nfa.advanceTime(accessor, state, event.getTimestamp(), AfterMatchSkipStrategy.noSkip());
                matches.addAll(pending.f0);
                Collection<Map<String, List<EnrichedTransaction>>> matched =
                        nfa.process(accessor, state, event, event.getTimestamp(),
                                AfterMatchSkipStrategy.noSkip(), null);
                matches.addAll(matched);
                if (state.isStateChanged()) {
                    state.resetStateChanged();
                    state.resetNewStartPartialMatch();
                }
            }
        }
        nfa.close();
        return matches;
    }

    private static TransactionEvent raw(String userId, String amount, long ts, String city, String eventType) {
        return new TransactionEvent("tx-" + userId + "-" + ts, userId,
                new BigDecimal(amount), city, ts, eventType);
    }

    private static EnrichedTransaction tx(String userId, String amount, long ts, String city, String eventType) {
        return new EnrichedTransaction(raw(userId, amount, ts, city, eventType), BigDecimal.ZERO, 0, null);
    }

    @Test
    public void rapidPatternMatchesAdjacentLargePairOnly() throws Exception {
        List<EnrichedTransaction> events = new ArrayList<>();
        events.add(tx("alice", "1200", T0 + 1000, "NYC", "PURCHASE"));
        events.add(tx("alice", "1500", T0 + 2000, "NYC", "PURCHASE"));

        List<Map<String, List<EnrichedTransaction>>> matches = consumeAll("rapid-transactions", events);
        assertEquals(1, matches.size(), "adjacent >1000 pair must match exactly once: " + matches);
        assertEquals(2, matches.get(0).get("first").size() + matches.get(0).get("second").size());
    }

    @Test
    public void rapidPatternRejectsSmallAmounts() throws Exception {
        List<EnrichedTransaction> events = new ArrayList<>();
        events.add(tx("frank", "50", T0 + 1000, "NYC", "PURCHASE"));
        events.add(tx("frank", "60", T0 + 2000, "NYC", "PURCHASE"));
        assertTrue(consumeAll("rapid-transactions", events).isEmpty());
    }

    @Test
    public void unusualPatternUsesEnrichedAverageGate() throws Exception {
        List<EnrichedTransaction> events = new ArrayList<>();
        events.add(new EnrichedTransaction(
                raw("bob", "2500", T0 + 1000, "NYC", "PURCHASE"), new BigDecimal("100"), 3, null));
        events.add(new EnrichedTransaction(
                raw("carol", "3000", T0 + 2000, "NYC", "PURCHASE"), new BigDecimal("500"), 3, null));

        List<Map<String, List<EnrichedTransaction>>> matches = consumeAll("unusual-amount", events);
        assertEquals(1, matches.size(), "only avg 100 user fires (2500 > 1000); 3000 < 5000 must not");
    }

    @Test
    public void geoPatternMatchesCityChangeOnly() throws Exception {
        List<EnrichedTransaction> events = new ArrayList<>();
        events.add(new EnrichedTransaction(
                raw("erin", "200", T0 + 1000, "NYC", "PURCHASE"), BigDecimal.ZERO, 0, null));
        events.add(new EnrichedTransaction(
                raw("erin", "150", T0 + 2000, "LA", "PURCHASE"), BigDecimal.ZERO, 1, "NYC"));

        List<Map<String, List<EnrichedTransaction>>> matches = consumeAll("geographic-anomaly", events);
        assertEquals(1, matches.size(), "NYC->LA consecutive pair must match: " + matches);
    }

    @Test
    public void takeoverPatternMatchesLoginChangeWithdrawSequence() throws Exception {
        List<EnrichedTransaction> events = new ArrayList<>();
        events.add(tx("dave", "0", T0 + 1000, "SEA", "LOGIN"));
        events.add(tx("dave", "0", T0 + 2000, "SEA", "CHANGE_PASSWORD"));
        events.add(tx("dave", "3000", T0 + 3000, "SEA", "WITHDRAWAL"));

        List<Map<String, List<EnrichedTransaction>>> matches = consumeAll("account-takeover", events);
        assertEquals(1, matches.size(), "LOGIN->CHANGE_PASSWORD->WITHDRAWAL must match: " + matches);
    }
}
