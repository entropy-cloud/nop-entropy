package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 CrossDbJoinMerger.crossDbMerge 的 NULL 语义（plan 1250-2 Phase 4 Proof）。
 *
 * <p>AR-05 修复前：crossDbMerge 用 String.valueOf(null) = "null" 作为 join key 建索引，
 * 导致两侧 null 行被错配为 "null" = "null"，违反 SQL NULL != NULL 语义。
 *
 * <p>本测试直接调用 CrossDbJoinMerger.crossDbMerge 方法（纯合并逻辑，无 ORM 依赖），
 * 直接断言：
 * <ul>
 *   <li>左 null + 右 null：inner join 不匹配（不输出），left join 保留左行（右列 null）</li>
 *   <li>右 null：不被任何左行匹配（不进 rightIndex）</li>
 *   <li>左 null + 右非 null：不匹配（inner 丢弃，left 保留）</li>
 *   <li>整型族跨类型（Integer vs Long / Byte vs Integer 等）数值等值键匹配通过（AR-20b re-adjudication，
 *       stringKey 匹配下整型等值键必然同串，精确类比较为过度防护）</li>
 *   <li>非整型不匹配（Integer vs BigDecimal / Integer vs String）仍显式抛
 *       ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH（避免静默精度失配/错配）</li>
 * </ul>
 */
public class TestMetaJoinCrossDbMergeNullSemantics {

    private final CrossDbJoinMerger merger = new CrossDbJoinMerger();

    @Test
    public void testNullKeyDoesNotMatchNullKeyInnerJoin() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", null, "name", "L1"));
        List<Map<String, Object>> right = rows(row("id", null, "extra", "R1"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);

        assertTrue(merged.isEmpty(),
                "inner join with NULL=NULL must NOT match (SQL standard); got: " + merged);
    }

    @Test
    public void testNullKeyLeftJoinRetainsLeftRowWithNullRight() {
        NopMetaTableJoin join = newJoin("id", "id", "left");
        List<Map<String, Object>> left = rows(row("id", null, "name", "L1"));
        List<Map<String, Object>> right = rows(row("id", null, "extra", "R1"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);

        assertEquals(1, merged.size(), "left join must retain left row when key is NULL");
        Map<String, Object> row = merged.get(0);
        assertEquals("L1", row.get("name"), "left column must be preserved");
        assertFalse(row.containsKey("extra"),
                "right column must not appear when no match (right=null suppressed)");
    }

    @Test
    public void testRightNullNotMatchedByAnyLeft() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(
                row("id", "K1", "name", "L1"),
                row("id", "K2", "name", "L2"));
        List<Map<String, Object>> right = rows(
                row("id", "K1", "extra", "R1"),
                row("id", null, "extra", "R_NULL"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);

        assertEquals(1, merged.size(), "only K1=K1 should match; right NULL must not be matched");
        assertEquals("L1", merged.get(0).get("name"));
        assertEquals("R1", merged.get(0).get("extra"));
    }

    /**
     * AR-20b re-adjudication（plan 2026-08-06-1228-1 Phase 2）：Integer vs Long 整型等值键由"显式拒绝"
     * 改为"匹配通过"——merge 匹配走 stringKey（String.valueOf），Long.toString(1)="1"=Integer.toString(1)，
     * 数值等值键必然同串，精确类比较（Class.equals）是过度防护（INT vs BIGINT 跨库 join 数值相等被误拒）；
     * 旧裁定（"silent String coercion is forbidden"）的 coercion 担忧仅对非整型成立。
     */
    @Test
    public void testIntegerVsLongKeysMatchNumerically() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", 1, "name", "L_int"));
        List<Map<String, Object>> right = rows(row("id", 1L, "extra", "R_long"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);
        assertEquals(1, merged.size(),
                "Integer 1 vs Long 1 are numerically equal integer-family keys and must match: " + merged);
        assertEquals("R_long", merged.get(0).get("extra"));
    }

    /** 整型族内其它跨类型组合（Byte vs Integer / Short vs Long）同样数值等值匹配（AR-20b）。 */
    @Test
    public void testOtherIntegerFamilyCrossTypeMatches() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", (byte) 1, "name", "L_byte"));
        List<Map<String, Object>> right = rows(row("id", 1, "extra", "R_int"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);
        assertEquals(1, merged.size(), "Byte 1 vs Integer 1 must match: " + merged);

        NopMetaTableJoin join2 = newJoin("id", "id", "inner");
        List<Map<String, Object>> left2 = rows(row("id", (short) 2, "name", "L_short"));
        List<Map<String, Object>> right2 = rows(row("id", 2L, "extra", "R_long"));
        List<Map<String, Object>> merged2 = invokeCrossDbMerge(join2, left2, right2);
        assertEquals(1, merged2.size(), "Short 2 vs Long 2 must match: " + merged2);
    }

    /** 单列内整型族混型（Integer + Long）→ 兼容不抛（AR-20b）。 */
    @Test
    public void testMixedIntegerFamilyWithinColumnMatches() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(
                row("id", 1, "name", "L1"),
                row("id", 2L, "name", "L2"));
        List<Map<String, Object>> right = rows(
                row("id", 1L, "extra", "R1"),
                row("id", 2, "extra", "R2"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);
        assertEquals(2, merged.size(),
                "mixed Integer/Long keys within a column are compatible and must both match: " + merged);
    }

    /** 非整型不匹配（Integer vs BigDecimal）维持拒绝——BigDecimal("1.0")="1.0" vs Integer 1="1" 数值等但不同串，放宽会静默失配。 */
    @Test
    public void testTypeMismatchIntegerVsBigDecimalStillThrows() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", 1, "name", "L_int"));
        List<Map<String, Object>> right = rows(row("id", new java.math.BigDecimal("1.0"), "extra", "R_dec"));

        NopException ex = assertThrows(NopException.class,
                () -> invokeCrossDbMerge(join, left, right),
                "Integer vs BigDecimal key must still explicitly fail (silent precision loss is forbidden)");
        assertEquals(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testTypeMismatchIntegerVsStringThrowsExplicitly() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", 1, "name", "L_int"));
        List<Map<String, Object>> right = rows(row("id", "1", "extra", "R_str"));

        NopException ex = assertThrows(NopException.class,
                () -> invokeCrossDbMerge(join, left, right),
                "Integer vs String key must explicitly fail");
        assertEquals(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    public void testSameIntegerFamilyMatches() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", 1, "name", "L"));
        List<Map<String, Object>> right = rows(row("id", 1, "extra", "R"));

        List<Map<String, Object>> merged = invokeCrossDbMerge(join, left, right);
        assertEquals(1, merged.size(), "same-type integer keys must match");
        assertEquals("R", merged.get(0).get("extra"));
    }

    /**
     * MA7.4-04：合并产物（笛卡尔积）必须有上限——两侧各 ≤ maxCrossDbRows 时，
     * 高基数重复 join 键的乘积可到 maxCrossDbRows^2 行入内存。构造小上限 merger，
     * 用重复键驱动乘积超限，断言 ERR_JOIN_CROSS_DB_SIZE_LIMIT（side=merged）。
     */
    @Test
    public void testMergedProductOverflowThrowsExplicitly() {
        CrossDbJoinMerger capped = new CrossDbJoinMerger(3);
        NopMetaTableJoin join = newJoin("id", "id", "inner");

        List<Map<String, Object>> left = rows(
                row("id", "K", "name", "L1"),
                row("id", "K", "name", "L2"));
        List<Map<String, Object>> right = rows(
                row("id", "K", "extra", "R1"),
                row("id", "K", "extra", "R2"),
                row("id", "K", "extra", "R3"));

        NopException ex = assertThrows(NopException.class,
                () -> capped.crossDbMerge(join, left, right, null, null),
                "merged product (2x3=6 > 3) must fail fast with ERR_JOIN_CROSS_DB_SIZE_LIMIT");
        assertEquals(NopMetadataErrors.ERR_JOIN_CROSS_DB_SIZE_LIMIT.getErrorCode(),
                ex.getErrorCode(), "merged-side overflow must throw ERR_JOIN_CROSS_DB_SIZE_LIMIT");
        assertEquals("merged", ex.getParam("side"), "overflow side must be reported as merged");
    }

    @Test
    public void testMergedProductWithinLimitStillWorks() {
        CrossDbJoinMerger capped = new CrossDbJoinMerger(10);
        NopMetaTableJoin join = newJoin("id", "id", "inner");

        List<Map<String, Object>> left = rows(
                row("id", "K", "name", "L1"),
                row("id", "K", "name", "L2"));
        List<Map<String, Object>> right = rows(
                row("id", "K", "extra", "R1"),
                row("id", "K", "extra", "R2"));

        List<Map<String, Object>> merged = capped.crossDbMerge(join, left, right, null, null);
        assertEquals(4, merged.size(), "2x2=4 within limit 10 must succeed");
    }

    // ============================ helpers ============================

    private List<Map<String, Object>> invokeCrossDbMerge(NopMetaTableJoin join,
                                                          List<Map<String, Object>> left,
                                                          List<Map<String, Object>> right) {
        return merger.crossDbMerge(join, left, right, null, null);
    }

    private static NopMetaTableJoin newJoin(String leftField, String rightField, String joinType) {
        NopMetaTableJoin join = new NopMetaTableJoin();
        join.setJoinId("test-join");
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setJoinType(joinType);
        return join;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Map<String, Object>> rows(Map<String, Object>... rs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rs) {
            list.add(new LinkedHashMap<>(r));
        }
        return list;
    }
}
