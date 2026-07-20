/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
 *   <li>类型不一致（Integer vs Long vs BigDecimal）显式抛 ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH</li>
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

    @Test
    public void testTypeMismatchIntegerVsLongThrowsExplicitly() {
        NopMetaTableJoin join = newJoin("id", "id", "inner");
        List<Map<String, Object>> left = rows(row("id", 1, "name", "L_int"));
        List<Map<String, Object>> right = rows(row("id", 1L, "extra", "R_long"));

        NopException ex = assertThrows(NopException.class,
                () -> invokeCrossDbMerge(join, left, right),
                "Integer vs Long key must explicitly fail (silent String coercion is forbidden)");
        assertEquals(NopMetadataErrors.ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH.getErrorCode(),
                ex.getErrorCode(),
                "must throw ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH");
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
