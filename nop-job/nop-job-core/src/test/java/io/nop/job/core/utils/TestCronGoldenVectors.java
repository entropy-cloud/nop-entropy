package io.nop.job.core.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * plan 536（cron 轨道 C2）：消费跨仓黄金向量集（权威副本见 nop-chaos-flux
 * docs/components/cron-editor/golden-vectors.json，C7 做哈希对账）。
 * repeat/range 节由 C3（TriggerSpec 锚点/范围字段）承接，本测试跳过。
 */
public class TestCronGoldenVectors {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static Map<String, Object> vectors;

    @BeforeAll
    static void loadVectors() throws Exception {
        try (InputStream in = TestCronGoldenVectors.class.getResourceAsStream("/cron/golden-vectors.json")) {
            assertTrue(in != null, "golden-vectors.json missing from test resources");
            vectors = JsonTool.parseMap(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
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

    @TestFactory
    Collection<DynamicTest> nextRunVectors() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object entry : list(vectors.get("nextRun"))) {
            Map<String, Object> v = map(entry);
            String id = (String) v.get("id");
            String expr = (String) v.get("expr");
            String after = (String) v.get("after");
            List<Object> expect = list(v.get("expectNext"));
            tests.add(dynamicTest(id + " " + expr, () -> {
                CronExpression cron = new CronExpression(expr, UTC);
                long t = Instant.parse(after).toEpochMilli();
                for (Object expectIso : expect) {
                    t = cron.getTimeAfter(t);
                    assertEquals(expectIso, iso(t), id + " after " + after);
                }
            }));
        }
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> diagnosticVectors() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object entry : list(vectors.get("diagnostics"))) {
            Map<String, Object> v = map(entry);
            String id = (String) v.get("id");
            String expr = (String) v.get("expr");
            boolean javaValid = Boolean.TRUE.equals(v.get("javaValid"));
            Object runaway = v.get("javaNextRun");
            tests.add(dynamicTest(id + " " + expr, () -> {
                assertEquals(javaValid, CronExpression.isValidExpression(expr), id + " isValidExpression");
                if (runaway != null) {
                    // tsCode=impossible 的两端不对称条目：构造成功、运行期 runaway
                    CronExpression cron = new CronExpression(expr, UTC);
                    assertThrows(NopException.class, () -> cron.getTimeAfter(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()),
                            id + " expected runaway");
                }
            }));
        }
        return tests;
    }

    // --- 手写判别用例（向量外，钉实现裁定，见 plan 536 Phase 1） ---

    @Test
    void yearField_farJump_rebasesRunawayBudget() {
        // 年段跳距 > 4 年 + 跳后月滚动：dot 重基裁定下正解可达；沿旧 dot 的实现会在首个滚动月误触 runaway
        CronExpression cron = new CronExpression("0 0 0 1 12 ? 2040", UTC);
        long t = cron.getTimeAfter(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli());
        assertEquals("2040-12-01T00:00:00Z", iso(t));
    }

    @Test
    void yearField_exhausted_throwsRunaway() {
        // 年段全部年份已过：getTimeAfter 抛 runaway 同款 NopException
        CronExpression cron = new CronExpression("0 0 0 1 1 ? 2020", UTC);
        assertThrows(NopException.class, () -> cron.getTimeAfter(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli()));
    }

    @Test
    void isValidExpression_neverThrowsOnMalformedTokens() {
        // 既有缺陷钉子：非数字 token 曾触发 NumberFormatException 直接逃逸
        assertFalse(CronExpression.isValidExpression("L 0 0 * * *"));
        assertFalse(CronExpression.isValidExpression("0 0 0 * XZY *"));
        assertFalse(CronExpression.isValidExpression(null));
    }
}
