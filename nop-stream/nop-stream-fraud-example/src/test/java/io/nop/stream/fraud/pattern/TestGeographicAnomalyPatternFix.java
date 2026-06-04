package io.nop.stream.fraud.pattern;

import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestGeographicAnomalyPatternFix {

    @Test
    void testCity2FilterIteratesAllCity1Events() throws Exception {
        IterativeCondition<TransactionEvent> condition = new IterativeCondition<TransactionEvent>() {
            @Override
            public boolean filter(TransactionEvent value, Context<TransactionEvent> ctx) throws Exception {
                for (TransactionEvent city1Event : ctx.getEventsForPattern("city1")) {
                    if (!value.getUserId().equals(city1Event.getUserId())) {
                        continue;
                    }
                    if (!value.getCity().equals(city1Event.getCity())) {
                        return true;
                    }
                }
                return false;
            }
        };

        TransactionEvent city2Event = new TransactionEvent("t2", "user1", 100.0, "CityB", System.currentTimeMillis());

        List<TransactionEvent> city1Events = Arrays.asList(
                new TransactionEvent("t0", "user2", 50.0, "CityA", System.currentTimeMillis()),
                new TransactionEvent("t1", "user1", 50.0, "CityA", System.currentTimeMillis())
        );

        IterativeCondition.Context<TransactionEvent> mockCtx = new IterativeCondition.Context<TransactionEvent>() {
            @Override
            public Iterable<TransactionEvent> getEventsForPattern(String patternName) {
                return city1Events;
            }
        };

        assertTrue(condition.filter(city2Event, mockCtx));
    }

    @Test
    void testCity2FilterReturnsFalseForSameUserSameCity() throws Exception {
        IterativeCondition<TransactionEvent> condition = new IterativeCondition<TransactionEvent>() {
            @Override
            public boolean filter(TransactionEvent value, Context<TransactionEvent> ctx) throws Exception {
                for (TransactionEvent city1Event : ctx.getEventsForPattern("city1")) {
                    if (!value.getUserId().equals(city1Event.getUserId())) {
                        continue;
                    }
                    if (!value.getCity().equals(city1Event.getCity())) {
                        return true;
                    }
                }
                return false;
            }
        };

        TransactionEvent city2Event = new TransactionEvent("t2", "user1", 100.0, "CityA", System.currentTimeMillis());

        List<TransactionEvent> city1Events = Collections.singletonList(
                new TransactionEvent("t1", "user1", 50.0, "CityA", System.currentTimeMillis())
        );

        IterativeCondition.Context<TransactionEvent> mockCtx = new IterativeCondition.Context<TransactionEvent>() {
            @Override
            public Iterable<TransactionEvent> getEventsForPattern(String patternName) {
                return city1Events;
            }
        };

        assertFalse(condition.filter(city2Event, mockCtx));
    }
}
