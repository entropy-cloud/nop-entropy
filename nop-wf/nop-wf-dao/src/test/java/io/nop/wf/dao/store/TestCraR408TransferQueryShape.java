package io.nop.wf.dao.store;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.48 R4-08（plan 2541）W-02（1.5-MA-07 裁定 D）transfer 分页查询形状钉——2514
 * C10-008 查询形状钉（TestWfScanQueryShape）同族载体：limit + orderBy(stepId asc) +
 * 游标谓词（stepId &gt; cursor）+ ownerId/status 过滤。结构钉形态（2537 红⑥ compile-red
 * 先例：四参查询构造器为修复面本体，pre-fix 不存在——编译红承载）。
 * 禁触边界：buildDueScanQuery/buildRemindScanQuery 形状零改写（2514 产物，由
 * TestWfScanQueryShape 既有钉零回退承载）。
 */
public class TestCraR408TransferQueryShape {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private DaoWorkflowStore newStore() {
        DaoWorkflowStore store = new DaoWorkflowStore();
        store.setDaoProvider((IDaoProvider) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{IDaoProvider.class},
                (proxy, method, args) -> null));
        store.setOrmTemplate((IOrmTemplate) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{IOrmTemplate.class},
                (proxy, method, args) -> null));
        return store;
    }

    @Test
    public void transferScanQuery_shape_pin() {
        DaoWorkflowStore store = newStore();
        QueryBean query = store.buildTransferScanQuery("u1", Set.of("wf-1", "wf-2"), 6, "step-009");

        assertEquals(6, query.getLimit(), "limit = batchSize (paged, no full materialization)");
        assertNotNull(query.getOrderBy(), "orderBy(stepId asc) is mandatory for cursor pagination");
        String orderByText = String.valueOf(query.getOrderBy());
        assertTrue(orderByText.contains("stepId"),
                "cursor ordering must be on the stable primary key stepId");
        // 游标谓词（严格大于）+ owner/status 过滤 + wfId in 过滤（结构树扁平化断言）
        String filterText = flattenFilter(query.getFilter());
        assertTrue(filterText.contains("gt"), "cursor predicate (gt stepId) must be present");
        assertTrue(filterText.contains("stepId"), "cursor predicate field must be stepId");
        assertTrue(filterText.contains("step-009"), "cursor value must be the given cursorStepId");
        assertTrue(filterText.contains("u1"), "ownerId filter must be present");
        assertTrue(filterText.contains("wf-1") && filterText.contains("wf-2"),
                "wfIds in-filter must be present when non-empty");
    }

    @Test
    public void transferScanQuery_noCursor_firstPage() {
        DaoWorkflowStore store = newStore();
        QueryBean query = store.buildTransferScanQuery("u1", null, 6, null);
        assertEquals(6, query.getLimit());
        assertTrue(String.valueOf(query.getOrderBy()).contains("stepId"));
        String filterText = flattenFilter(query.getFilter());
        assertTrue(filterText.contains("u1"));
        assertTrue(!filterText.contains("gt"),
                "first page (null cursor) must not carry the cursor predicate");
    }

    private String flattenFilter(io.nop.api.core.beans.TreeBean node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private void collect(io.nop.api.core.beans.TreeBean node, StringBuilder sb) {
        if (node == null)
            return;
        sb.append(node.getTagName()).append(' ');
        if (node.getAttrs() != null) {
            node.getAttrs().forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(c -> collect(c, sb));
        }
    }

    @Test
    public void dueRemindScanQueries_untouched() {
        // 禁触边界对照：2514 due/remind 查询形状与本批 transfer 形状独立——二者字段形态不同
        //（timeProp 游标 vs stepId 游标），证明未发生形状改写交叉。
        // plan 2585 Phase 2：timeProp 游标升级 (time, stepId) 二元组（orderBy 补 stepId
        // tie-breaker），本断言只锚定 timeProp 字段仍居首
        DaoWorkflowStore store = newStore();
        QueryBean due = store.buildDueScanQuery(100, null, null);
        assertTrue(String.valueOf(due.getOrderBy()).contains("dueTime"),
                "2514 due scan shape untouched (orderBy dueTime)");
        QueryBean remind = store.buildRemindScanQuery(100, null, null);
        assertTrue(String.valueOf(remind.getOrderBy()).contains("remindTime"),
                "2514 remind scan shape untouched (orderBy remindTime)");
    }
}
