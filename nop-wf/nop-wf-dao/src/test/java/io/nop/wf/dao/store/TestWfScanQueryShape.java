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
 */
public class TestWfScanQueryShape {
    @Test
    public void testDueScanQuery_hasLimitOrderByAndCursorFilter() {
        DaoWorkflowStore store = new DaoWorkflowStore();

        // 无游标（首轮）
        QueryBean first = store.buildDueScanQuery(500, null);
        assertEquals(500, first.getLimit());
        assertNotNull(first.getFilter());
        assertNotNull(first.getOrderBy());
        assertEquals(1, first.getOrderBy().size());
        assertEquals("dueTime", first.getOrderBy().get(0).getName());
        assertFalse(first.getOrderBy().get(0).isDesc());

        // 带游标（分批推进）：谓词树包含严格大于游标的下界条件
        QueryBean next = store.buildDueScanQuery(500, new Timestamp(1_000L));
        assertEquals(500, next.getLimit());
        assertNotNull(next.getFilter());
        assertEquals("dueTime", next.getOrderBy().get(0).getName());
        assertTrue(containsFilterName(next.getFilter(), "dueTime"));
    }

    @Test
    public void testRemindScanQuery_sameShapeAsDue() {
        DaoWorkflowStore store = new DaoWorkflowStore();
        QueryBean query = store.buildRemindScanQuery(500, null);
        assertEquals(500, query.getLimit());
        assertNotNull(query.getFilter());
        assertEquals("remindTime", query.getOrderBy().get(0).getName());
        assertFalse(query.getOrderBy().get(0).isDesc());
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
}
