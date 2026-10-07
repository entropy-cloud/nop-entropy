package io.nop.job.core.trigger;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * plan 537（cron 轨道 C3）：经 TriggerBuilder 全链路消费黄金向量 repeat/range 节。
 * fireCount 桩播种规则见 plan 537 Test Strategy：countEpoch（range.start ?? 1970-01-01）起暖机计数。
 */
public class TestAnchorRepeatTrigger {
    private static TimeZone originalTz;
    private static Map<String, Object> vectors;

    @BeforeAll
    static void setUp() throws Exception {
        originalTz = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try (InputStream in = TestAnchorRepeatTrigger.class.getResourceAsStream("/cron/golden-vectors.json")) {
            assertTrue(in != null, "golden-vectors.json missing");
            vectors = JsonTool.parseMap(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AfterAll
    static void tearDown() {
        TimeZone.setDefault(originalTz);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    private static String iso(long millis) {
        return Instant.ofEpochMilli(millis).toString();
    }

    private static long toMillis(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    private static long localMidnight(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * eval context 桩：范围三元组经 context（而非 spec）供 LimitTime/LimitCount 消费（对齐 TriggerSpecHelper.toEvalContext）；
     * fireCount 由播种与受理递增（对齐 LocalJobScheduler:349）。
     */
    private static final class CountingEvalContext implements ITriggerEvalContext {
        long fireCount;
        long minScheduleTime;
        long maxScheduleTime;
        long maxExecutionCount;

        @Override
        public long getFireCount() {
            return fireCount;
        }

        @Override
        public long getLastScheduledTime() {
            return -1L;
        }

        @Override
        public long getLastEndTime() {
            return -1L;
        }

        @Override
        public long getMinScheduleTime() {
            return minScheduleTime;
        }

        @Override
        public long getMaxScheduleTime() {
            return maxScheduleTime;
        }

        @Override
        public long getMaxExecutionCount() {
            return maxExecutionCount;
        }

        @Override
        public boolean isScheduleCompleted() {
            return false;
        }
    }

    /**
     * 桩 fireCount 播种：模拟任务自 countEpoch 存活，[countEpoch, after) 内受理的 fire 计入 fireCount。
     */
    private static long seedFireCount(ITrigger trigger, CountingEvalContext ctx, long countEpoch, long afterMs) {
        ctx.fireCount = 0;
        long t = countEpoch - 1;
        int guard = 0;
        while (guard++ < 100000) {
            t = trigger.nextScheduleTime(t, ctx);
            if (t < 0 || t >= afterMs)
                break;
            ctx.fireCount++;
        }
        return ctx.fireCount;
    }

    /**
     * 向量 → TriggerSpec 装配（RP-06 式 anchor 回退发生在装配层，TriggerBuilder 保持严格）。
     */
    private static ITrigger buildFromVector(Map<String, Object> v) {
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr((String) v.get("cron"));
        Map<String, Object> repeat = v.get("repeat") == null ? null : map(v.get("repeat"));
        Map<String, Object> range = v.get("range") == null ? null : map(v.get("range"));
        if (repeat != null) {
            spec.setRepeatAnchorEvery(((Number) repeat.get("every")).intValue());
            spec.setRepeatAnchorUnit((String) repeat.get("unit"));
        }
        String anchorDate = repeat == null ? null : (String) repeat.get("anchor");
        if (anchorDate == null && range != null)
            anchorDate = (String) range.get("start");
        if (repeat != null)
            spec.setRepeatAnchorDate(anchorDate);
        return TriggerBuilder.buildTrigger(spec, null);
    }

    /**
     * 范围三元组入 eval context（宿主拆列语义：spec 列 → 实体列 → context）。
     */
    private static CountingEvalContext newContext(Map<String, Object> v) {
        CountingEvalContext ctx = new CountingEvalContext();
        Map<String, Object> range = v.get("range") == null ? null : map(v.get("range"));
        if (range != null) {
            if (range.get("start") != null)
                ctx.minScheduleTime = localMidnight((String) range.get("start"));
            if (range.get("end") != null) {
                long endExclusive = LocalDate.parse((String) range.get("end")).plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                ctx.maxScheduleTime = endExclusive - 1;
            }
            if (range.get("count") != null)
                ctx.maxExecutionCount = ((Number) range.get("count")).longValue();
        }
        return ctx;
    }

    private static void assertSequence(Map<String, Object> v) {
        String id = (String) v.get("id");
        List<Object> expect = list(v.get("expectNext"));
        long afterMs = toMillis((String) v.get("after"));
        Map<String, Object> range = v.get("range") == null ? null : map(v.get("range"));
        long countEpoch = range != null && range.get("start") != null
                ? localMidnight((String) range.get("start"))
                : localMidnight("1970-01-01");

        ITrigger trigger = buildFromVector(v);
        CountingEvalContext ctx = newContext(v);
        seedFireCount(trigger, ctx, countEpoch, afterMs);

        long t = afterMs;
        for (Object expectIso : expect) {
            t = trigger.nextScheduleTime(t, ctx);
            assertTrue(t >= 0, id + ": expected " + expectIso + " but sequence terminated");
            assertEquals(expectIso, iso(t), id);
            ctx.fireCount++;
        }
        Object exhaustedAfter = v.get("expectExhaustedAfter");
        if (exhaustedAfter != null) {
            long r = trigger.nextScheduleTime(t, ctx);
            assertEquals(-1L, r, id + ": expected exhaustion after " + exhaustedAfter);
        }
    }

    @TestFactory
    Collection<DynamicTest> repeatVectors() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object entry : list(vectors.get("repeat"))) {
            Map<String, Object> v = map(entry);
            tests.add(dynamicTest(v.get("id") + " " + v.get("cron"), () -> assertSequence(v)));
        }
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> rangeVectors() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object entry : list(vectors.get("range"))) {
            Map<String, Object> v = map(entry);
            tests.add(dynamicTest(v.get("id") + " " + v.get("cron"), () -> assertSequence(v)));
        }
        return tests;
    }

    // --- 手写判别用例（向量外，plan 537 Phase 1） ---

    @Test
    void anchor_withNonCronTrigger_rejected() {
        TriggerSpec spec = new TriggerSpec();
        spec.setRepeatInterval(1000L);
        spec.setRepeatAnchorEvery(2);
        spec.setRepeatAnchorUnit("day");
        spec.setRepeatAnchorDate("2026-01-01");
        assertThrows(NopException.class, () -> TriggerBuilder.buildTrigger(spec, null));
    }

    @Test
    void anchor_invalidParams_rejected() {
        TriggerSpec badUnit = new TriggerSpec();
        badUnit.setCronExpr("0 0 9 * * ?");
        badUnit.setRepeatAnchorEvery(2);
        badUnit.setRepeatAnchorUnit("month");
        badUnit.setRepeatAnchorDate("2026-01-01");
        assertThrows(NopException.class, () -> TriggerBuilder.buildTrigger(badUnit, null));

        TriggerSpec badEvery = new TriggerSpec();
        badEvery.setCronExpr("0 0 9 * * ?");
        badEvery.setRepeatAnchorEvery(0);
        badEvery.setRepeatAnchorUnit("day");
        badEvery.setRepeatAnchorDate("2026-01-01");
        assertThrows(NopException.class, () -> TriggerBuilder.buildTrigger(badEvery, null));

        TriggerSpec missingDate = new TriggerSpec();
        missingDate.setCronExpr("0 0 9 * * ?");
        missingDate.setRepeatAnchorEvery(2);
        missingDate.setRepeatAnchorUnit("day");
        assertThrows(NopException.class, () -> TriggerBuilder.buildTrigger(missingDate, null));

        TriggerSpec badDate = new TriggerSpec();
        badDate.setCronExpr("0 0 9 * * ?");
        badDate.setRepeatAnchorEvery(2);
        badDate.setRepeatAnchorUnit("day");
        badDate.setRepeatAnchorDate("2026-1-1");
        assertThrows(NopException.class, () -> TriggerBuilder.buildTrigger(badDate, null));
    }

    @Test
    void anchor_neverMatches_returnsMinusOneAfterScanCap() {
        // 周一 cron + 周日锚点每 7 天：周一差值恒 ≡1 (mod 7)，永不过滤命中 → 扫描上限后 -1
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr("0 0 0 ? * MON");
        spec.setRepeatAnchorEvery(7);
        spec.setRepeatAnchorUnit("day");
        spec.setRepeatAnchorDate("2026-01-04");
        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        CountingEvalContext ctx = new CountingEvalContext();
        long r = trigger.nextScheduleTime(toMillis("2026-01-01T00:00:00Z"), ctx);
        assertEquals(-1L, r);
    }
}
