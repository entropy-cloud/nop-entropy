
package io.nop.metadata.service.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * AR-10（plan 2026-08-06-0553-3 Phase 2）：MySQL quarter/week 粒度分桶模板语义正确性。
 *
 * <p>测试基建限制：本模块测试为 H2-only，无法对 MySQL 模板做语义执行断言——沿
 * {@code TestSqlPaginationOffsetOnly}（SqlPagination.java）先例做**模板字符串断言**：
 * <ul>
 *   <li>MySQL quarter 桶 = 季度首日 00:00:00（修复前为月首 {@code %Y-%m-01}，与 month 逐字节相同）</li>
 *   <li>MySQL week 桶 = ISO 周语义周一 00:00:00（修复前与 day 逐字节相同）</li>
 *   <li>H2/PG 的 DATE_TRUNC 模板不回归</li>
 * </ul>
 */
public class TestGranularityBucketingTemplates {

    private static final String MYSQL_QUARTER_EXPECTED =
            "CONCAT(YEAR(TS),'-',LPAD((QUARTER(TS)*3-2),2,'0'),'-01 00:00:00')";
    private static final String MYSQL_WEEK_EXPECTED =
            "DATE_FORMAT(DATE_SUB(TS, INTERVAL WEEKDAY(TS) DAY), '%Y-%m-%d 00:00:00')";

    @Test
    public void testMySqlQuarterIsQuarterStart() {
        String tpl = GranularityBucketing.translate("quarter", "TS", "MySQL", "q");
        assertEquals(MYSQL_QUARTER_EXPECTED, tpl,
                "MySQL quarter bucket must be quarter-first-day (fix before: month-first %Y-%m-01): " + tpl);
        // 判别性：quarter 与 month 模板必须不同（修复前逐字节相同）
        String month = GranularityBucketing.translate("month", "TS", "MySQL", "m");
        assertNotEquals(month, tpl, "MySQL quarter and month templates must differ");
    }

    @Test
    public void testMySqlQuarterExpressionYieldsQuarterFirstDay() {
        // 表达式正确性核对：QUARTER(TS)∈{1,2,3,4} → *3-2 → {1,4,7,10} → 各季度首月
        assertEquals("01", String.format("%02d", 1 * 3 - 2));
        assertEquals("04", String.format("%02d", 2 * 3 - 2));
        assertEquals("07", String.format("%02d", 3 * 3 - 2));
        assertEquals("10", String.format("%02d", 4 * 3 - 2));
        String tpl = GranularityBucketing.translate("quarter", "TS", "MySQL", "q");
        // 语义核对：QUARTER 映射表达式（*3-2 把 1..4 → 1,4,7,10 季度首月）+ 首日 00:00:00 字面量
        assertEquals(true, tpl.contains("QUARTER(TS)*3-2"),
                "quarter template must map QUARTER() to quarter-first-month: " + tpl);
        assertEquals(true, tpl.contains("'-01 00:00:00'"),
                "quarter template must end with quarter-first-day 00:00:00: " + tpl);
    }

    @Test
    public void testMySqlWeekIsIsoWeekMondayMidnight() {
        String tpl = GranularityBucketing.translate("week", "TS", "MySQL", "w");
        assertEquals(MYSQL_WEEK_EXPECTED, tpl,
                "MySQL week bucket must be ISO-week Monday 00:00:00 (fix before: day-level %Y-%m-%d): " + tpl);
        // 判别性：week 与 day 模板必须不同（修复前逐字节相同）
        String day = GranularityBucketing.translate("day", "TS", "MySQL", "d");
        assertNotEquals(day, tpl, "MySQL week and day templates must differ");
        // 必须含午夜截断（H2/PG DATE_TRUNC('week') 产出周一 00:00:00；若保留原时间分量同周不同时刻
        // 两行在 MySQL 分两桶、H2/PG 一桶，仍属静默错桶）
        assertEquals(true, tpl.contains("00:00:00"), "week template must truncate to midnight: " + tpl);
        // %x-%v 产出 "2026-33" 周数字符串而非日期，不得直接作为 bucket 键
        assertEquals(false, tpl.contains("%x"), "week template must not use %x-%v week-number as bucket key: " + tpl);
        assertEquals(false, tpl.contains("%v"), "week template must not use %x-%v week-number as bucket key: " + tpl);
    }

    @Test
    public void testH2PostgresTemplatesUnchanged() {
        assertEquals("DATE_TRUNC('quarter',TS)", GranularityBucketing.translate("quarter", "TS", "H2", "q"));
        assertEquals("DATE_TRUNC('week',TS)", GranularityBucketing.translate("week", "TS", "H2", "w"));
        assertEquals("DATE_TRUNC('quarter',TS)", GranularityBucketing.translate("quarter", "TS", "PostgreSQL", "q"));
        assertEquals("DATE_TRUNC('week',TS)", GranularityBucketing.translate("week", "TS", "PostgreSQL", "w"));
    }
}
