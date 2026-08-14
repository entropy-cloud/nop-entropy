package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.query.PanelDataBinder;
import io.nop.report.dao.entity.NopReportDataset;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面板参数求值器错误契约测试（Dim09-01）。
 *
 * <p>覆盖 {@code PanelParamEvaluator.evaluate} 的 4 类 paramMapping 配置错误经公共入口
 * {@link PanelDataBinder#queryPanelData} 抛出 {@link NopException} +
 * {@code ERR_DATAV_INVALID_PARAM_CONFIG}（非裸 {@link IllegalArgumentException}），
 * 携带 {@code panelId} param 与 {@code reason} param，且保留 cause 链（解析失败场景）。</p>
 *
 * <p>这 4 类错误此前以裸 {@link IllegalArgumentException} 直穿
 * {@code NopDatavPanelBizModel.getPanelData}（{@code @BizQuery} 公共 GraphQL action），
 * 破坏两层错误策略与错误码可观测性。</p>
 */
public class TestPanelParamErrorContract extends AbstractNopDatavTest {

    @jakarta.inject.Inject
    IDaoProvider daoProvider;

    /**
     * 错误 1/4：paramMapping 为非法 JSON（解析失败）→ NopException + cause 链保留。
     */
    @Test
    public void testInvalidJsonThrowsNopExceptionWithCause() {
        NopException ex = runAndCapture("{\"region\":", "panel-invalid-json");

        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-invalid-json", ex.getParam(ARG_PANEL_ID));
        assertNotNull(ex.getParam(ARG_REASON), "reason param non-empty");
        assertNotNull(ex.getCause(), "cause chain preserved for parse failure");
        assertReasonMentions(ex, "paramMapping");
    }

    /**
     * 错误 2/4：paramMapping 解析成功但非 object（如数组）→ NopException（无底层异常，cause 为 null）。
     */
    @Test
    public void testNonObjectThrowsNopException() {
        NopException ex = runAndCapture("[1,2,3]", "panel-non-object");

        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-non-object", ex.getParam(ARG_PANEL_ID));
        assertNotNull(ex.getParam(ARG_REASON));
        // 校验失败无底层异常，cause 为 null（非吞异常，错误显式抛出）
        assertNull(ex.getCause());
    }

    /**
     * 错误 3/4：单条 rule 非 object（如字符串）→ NopException。
     */
    @Test
    public void testRuleNotObjectThrowsNopException() {
        NopException ex = runAndCapture("{\"region\":\"not-object\"}", "panel-rule-not-object");

        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-rule-not-object", ex.getParam(ARG_PANEL_ID));
        assertNotNull(ex.getParam(ARG_REASON));
        assertReasonMentions(ex, "region");
        assertNull(ex.getCause());
    }

    /**
     * 错误 4/4：rule 缺 source → NopException。
     */
    @Test
    public void testMissingSourceThrowsNopException() {
        NopException ex = runAndCapture("{\"region\":{}}", "panel-missing-source");

        assertEquals(ERR_DATAV_INVALID_PARAM_CONFIG.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-missing-source", ex.getParam(ARG_PANEL_ID));
        assertNotNull(ex.getParam(ARG_REASON));
        assertReasonMentions(ex, "source");
        assertNull(ex.getCause());
    }

    /**
     * 回归保护：合法 paramMapping 仍正常工作（确保错误包装未误伤正常路径）。
     */
    @Test
    public void testValidParamMappingStillWorks() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ok");
        NopDatavPanel panel = savePanelWithParamMapping("panel-ok", dashboardId,
                "{\"region\":{\"source\":\"region\",\"defaultValue\":\"south\"}}");

        PanelDataBinder binder = new PanelDataBinder(daoProvider, jdbcTemplate);
        PanelDataResult result = binder.queryPanelData(
                panel.getPanelId(), panel, Map.of("region", "north"));
        assertNotNull(result);
        assertTrue(result.isHasDataset());
        assertEquals(2, result.getRows().size(), "north has 2 rows");
    }

    // ==================== Helpers ====================

    private NopException runAndCapture(String malformedParamMapping, String panelId) {
        setupSalesData();
        String dashboardId = setupDashboard("dash-" + panelId);
        NopDatavPanel panel = savePanelWithParamMapping(panelId, dashboardId, malformedParamMapping);

        PanelDataBinder binder = new PanelDataBinder(daoProvider, jdbcTemplate);
        return assertThrows(NopException.class, () ->
                binder.queryPanelData(panel.getPanelId(), panel, null));
    }

    private static void assertReasonMentions(NopException ex, String keyword) {
        Object reason = ex.getParam(ARG_REASON);
        assertNotNull(reason);
        assertNotEquals("", reason.toString().trim());
        assertTrue(reason.toString().toLowerCase().contains(keyword.toLowerCase()),
                "reason mentions " + keyword + ": " + reason);
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .name("drop:TEST_DATAV_SALES_PARM")
                    .sql("drop table TEST_DATAV_SALES_PARM").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES_PARM")
                .sql("create table TEST_DATAV_SALES_PARM(REGION varchar(50), AMOUNT int)")
                .end());
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES_PARM")
                .sql("insert into TEST_DATAV_SALES_PARM(REGION, AMOUNT) values(")
                .param("north").sql(",").param(100).sql(")")
                .end());
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES_PARM")
                .sql("insert into TEST_DATAV_SALES_PARM(REGION, AMOUNT) values(")
                .param("north").sql(",").param(200).sql(")")
                .end());
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES_PARM")
                .sql("insert into TEST_DATAV_SALES_PARM(REGION, AMOUNT) values(")
                .param("south").sql(",").param(50).sql(")")
                .end());
    }

    private String setupDashboard(String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private NopDatavPanel savePanelWithParamMapping(String panelId, String dashboardId, String paramMapping) {
        String refId = panelId + "-ref";
        String dsId = panelId + "-ds";

        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, AMOUNT as amount "
                + "from TEST_DATAV_SALES_PARM where REGION = ${region} order by AMOUNT desc");
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
        ref.setDatasetRefId(refId);
        ref.setDashboardId(dashboardId);
        ref.setRefDatasetId(dsId);
        ref.setRefDatasetName(panelId);
        ref.setParamMapping(paramMapping);
        ref.setVersion(0L);
        ref.setCreatedBy("test");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("test");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelId);
        p.setDisplayName(panelId);
        p.setPanelType(TYPE_CHART);
        p.setDatasetRefId(refId);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }
}
