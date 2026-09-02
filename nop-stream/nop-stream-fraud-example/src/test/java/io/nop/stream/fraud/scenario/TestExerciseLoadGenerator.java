/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.T0;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.WINDOW_SIZE_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 15 (Phase 2 device verification): the load generator's plan/expectation
 * derivation and fail-fast validation, independent of any cluster.
 */
class TestExerciseLoadGenerator {

    @Test
    void s2PlanDerivesExpectedRowsFromTheEmissionPlan() {
        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(2L, 100L, 5);

        assertEquals(20L, plan.getDataLines().size(), "2s at 100ms/line = 20 data lines");
        assertEquals(4, plan.getDistinctUsers(), "5 lines per user = 4 users");

        // Hand-derived expectations (T0 is 10s-window aligned):
        // su-0000..su-0003, 5 lines each, amounts 10,11,12,13, all inside window [T0, T0+10s).
        Set<TxSummaryRow> expected = plan.getExpectedRows();
        assertEquals(4, expected.size());
        Map<String, TxSummaryRow> byUser = new LinkedHashMap<>();
        for (TxSummaryRow row : expected) {
            byUser.put(row.getUserId(), row);
        }
        for (int k = 0; k < 4; k++) {
            TxSummaryRow row = byUser.get(String.format("su-%04d", k));
            assertEquals(new BigDecimal(String.valueOf(5L * (10L + k))), row.getTotalAmount(), "user " + k);
            assertEquals(5L, row.getCount(), "user " + k);
            assertEquals(T0, row.getWindowStart(), "all 20 lines fall into the first window");
            assertEquals(T0 + WINDOW_SIZE_MS, row.getWindowEnd());
        }
        for (int k = 0; k < 4; k++) {
            assertTrue(plan.getDataLines().get(5 * k).startsWith(String.format("su-%04d,%d,", k, 10L + k)),
                    "user blocks are contiguous: " + plan.getDataLines().get(5 * k));
        }
    }

    @Test
    void s2PlanPumpTailIsFarFutureSingleWindowAndNeverExpected() {
        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(1L, 100L, 10);
        long lastDataTs = Long.parseLong(plan.getDataLines().get(plan.getDataLines().size() - 1).split(",")[2]);
        assertEquals(ExerciseLoadGenerator.PUMP_COUNT, plan.getPumpLines().size());
        long firstTs = -1L;
        long lastTs = -1L;
        for (String line : plan.getPumpLines()) {
            String[] parts = line.split(",");
            assertEquals("u-keep", parts[0]);
            long ts = Long.parseLong(parts[2]);
            assertTrue(ts >= lastDataTs + ExerciseLoadGenerator.PUMP_LEAD_MS,
                    "pump events must be far-future watermark carriers");
            if (firstTs == -1L) {
                firstTs = ts;
            }
            lastTs = ts;
        }
        long lastDataWindowEnd = ScenarioTestSupport.windowStart(lastDataTs)
                + ScenarioTestSupport.WINDOW_SIZE_MS;
        assertTrue(firstTs - ScenarioTestSupport.WATERMARK_DELAY_MS >= lastDataWindowEnd,
                "first pump watermark (ts - watermarkDelay) must close the last data window");
        assertTrue(lastTs - firstTs < ScenarioTestSupport.WINDOW_SIZE_MS,
                "pump span must be far below one window so no closable pump window opens");
        for (TxSummaryRow row : plan.getExpectedRows()) {
            assertTrue(!row.getUserId().startsWith("u-keep"), "pump windows stay in-flight, never expected");
        }
    }

    @Test
    void s2PlanIsDeterministic() {
        ExerciseLoadGenerator.S2LoadPlan a = ExerciseLoadGenerator.s2LoadPlan(3L, 50L, 4);
        ExerciseLoadGenerator.S2LoadPlan b = ExerciseLoadGenerator.s2LoadPlan(3L, 50L, 4);
        assertEquals(a.getDataLines(), b.getDataLines());
        assertEquals(a.getExpectedRows(), b.getExpectedRows());
        assertEquals(a.getPumpLines(), b.getPumpLines());
    }

    @Test
    void s2PlanValidatesParametersFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> ExerciseLoadGenerator.s2LoadPlan(0L, 100L, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ExerciseLoadGenerator.s2LoadPlan(1L, 0L, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ExerciseLoadGenerator.s2LoadPlan(1L, 100L, 0));
    }

    @Test
    void s1PlanBurstsAreAdjacentAndComputable() {
        ExerciseLoadGenerator.S1LoadPlan plan = ExerciseLoadGenerator.s1LoadPlan(2L, 100L);
        // 2s at 100ms/event = 20 data events; window 0 positions 10/11 are the burst pair.
        List<Map<String, Object>> events = plan.getEvents();
        int burstEvents = 0;
        int noiseEvents = 0;
        int pumpEvents = 0;
        for (Map<String, Object> spec : events) {
            @SuppressWarnings("unchecked")
            Map<String, Object> after = (Map<String, Object>) spec.get("after");
            long ts = ((Number) spec.get("ts")).longValue();
            if ("frank".equals(after.get("userId"))) {
                pumpEvents++;
                continue;
            }
            if (String.valueOf(after.get("userId")).startsWith("sb-")) {
                burstEvents++;
                assertEquals("1200", String.valueOf(after.get("amount")));
                assertTrue(ts == T0 + 1000L || ts == T0 + 1100L, "burst pair offsets: " + ts);
                assertEquals("NYC", after.get("city"));
                assertEquals("PURCHASE", after.get("eventType"));
            } else {
                noiseEvents++;
                assertEquals("50", String.valueOf(after.get("amount")), "noise never triggers RAPID/UNUSUAL");
                assertEquals("NYC", after.get("city"), "same city never triggers GEO");
                assertEquals("PURCHASE", after.get("eventType"), "PURCHASE never triggers TAKEOVER");
            }
        }
        assertEquals(2, burstEvents);
        assertEquals(18, noiseEvents);
        assertEquals(ExerciseLoadGenerator.PUMP_COUNT, pumpEvents);
        assertEquals(1, plan.getExpectedAlerts().size());
        AlertSummaryRow alert = plan.getExpectedAlerts().iterator().next();
        assertEquals("sb-0000", alert.getUserId());
        assertEquals(ScenarioTestSupport.PATTERN_RAPID, alert.getPattern());
        assertEquals(1L, alert.getAlertCount());
        assertEquals(new BigDecimal("2400"), alert.getTotalAmount());
        assertEquals(T0, alert.getWindowStart());
    }

    @Test
    void s1PlanWorksForFinerPacing() {
        ExerciseLoadGenerator.S1LoadPlan plan = ExerciseLoadGenerator.s1LoadPlan(1L, 50L);
        long dataEvents = plan.getEvents().stream()
                .filter(spec -> !"frank".equals(((Map<?, ?>) spec.get("after")).get("userId")))
                .count();
        assertEquals(20L, dataEvents, "1s at 50ms/event = 20 data events");
        assertEquals(1, plan.getExpectedAlerts().size());
    }

    @Test
    void s1PlanPumpTailIsFrankNoiseBeyondTheDataWindow() {
        ExerciseLoadGenerator.S1LoadPlan plan = ExerciseLoadGenerator.s1LoadPlan(1L, 100L);
        long dataEvents = 10L;
        long lastDataTs = T0 + (dataEvents - 1) * 100L;
        long dataWindowEnd = ScenarioTestSupport.windowStart(lastDataTs)
                + ScenarioTestSupport.WINDOW_SIZE_MS;
        int pump = 0;
        for (Map<String, Object> spec : plan.getEvents()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> after = (Map<String, Object>) spec.get("after");
            if ("frank".equals(after.get("userId"))) {
                pump++;
                long ts = ((Number) spec.get("ts")).longValue();
                assertTrue(ts >= lastDataTs + ExerciseLoadGenerator.PUMP_LEAD_MS);
                assertEquals("50", String.valueOf(after.get("amount")));
            }
        }
        assertEquals(ExerciseLoadGenerator.PUMP_COUNT, pump);
        long firstPumpTs = -1L;
        long lastPumpTs = -1L;
        for (Map<String, Object> spec : plan.getEvents()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> after = (Map<String, Object>) spec.get("after");
            if ("frank".equals(after.get("userId"))) {
                long ts = ((Number) spec.get("ts")).longValue();
                if (firstPumpTs == -1L) {
                    firstPumpTs = ts;
                }
                lastPumpTs = ts;
            }
        }
        assertTrue(firstPumpTs - ScenarioTestSupport.WATERMARK_DELAY_MS >= dataWindowEnd,
                "first pump watermark must close the last S1 data window");
        assertTrue(lastPumpTs - firstPumpTs < ScenarioTestSupport.WINDOW_SIZE_MS);
    }

    @Test
    void s1PlanValidatesPacingAgainstBurstOffsets() {
        assertThrows(IllegalArgumentException.class,
                () -> ExerciseLoadGenerator.s1LoadPlan(1L, 30L));
        assertThrows(IllegalArgumentException.class,
                () -> ExerciseLoadGenerator.s1LoadPlan(0L, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> ExerciseLoadGenerator.s1LoadPlan(1L, 0L));
    }
}
