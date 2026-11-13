package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-009 主查询路径行上界红⑧钉（plan 2526 裁定②，C10 补测-2）。
 *
 * <p>配置小上界（2 行）+ 种子大结果集（5 行）→ 主查询路径（getPanelData 单面板/getDashboardData
 * 批量）显式抛/归集 ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED（非静默截断）。缺陷态（无上界）
 * 全量物化不断言——红证由变异锚定承载（摘 rowLimit 传入 → 查询成功不断言红）。</p>
 */
@NopTestProperty(name = "nop.datav.dashboard-query.max-rows-per-panel", value = "2")
public class TestNopDatavQueryRowBound_CL009 extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavPanelBiz panelBiz;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Test
    public void testGetPanelDataThrowsRowLimitExceeded() {
        setup();
        IServiceContext ctx = ownerContext("alice");
        NopDatavPanel panel = saveChartPanel("panel-cl009", seedDashboard("dash-cl009"));

        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.getPanelData(panel.getPanelId(), Map.of(), ctx));
        assertEquals(ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED.getErrorCode(), ex.getErrorCode(),
                "panel query over bound must throw query-row-limit-exceeded, got: " + ex.getErrorCode());
    }

    @Test
    public void testGetDashboardDataReportsPanelLevelFailureItem() {
        setup();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = seedDashboard("dash-cl009-batch");
        saveChartPanel("panel-cl009-b1", dashboardId);

        Object result = dashboardBiz.getDashboardData(dashboardId, Map.of(), null, ctx);
        List<?> items = panelItems(result);
        assertNotNull(items, "dashboard data items present");
        assertEquals(1, items.size());
        Object item = items.get(0);
        Boolean success = (Boolean) beanProp(item, "success");
        String errorCode = (String) beanProp(item, "errorCode");
        assertEquals(Boolean.FALSE, success, "over-bound panel must be a failure item (not silent truncation)");
        assertEquals(ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED.getErrorCode(), errorCode,
                "failure item carries the query-row-limit error code");
    }

    // ==================== Helpers（沿 E2E harness） ====================

    private List<?> panelItems(Object dashboardDataResult) {
        Object items = beanProp(dashboardDataResult, "panels");
        return (List<?>) items;
    }

    private Object beanProp(Object bean, String name) {
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return bean.getClass().getMethod("get" + cap).invoke(bean);
        } catch (Exception ignored) {
            // boolean 属性走 is 前缀——反射探测两命名约定
        }
        try {
            return bean.getClass().getMethod("is" + cap).invoke(bean);
        } catch (Exception e) {
            throw new IllegalStateException("no accessor for " + name + " on " + bean.getClass(), e);
        }
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private String seedDashboard(String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private void setup() {
        try {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .name("drop:TEST_DATAV_SALES_CL009")
                    .sql("drop table TEST_DATAV_SALES_CL009").end());
        } catch (Exception ignored) {
            // 表不存在（首跑）时 drop 失败属预期——建表语句紧随其后
        }
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES_CL009")
                .sql("create table TEST_DATAV_SALES_CL009(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        for (int i = 1; i <= 5; i++) {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .name("insert:TEST_DATAV_SALES_CL009")
                    .sql("insert into TEST_DATAV_SALES_CL009(REGION, PRODUCT, AMOUNT) values(")
                    .param("north").sql(",").param("p" + i).sql(",").param(i).sql(")")
                    .end());
        }
    }

    private NopDatavPanel saveChartPanel(String panelId, String dashboardId) {
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(panelId + "-ds");
        ds.setDsName(panelId + "-ds");
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES_CL009");
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(System.currentTimeMillis()));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        long now = System.currentTimeMillis();
        NopDatavDatasetRef ref = new NopDatavDatasetRef();
        ref.setDatasetRefId(panelId + "-ref");
        ref.setDashboardId(dashboardId);
        ref.setRefDatasetId(panelId + "-ds");
        ref.setRefDatasetName(panelId);
        ref.setParamMapping(JsonTool.stringify(Map.of()));
        ref.setVersion(0L);
        ref.setCreatedBy("alice");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("alice");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelId);
        p.setDisplayName(panelId);
        p.setPanelType(io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART);
        p.setDatasetRefId(panelId + "-ref");
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }
}
