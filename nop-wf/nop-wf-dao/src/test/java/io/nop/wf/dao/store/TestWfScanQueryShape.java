package io.nop.wf.dao.store;

import io.nop.api.core.beans.query.QueryBean;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2514 C10-008 查询形状断言（MA-05 裁定④ R02 分批范式）：
 * due/remind 扫描查询必须携带 limit（批次上限）+ orderBy（扫描时间升序）+ 游标谓词，
 * 消费侧据此分页推进、无全量物化。
 *
 * <p>plan 2585 Phase 2：游标升级 (time, stepId) 二元组——orderBy 补 stepId tie-breaker
 * （游标序与返回序一致），游标谓词形状 ((eq time and gt stepId) or gt time)。
 */
public class TestWfScanQueryShape {
    @Test
    public void testDueScanQuery_hasLimitOrderByAndCursorFilter() {
        DaoWorkflowStore store = new DaoWorkflowStore();

        // 无游标（首轮）
        QueryBean first = store.buildDueScanQuery(500, null, null);
        assertEquals(500, first.getLimit());
        assertNotNull(first.getFilter());
        assertNotNull(first.getOrderBy());
        // plan 2585：orderBy = (dueTime asc, stepId asc) 二元组 tie-breaker
        assertEquals(2, first.getOrderBy().size());
        assertEquals("dueTime", first.getOrderBy().get(0).getName());
        assertFalse(first.getOrderBy().get(0).isDesc());
        assertEquals("stepId", first.getOrderBy().get(1).getName());
        assertFalse(first.getOrderBy().get(1).isDesc());

        // 带游标（分批推进）：谓词树包含二元组游标下界条件（dueTime + stepId）
        QueryBean next = store.buildDueScanQuery(500, new Timestamp(1_000L), "step-001");
        assertEquals(500, next.getLimit());
        assertNotNull(next.getFilter());
        assertEquals("dueTime", next.getOrderBy().get(0).getName());
        assertTrue(containsFilterName(next.getFilter(), "dueTime"));
        assertTrue(containsFilterName(next.getFilter(), "stepId"));
        // 二元组谓词形状：or( and(eq dueTime, gt stepId), gt dueTime )
        String flat = flattenFilter(next.getFilter());
        assertTrue(flat.contains("or"), "tuple cursor predicate must be an or-tree");
        assertTrue(flat.contains("and"), "tuple cursor predicate must contain the eq+gt conjunction");
    }

    @Test
    public void testRemindScanQuery_sameShapeAsDue() {
        DaoWorkflowStore store = new DaoWorkflowStore();
        QueryBean query = store.buildRemindScanQuery(500, null, null);
        assertEquals(500, query.getLimit());
        assertNotNull(query.getFilter());
        assertEquals("remindTime", query.getOrderBy().get(0).getName());
        assertFalse(query.getOrderBy().get(0).isDesc());
        assertEquals("stepId", query.getOrderBy().get(1).getName(),
                "plan 2585: remind half must carry the same stepId tie-breaker");
    }

    private static boolean containsFilterName(io.nop.api.core.beans.TreeBean filter, String name) {
        if (filter == null)
            return false;
        if (name.equals(filter.getAttr("name")))
            return true;
        if (filter.getChildren() != null) {
            for (io.nop.api.core.beans.TreeBean child : filter.getChildren()) {
                if (containsFilterName(child, name))
                    return true;
            }
        }
        return false;
    }

    private static String flattenFilter(io.nop.api.core.beans.TreeBean node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(io.nop.api.core.beans.TreeBean node, StringBuilder sb) {
        if (node == null)
            return;
        sb.append(node.getTagName()).append(' ');
        if (node.getChildren() != null) {
            for (io.nop.api.core.beans.TreeBean child : node.getChildren()) {
                collect(child, sb);
            }
        }
    }
}
