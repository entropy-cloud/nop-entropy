package io.nop.wf.dao.store;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.dao.entity.NopWfStepInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）W-08（1.5-C5-006 裁定 D）红⑧ 查询形状腿。
 *
 * <p>红线：修复前 {@code getSteps} 直返 ORM 实体集全集、{@code AbstractWorkflowStore} 内存
 * 过滤（getLatestStepRecordByName 全集排序、getStepRecords 全集载入）。修复后相同语义的读
 * 路径必须携带 SQL 谓词 / orderBy / limit（谓词可翻译腿下推）。
 *
 * <p>点名零改写：{@link TestWfScanQueryShape}（2514 C10-008）之 due/remind 查询形状断言保持原样
 * ——本类为 getSteps 族新增腿。
 *
 * <p>语义等价保护（会话内未 flush 写可见性）由 `nop-wf-service` 的 {@code TestDaoWorkflowEngine}
 * 全量 28 例承载——该套件即本工单的回归门（执行期实测：无条件下推时 21 例全红）。
 */
public class TestWfStepsReadPathR409 {

    private final DaoWorkflowStore store = new DaoWorkflowStore();

    /**
     * 红⑧(i)：getLatestStepRecordByName 必须 orderBy(createTime desc)+limit 1 全下推。
     */
    @Test
    public void latestStepQuery_pushesDownOrderByAndLimit() {
        QueryBean query = store.buildLatestStepRecordQuery("wf-r409", "review");
        assertNotNull(query.getFilter(), "latest-step query must carry SQL predicates (wfId + stepName)");
        assertEquals(1, query.getLimit(),
                "getLatestStepRecordByName must push down limit 1 (pre-fix: full set loaded + sorted in memory)");
        assertNotNull(query.getOrderBy(), "orderBy must be pushed down");
        assertEquals(1, query.getOrderBy().size());
        assertEquals(NopWfStepInstance.PROP_NAME_createTime, query.getOrderBy().get(0).getName());
        assertTrue(query.getOrderBy().get(0).isDesc(), "latest step requires createTime DESC ordering");
        assertTrue(containsFilterName(query.getFilter(), NopWfStepInstance.PROP_NAME_wfId));
        assertTrue(containsFilterName(query.getFilter(), NopWfStepInstance.PROP_NAME_stepName));
    }

    /**
     * 红⑧(ii)：includeHistory=false 腿必须下推 status &lt; HISTORY_BOUND 预滤（载入行数有界）。
     */
    @Test
    public void nonHistoryStepQuery_filtersByStatusCeiling() {
        QueryBean query = store.buildStepRecordsQuery("wf-r409", false);
        assertTrue(containsFilterName(query.getFilter(), NopWfStepInstance.PROP_NAME_status),
                "includeHistory=false must push down a status predicate (isHistory == status >= HISTORY_BOUND)");
        assertTrue(containsFilterName(query.getFilter(), NopWfStepInstance.PROP_NAME_wfId),
                "wfId predicate must always be pushed down");
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND,
                NopWfCoreConstants.WF_STEP_STATUS_COMPLETED,
                "history bound is derived from COMPLETED status");
    }

    /**
     * 红⑧ 对照腿：includeHistory=true 腿不得静默截断（保持全集语义）。
     */
    @Test
    public void historyStepQuery_keepsFullSetSemantics() {
        QueryBean query = store.buildStepRecordsQuery("wf-r409", true);
        assertFalse(containsFilterName(query.getFilter(), NopWfStepInstance.PROP_NAME_status),
                "includeHistory=true must NOT filter by status (no silent truncation)");
        assertEquals(0, query.getLimit(),
                "includeHistory=true must not impose an implicit limit (QueryBean default 0 = unbounded)");
    }

    /**
     * 红⑧ 接线腿：读方法必须在本类 override——否则调用落到 {@code AbstractWorkflowStore} 的
     * 内存全集扫描实现，SQL 下推等于没接线（Anti-Hollow 接线验证）。
     */
    @Test
    public void readPaths_areOverriddenInDaoStore() throws Exception {
        for (String name : new String[]{"getLatestStepRecordByName", "getStepRecords", "getStepRecordsByName"}) {
            boolean declaredHere = false;
            for (java.lang.reflect.Method m : DaoWorkflowStore.class.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    declaredHere = true;
                    break;
                }
            }
            assertTrue(declaredHere,
                    name + " must be overridden in DaoWorkflowStore so the SQL pushdown actually runs "
                            + "(W-08 wiring; pre-fix: inherited in-memory full-set scan)");
        }
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
