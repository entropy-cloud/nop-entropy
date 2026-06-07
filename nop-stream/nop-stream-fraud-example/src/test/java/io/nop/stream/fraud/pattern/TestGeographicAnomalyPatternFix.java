package io.nop.stream.fraud.pattern;

import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestGeographicAnomalyPatternFix {

    private static TransactionEvent tx(String id, String userId, double amount, String city, long ts) {
        return new TransactionEvent(id, userId, BigDecimal.valueOf(amount), city, ts, "TRANSFER");
    }

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

        TransactionEvent city2Event = tx("t2", "user1", 100.0, "CityB", System.currentTimeMillis());

        List<TransactionEvent> city1Events = Arrays.asList(
                tx("t0", "user2", 50.0, "CityA", System.currentTimeMillis()),
                tx("t1", "user1", 50.0, "CityA", System.currentTimeMillis())
        );

        IterativeCondition.Context<TransactionEvent> mockCtx = new IterativeCondition.Context<TransactionEvent>() {
            @Override
            public Iterable<TransactionEvent> getEventsForPattern(String patternName) {
                return city1Events;
            }

            @Override
            public long timestamp() {
                return System.currentTimeMillis();
            }

            @Override
            public long currentProcessingTime() {
                return System.currentTimeMillis();
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

        TransactionEvent city2Event = tx("t2", "user1", 100.0, "CityA", System.currentTimeMillis());

        List<TransactionEvent> city1Events = Collections.singletonList(
                tx("t1", "user1", 50.0, "CityA", System.currentTimeMillis())
        );

        IterativeCondition.Context<TransactionEvent> mockCtx = new IterativeCondition.Context<TransactionEvent>() {
            @Override
            public Iterable<TransactionEvent> getEventsForPattern(String patternName) {
                return city1Events;
            }

            @Override
            public long timestamp() {
                return System.currentTimeMillis();
            }

            @Override
            public long currentProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        assertFalse(condition.filter(city2Event, mockCtx));
    }
}
