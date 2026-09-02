/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.CdcEventFixtures;

/**
 * Item 15 (stability exercise): parametric load generator for the soak / chaos /
 * backpressure drill cells. Extends the S1/S2 bounded-fixture pattern (no new
 * engine capability): given (durationSec, pacing, state-growth shape) it produces
 * BOTH the deterministic input fixture AND the exact expected sink output derived
 * from the same emission plan — the drill's integrity assertion is therefore
 * single-source (no hand-computed expectations).
 *
 * <p>Fixture shapes (Phase 1 matrix, {@code ai-dev/analysis/2026-09/
 * 2026-09-03-distributed-stability-exercise-report.md}):
 * <ul>
 *   <li><b>S2</b> ({@code userId,amount,eventTime} lines): new keyed user every
 *       {@code linesPerUser} lines (state growth), event time paced 1:1 with wall
 *       clock ({@code T0 + i * lineDelayMs}), plus a far-future watermark-pump tail
 *       so every DATA window closes mid-run (pump windows stay in-flight, mirroring
 *       the C0 watermark-pump semantics).</li>
 *   <li><b>S1</b> (CDC event specs): per 10s event-time window one burst user with
 *       exactly two adjacent &gt;1000 PURCHASE events in the same city (exactly one
 *       RAPID_TRANSACTION alert, computable) + non-triggering noise (amount 50,
 *       same city, PURCHASE — no UNUSUAL/GEO/TAKEOVER by construction).</li>
 * </ul>
 */
public final class ExerciseLoadGenerator {

    /** Watermark-pump lead beyond the last data event time (must exceed watermark delay + window). */
    static final long PUMP_LEAD_MS = 20_000L;
    /**
     * Pump events are spaced {@value #PUMP_EVENT_STEP_MS}ms apart in EVENT time and
     * ALL stay inside ONE far-future window: the FIRST pump event advances the
     * watermark past every data window (so they all close and commit mid-run),
     * while the pump's own window never closes and stays in-flight at EOS — a pump
     * spanning &gt;10s of event time would open closable windows of its own and
     * commit unexpected rows (drill smoke finding 2026-09-03). The wall-clock
     * commit margin comes from the source's finishLingerMs, not from pump volume.
     */
    static final long PUMP_EVENT_STEP_MS = 100L;
    static final int PUMP_COUNT = 12;

    private ExerciseLoadGenerator() {
    }

    public static final class S2LoadPlan {
        final List<String> dataLines = new ArrayList<>();
        final List<String> pumpLines = new ArrayList<>();
        final Set<TxSummaryRow> expectedRows = new LinkedHashSet<>();
        long plannedEmissionMs;
        int distinctUsers;

        public List<String> getDataLines() {
            return dataLines;
        }

        public List<String> getPumpLines() {
            return pumpLines;
        }

        public Set<TxSummaryRow> getExpectedRows() {
            return expectedRows;
        }

        public long getPlannedEmissionMs() {
            return plannedEmissionMs;
        }

        public int getDistinctUsers() {
            return distinctUsers;
        }

        public long getTotalLineCount() {
            return dataLines.size() + pumpLines.size();
        }
    }

    public static final class S1LoadPlan {
        final List<Map<String, Object>> events = new ArrayList<>();
        final Set<AlertSummaryRow> expectedAlerts = new LinkedHashSet<>();
        long plannedEmissionMs;
        int burstWindows;

        public List<Map<String, Object>> getEvents() {
            return events;
        }

        public Set<AlertSummaryRow> getExpectedAlerts() {
            return expectedAlerts;
        }

        public long getPlannedEmissionMs() {
            return plannedEmissionMs;
        }

        public int getBurstWindows() {
            return burstWindows;
        }

        public long getTotalEventCount() {
            return events.size();
        }
    }

    /**
     * S2 load plan: {@code durationSec} of paced emission at one line per
     * {@code lineDelayMs}, a fresh keyed user every {@code linesPerUser} lines.
     */
    public static S2LoadPlan s2LoadPlan(long durationSec, long lineDelayMs, int linesPerUser) {
        if (durationSec < 1) {
            throw new IllegalArgumentException("durationSec must be >= 1 (got " + durationSec + ")");
        }
        if (lineDelayMs < 1) {
            throw new IllegalArgumentException("lineDelayMs must be >= 1 (got " + lineDelayMs + ")");
        }
        if (linesPerUser < 1) {
            throw new IllegalArgumentException("linesPerUser must be >= 1 (got " + linesPerUser + ")");
        }

        S2LoadPlan plan = new S2LoadPlan();
        long dataCount = durationSec * 1000L / lineDelayMs;
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (long i = 0; i < dataCount; i++) {
            int userIndex = (int) (i / linesPerUser);
            String userId = String.format("su-%04d", userIndex);
            long amount = 10L + (userIndex % 40);
            long ts = ScenarioTestSupport.T0 + i * lineDelayMs;
            plan.dataLines.add(userId + "," + amount + "," + ts);
            String key = userId + "|" + ScenarioTestSupport.windowStart(ts);
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            sums.merge(key, BigDecimal.valueOf(amount), BigDecimal::add);
        }
        plan.distinctUsers = (int) ((dataCount + linesPerUser - 1) / linesPerUser);
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            int sep = e.getKey().lastIndexOf('|');
            String userId = e.getKey().substring(0, sep);
            long windowStart = Long.parseLong(e.getKey().substring(sep + 1));
            plan.expectedRows.add(new TxSummaryRow(userId, windowStart,
                    windowStart + ScenarioTestSupport.WINDOW_SIZE_MS,
                    e.getValue()[0], sums.get(e.getKey())));
        }

        long lastDataTs = ScenarioTestSupport.T0 + (dataCount - 1) * lineDelayMs;
        long pumpAnchor = lastDataTs + PUMP_LEAD_MS;
        for (int j = 0; j < PUMP_COUNT; j++) {
            long ts = pumpAnchor + j * PUMP_EVENT_STEP_MS;
            plan.pumpLines.add("u-keep,10," + ts);
        }
        plan.plannedEmissionMs = plan.getTotalLineCount() * lineDelayMs;
        return plan;
    }

    /**
     * S1 load plan: {@code durationSec} of paced CDC emission at one event per
     * {@code emitDelayMs}. Per 10s window: burst user {@code sb-k} with exactly two
     * adjacent 1200-amount PURCHASE events (one RAPID alert) + same-city 50-amount
     * noise. {@code emitDelayMs} must divide 1000 evenly (burst offsets 1000/1100ms
     * must land on emission positions).
     */
    public static S1LoadPlan s1LoadPlan(long durationSec, long emitDelayMs) {
        if (durationSec < 1) {
            throw new IllegalArgumentException("durationSec must be >= 1 (got " + durationSec + ")");
        }
        if (emitDelayMs < 1 || 1000L % emitDelayMs != 0L) {
            throw new IllegalArgumentException("emitDelayMs must be >= 1 and divide 1000 evenly"
                    + " (burst offsets 1000/1100ms must be emission positions), got " + emitDelayMs);
        }

        S1LoadPlan plan = new S1LoadPlan();
        long totalEvents = durationSec * 1000L / emitDelayMs;
        long perWindow = ScenarioTestSupport.WINDOW_SIZE_MS / emitDelayMs;
        int burstPos1 = (int) (1000L / emitDelayMs);
        int burstPos2 = (int) (1100L / emitDelayMs);
        long windowCount = (totalEvents + perWindow - 1) / perWindow;
        int seq = 0;
        for (long i = 0; i < totalEvents; i++) {
            int windowIndex = (int) (i / perWindow);
            int pos = (int) (i % perWindow);
            long ts = ScenarioTestSupport.T0 + i * emitDelayMs;
            if (pos == burstPos1 || pos == burstPos2) {
                String user = String.format("sb-%04d", windowIndex);
                plan.events.add(CdcEventFixtures.spec("c", ts,
                        "tx-b-" + windowIndex + "-" + pos, user, "1200", "NYC", "PURCHASE"));
            } else {
                String user = String.format("nois-%04d", windowIndex);
                plan.events.add(CdcEventFixtures.spec("c", ts,
                        "tx-n-" + seq++, user, "50", "NYC", "PURCHASE"));
            }
        }
        for (int k = 0; k < windowCount; k++) {
            long burstTs = ScenarioTestSupport.T0 + k * ScenarioTestSupport.WINDOW_SIZE_MS + 1000L;
            long start = ScenarioTestSupport.windowStart(burstTs);
            plan.expectedAlerts.add(new AlertSummaryRow(start, start + ScenarioTestSupport.WINDOW_SIZE_MS,
                    String.format("sb-%04d", k), ScenarioTestSupport.PATTERN_RAPID, 1L,
                    new BigDecimal("2400")));
        }

        long lastDataTs = ScenarioTestSupport.T0 + (totalEvents - 1) * emitDelayMs;
        long pumpAnchor = lastDataTs + PUMP_LEAD_MS;
        for (int j = 0; j < PUMP_COUNT; j++) {
            long ts = pumpAnchor + j * PUMP_EVENT_STEP_MS;
            plan.events.add(CdcEventFixtures.spec("c", ts,
                    "tx-pump-" + j, "frank", "50", "NYC", "PURCHASE"));
        }
        plan.burstWindows = (int) windowCount;
        plan.plannedEmissionMs = plan.getTotalEventCount() * emitDelayMs;
        return plan;
    }

    /** Sorted view of the S2 expected rows (stable report rendering). */
    public static Map<String, TxSummaryRow> sortedByKey(Set<TxSummaryRow> rows) {
        Map<String, TxSummaryRow> sorted = new TreeMap<>();
        for (TxSummaryRow row : rows) {
            sorted.put(row.getUserId() + "|" + row.getWindowStart(), row);
        }
        return sorted;
    }
}
