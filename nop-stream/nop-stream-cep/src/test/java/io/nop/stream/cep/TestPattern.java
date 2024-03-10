/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TestPattern {
    @Test
    public void testNFA() {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() == 42))
                .next("middle")
                .subtype(SubEvent.class)
                .where(SimpleCondition.of(subEvent -> subEvent.getVolume() >= 10.0))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFA<Event> nfa = NFACompiler.compileFactory(pattern, true).createNFA();
        nfa.open(new MockRuntimeContext(), null);

        NFAState nfaState = nfa.createInitialNFAState();

        SharedBuffer<Event> partialMatches = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        List<Event> events = getData();
        for (Event event : events) {
            Collection<Map<String, List<Event>>> matches = consumeEvent(nfa, partialMatches, nfaState, event, CoreMetrics.currentTimeMillis());
            System.out.println(matches);

            if (nfaState.isStateChanged()) {
                nfaState.resetStateChanged();
                nfaState.resetNewStartPartialMatch();
            }
        }
        nfa.close();
    }

    Collection<Map<String, List<Event>>> consumeEvent(NFA<Event> nfa, SharedBuffer<Event> partialMatches,
                                                      NFAState nfaState, Event event, long timestamp) {
        try (SharedBufferAccessor<Event> sharedBufferAccessor = partialMatches.getAccessor()) {
            Tuple2<
                    Collection<Map<String, List<Event>>>,
                    Collection<Tuple2<Map<String, List<Event>>, Long>>>
                    pendingMatchesAndTimeout =
                    nfa.advanceTime(
                            sharedBufferAccessor,
                            nfaState,
                            timestamp,
                            AfterMatchSkipStrategy.noSkip());

            Collection<Map<String, List<Event>>> pendingMatches = pendingMatchesAndTimeout.f0;

            Collection<Map<String, List<Event>>> matchedPatterns =
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

    private List<Event> getData() {
        List<Event> events = new ArrayList<>();
        events.add(new Event(1, "a1"));
        events.add(new Event(32, "a32"));
        events.add(new Event(42, "a42"));
        events.add(new SubEvent(44, "a44", 12));
        events.add(new Event(46, "end"));
        events.add(new Event(47, "other"));
        return events;
    }
}
