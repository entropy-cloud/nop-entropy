
package io.nop.metadata.service.quality;

import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-15（R8.1）判别性测试：freshness 负年龄（未来时间戳/DB 时钟超前）不再恒 PASS——
 * 裁定为 fail-loud 语义（负年龄 = 时钟偏移异常，显式 FAIL + details 暴露原始差值）。
 *
 * <p>用真实 H2 连接 + 真实时间戳数据走 {@link MetaQualityRuleExecutor#judge} 公开入口
 * （沿 TestMetaQualityCheckpointScheduler.seedTable 先例），非 mock 判定。
 */
public class TestMetaQualityRuleExecutorFreshness {

    private static final String DB_URL = "jdbc:h2:mem:meta_fresh_r81;DB_CLOSE_DELAY=-1";

    @Test
    public void testFutureTimestampFailsWithRawAgeExposed() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "sa", "")) {
            createTable(c);
            Timestamp future = new Timestamp(System.currentTimeMillis() + 2 * 3600_000L);
            insert(c, future);

            QualityRuleJudgment j = judgeFreshness(c, "{\"timestampColumn\":\"TS\",\"maxAgeMinutes\":60}");

            // 修复前 ageMinutes 为负 → ageMinutes > maxAgeMinutes 恒 false → 无条件 PASS（red）
            assertEquals("FAIL", j.getStatus(),
                    "future timestamp must FAIL (was PASS before AR-15: negative age never exceeds maxAgeMinutes)");
            // details 暴露原始差值（负值）
            Object raw = j.getDetails().get("rawAgeMinutes");
            assertTrue(raw instanceof Number && ((Number) raw).doubleValue() < 0,
                    "details must expose negative rawAgeMinutes (clock-skew evidence), got: " + raw);
            // 消息可诊断：时钟偏移提示
            assertTrue(j.getMessage().contains("in the future"),
                    "message must surface clock-skew diagnosis, got: " + j.getMessage());
            // actualValue 即原始差值（显式 FAIL 不伪造 0）
            assertEquals(((Number) raw).doubleValue(), j.getActualValue(), 0.001,
                    "actualValue must be the raw (negative) age minutes");
        }
    }

    @Test
    public void testFutureTimestampFailsEvenWithoutMaxAgeMinutes() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "sa", "")) {
            createTable(c);
            Timestamp future = new Timestamp(System.currentTimeMillis() + 30 * 60_000L);
            insert(c, future);

            QualityRuleJudgment j = judgeFreshness(c, "{\"timestampColumn\":\"TS\"}");

            // 无 maxAgeMinutes 时旧逻辑 pass=true（负值不触发任何 FAIL 分支）——负年龄本身即违约
            assertEquals("FAIL", j.getStatus(),
                    "future timestamp must FAIL even without maxAgeMinutes (clock skew is a violation per se)");
            assertTrue(((Number) j.getDetails().get("rawAgeMinutes")).doubleValue() < 0,
                    "rawAgeMinutes must be negative, got: " + j.getDetails().get("rawAgeMinutes"));
        }
    }

    /** 正路径回归：过去时间戳且在 maxAgeMinutes 内 → PASS；超过 maxAgeMinutes → FAIL（既有语义保持）。 */
    @Test
    public void testPastTimestampNormalSemanticsKept() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "sa", "")) {
            createTable(c);
            insert(c, new Timestamp(System.currentTimeMillis() - 10 * 60_000L)); // 10 min ago

            QualityRuleJudgment pass = judgeFreshness(c, "{\"timestampColumn\":\"TS\",\"maxAgeMinutes\":60}");
            assertEquals("PASS", pass.getStatus(),
                    "10-min-old timestamp within maxAgeMinutes=60 must PASS (no regression)");
            assertTrue(((Number) pass.getDetails().get("rawAgeMinutes")).doubleValue() >= 0,
                    "rawAgeMinutes must be non-negative for past timestamp");

            QualityRuleJudgment fail = judgeFreshness(c, "{\"timestampColumn\":\"TS\",\"maxAgeMinutes\":5}");
            assertEquals("FAIL", fail.getStatus(),
                    "10-min-old timestamp exceeding maxAgeMinutes=5 must FAIL (existing semantics kept)");
        }
    }

    private static void createTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS T_FRESH (ID INT NOT NULL, TS TIMESTAMP)");
            st.execute("DELETE FROM T_FRESH");
        }
    }

    private static void insert(Connection c, Timestamp ts) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO T_FRESH VALUES (1, ?)")) {
            ps.setTimestamp(1, ts);
            ps.executeUpdate();
        }
    }

    private static QualityRuleJudgment judgeFreshness(Connection c, String paramsJson) {
        TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-fresh",
                "T_FRESH", null, null, null, null, null);
        MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
        return executor.judge(c, ref, null, "freshness", "table", paramsJson, null, null, "H2");
    }
}
