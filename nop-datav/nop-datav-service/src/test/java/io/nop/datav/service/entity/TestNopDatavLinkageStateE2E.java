package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.FilterState;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavFilterStateBiz;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.JumpResult;
import io.nop.datav.biz.LinkageResult;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端测试（D2-2 + D2-3 Phase 4）。贯穿完整链路：
 *
 * <pre>
 * 1. 联动 E2E：
 *    创建看板（带全局参数 region）→ 创建业务表 TEST_DATAV_LINK_E2E
 *    → 创建 NopReportDataset（SQL 用 ${region} 命名参数）
 *    → 创建两个 DatasetRef（source + target，paramMapping 都引用 region source key）
 *    → 创建源面板 A + 目标面板 B（同一 dashboardId）
 *    → 配置 A 的 panelConfig linkage 规则（点击 region → 设置 B 的 region 筛选）
 *    → 调用 resolveLinkage 模拟点击 region=East
 *    → 对 B 调用 getPanelData 传入联动参数
 *    → 断言 B 仅返回 East 数据（证明联动 → getPanelData → paramMapping → SQL 链路端到端连通）
 *
 * 2. 跳转 E2E：
 *    配置 panelConfig jump 规则（点击 region → 跳转到目标 dashboard，携带 region 参数）
 *    → 调用 resolveJump
 *    → 断言返回的 targetType + targetId + params 含正确注入参数
 *    配置 external-url 跳转规则
 *    → 调用 resolveJump
 *    → 断言 URL 模板占位符被替换为点击值
 *
 * 3. 外部参数注入 E2E：
 *    通过 API 请求参数传入 region=North
 *    → 经 resolveFilterValues 归一化
 *    → 面板查询受影响（证明外部参数 = URL/API 传入的全局筛选参数，复用 resolveFilterValues）
 *
 * 4. filter_state E2E：
 *    设置全局筛选 region=East + 面板 A 联动选择
 *    → 保存 filter_state
 *    → 清除内存（new context）
 *    → 恢复 filter_state
 *    → 断言恢复后的全局筛选 + 联动选择与保存前一致
 * </pre>
 *
 * <p>本测试由独立子 agent closure audit 作为端到端验证证据（rule #22 + Anti-Hollow Check）。</p>
 */
public class TestNopDatavLinkageStateE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavPanelBiz panelBiz;

    @Inject
    INopDatavFilterStateBiz filterStateBiz;

    /**
     * 主线联动 E2E：点击源面板 region 字段 → 目标面板仅返回 East 数据。
     * 证明 resolveLinkage → getPanelData → paramMapping → SQL 链路端到端连通。
     */
    @Test
    public void testLinkageEndToEnd() {
        IServiceContext context = newContext("e2e-user");
        createLinkE2ETable();
        insertLinkE2ERow("east", 100);
        insertLinkE2ERow("east", 200);
        insertLinkE2ERow("west", 300);
        insertLinkE2ERow("north", 50);

        NopReportDataset ds = newReportDataset("ds-link-e2e", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_LINK_E2E where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-link-e2e", "Link E2E");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all")
        )));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        // Source panel's DatasetRef: source key = "region"
        NopDatavDatasetRef sourceRef = newDatasetRef("ref-link-src", dashboard.getDashboardId(),
                "ds-link-e2e", "Source DS");
        sourceRef.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(sourceRef);

        // Target panel's DatasetRef: source key = "regionFilter" (set by linkage)
        NopDatavDatasetRef targetRef = newDatasetRef("ref-link-tgt", dashboard.getDashboardId(),
                "ds-link-e2e", "Target DS");
        targetRef.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "regionFilter"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(targetRef);

        // Source panel A with linkage rule: click region → set target panel's regionFilter param
        NopDatavPanel sourcePanel = newPanel("p-link-src", dashboard.getDashboardId(), "Source A");
        sourcePanel.setPanelType(TYPE_TABLE);
        sourcePanel.setDatasetRefId("ref-link-src");
        sourcePanel.setPanelConfig(JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of(
                        "sourceField", "region",
                        "targetPanelId", "p-link-tgt",
                        "targetParam", "regionFilter"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(sourcePanel);

        // Target panel B
        NopDatavPanel targetPanel = newPanel("p-link-tgt", dashboard.getDashboardId(), "Target B");
        targetPanel.setPanelType(TYPE_TABLE);
        targetPanel.setDatasetRefId("ref-link-tgt");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(targetPanel);

        // Step 1: resolveLinkage simulating clicking region=east on source panel A
        Map<String, Object> clickContext = new LinkedHashMap<>();
        clickContext.put("field", "region");
        clickContext.put("value", "east");
        LinkageResult linkage = panelBiz.resolveLinkage("p-link-src", clickContext, context);

        assertNotNull(linkage, "matched rule must return non-null");
        assertEquals("p-link-tgt", linkage.getTargetPanelId());
        assertEquals("east", linkage.getParams().get("regionFilter"),
                "linkage result params key = targetParam (regionFilter), value = clicked value");

        // Step 2: pass linkage params to getPanelData on target panel B
        PanelDataResult result = panelBiz.getPanelData("p-link-tgt", linkage.getParams(), context);

        assertNotNull(result);
        assertTrue(result.isHasDataset());
        assertEquals(2, result.getRows().size(), "target panel B must return only east rows (2)");

        // Step 3: click different region value -> different result (param-driven query)
        clickContext.put("value", "north");
        LinkageResult linkage2 = panelBiz.resolveLinkage("p-link-src", clickContext, context);
        PanelDataResult result2 = panelBiz.getPanelData("p-link-tgt", linkage2.getParams(), context);
        assertEquals(1, result2.getRows().size(), "clicking region=north must yield 1 row");

        assertTrue(result.getRows().size() != result2.getRows().size(),
                "different click values must produce different result counts (param e2e effective)");
    }

    /**
     * 跳转 E2E（dashboard + external-url）：源字段值正确注入到目标 URL/参数。
     */
    @Test
    public void testJumpEndToEnd() {
        IServiceContext context = newContext("e2e-user");
        NopDatavDashboard dash = saveDashboard("dash-jump-e2e", "Jump E2E");
        // dashboard jump target must exist; save it as a separate dashboard
        saveDashboard("dash-target-1", "Jump Target 1");

        // dashboard-type jump
        NopDatavPanel panel1 = newPanel("p-jump-1", dash.getDashboardId(), "Jump1");
        panel1.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "dashboard",
                        "targetId", "dash-target-1",
                        "params", Map.of("region", "${region}")))
        )));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel1);

        Map<String, Object> click1 = new LinkedHashMap<>();
        click1.put("field", "region");
        click1.put("value", "east");
        JumpResult result1 = panelBiz.resolveJump("p-jump-1", click1, context);
        assertNotNull(result1);
        assertEquals("dashboard", result1.getTargetType());
        assertEquals("dash-target-1", result1.getTargetId());
        assertEquals("east", result1.getParams().get("region"),
                "dashboard jump params: ${region} resolved to clicked value");

        // external-url jump with URL template
        NopDatavPanel panel2 = newPanel("p-jump-2", dash.getDashboardId(), "Jump2");
        panel2.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "external-url",
                        "targetId", "https://report.example.com/r?region=${region}&from=datav"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel2);

        Map<String, Object> click2 = new LinkedHashMap<>();
        click2.put("field", "region");
        click2.put("value", "north");
        JumpResult result2 = panelBiz.resolveJump("p-jump-2", click2, context);
        assertNotNull(result2);
        assertEquals("external-url", result2.getTargetType());
        assertEquals("https://report.example.com/r?region=north&from=datav", result2.getTargetId(),
                "URL template ${region} placeholder substituted with clicked value");
    }

    /**
     * 外部参数注入 E2E：通过 API 请求参数传入 region=North → 经 resolveFilterValues 归一化 → 面板查询受影响。
     * 证明外部参数 = URL/API/embed 传入的全局筛选参数，复用 resolveFilterValues（无独立通道）。
     */
    @Test
    public void testExternalParamInjectionEndToEnd() {
        IServiceContext context = newContext("e2e-user");
        createLinkE2ETable();
        insertLinkE2ERow("east", 100);
        insertLinkE2ERow("north", 50);
        insertLinkE2ERow("north", 70);

        NopReportDataset ds = newReportDataset("ds-ext-e2e", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_LINK_E2E where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-ext-e2e", "Ext E2E");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all")
        )));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef("ref-ext-e2e", dashboard.getDashboardId(),
                "ds-ext-e2e", "Ext DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel panel = newPanel("p-ext-e2e", dashboard.getDashboardId(), "Ext Panel");
        panel.setPanelType(TYPE_TABLE);
        panel.setDatasetRefId("ref-ext-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        // External param injection: caller passes region=north via API request params
        // (simulating URL/embed/API source). resolveFilterValues normalizes it.
        Map<String, Object> resolved = dashboardBiz.resolveFilterValues(
                dashboard.getDashboardId(), Map.of("region", "north"), context);
        assertEquals("north", resolved.get("region"));

        // Pass resolved params to getPanelData -> query result affected by external param
        PanelDataResult result = panelBiz.getPanelData(panel.getPanelId(), resolved, context);
        assertEquals(2, result.getRows().size(), "external region=north must yield 2 rows");

        // Different external param -> different result (proves external param drives query)
        Map<String, Object> resolved2 = dashboardBiz.resolveFilterValues(
                dashboard.getDashboardId(), Map.of("region", "east"), context);
        PanelDataResult result2 = panelBiz.getPanelData(panel.getPanelId(), resolved2, context);
        assertEquals(1, result2.getRows().size(), "external region=east must yield 1 row");
    }

    /**
     * filter_state E2E：保存全局筛选 + 联动选择 → 清除内存 → 恢复 → 状态一致。
     * 证明 saveFilterState → NopDatavFilterState 实体存储 → getFilterState 链路连通。
     */
    @Test
    public void testFilterStateSaveRestoreEndToEnd() {
        IServiceContext context = newContext("alice");
        NopDatavDashboard dashboard = saveDashboard("dash-fs-e2e", "FS E2E");

        // Build state to save: global filters + panel A's linkage selection + URL state
        Map<String, Object> globalFilters = new LinkedHashMap<>();
        globalFilters.put("region", "east");
        globalFilters.put("dateRange.start", "2024-01-01");
        globalFilters.put("dateRange.end", "2024-06-30");

        Map<String, Map<String, Object>> panelSelections = new LinkedHashMap<>();
        Map<String, Object> selection = new LinkedHashMap<>();
        selection.put("field", "region");
        selection.put("value", "east");
        panelSelections.put("p-src-e2e", selection);

        String urlState = "region=east&dateRange.start=2024-01-01&dateRange.end=2024-06-30";

        // Save
        filterStateBiz.saveFilterState(dashboard.getDashboardId(),
                globalFilters, panelSelections, urlState, context);

        // "Clear memory" - simulate fresh request by using new context (same user)
        IServiceContext restoredContext = newContext("alice");

        // Restore
        FilterState restored = filterStateBiz.getFilterState(dashboard.getDashboardId(), restoredContext);
        assertNotNull(restored, "saved state must be restored");

        // Verify global filters preserved (round-trip consistency)
        assertEquals("east", restored.getGlobalFilters().get("region"));
        assertEquals("2024-01-01", restored.getGlobalFilters().get("dateRange.start"));
        assertEquals("2024-06-30", restored.getGlobalFilters().get("dateRange.end"));

        // Verify panel selections preserved
        assertEquals(1, restored.getPanelSelections().size());
        FilterState.PanelSelection ps = restored.getPanelSelections().get("p-src-e2e");
        assertNotNull(ps);
        assertEquals("region", ps.getField());
        assertEquals("east", ps.getValue());

        // Verify urlState preserved
        assertEquals(urlState, restored.getUrlState());

        // Verify restored state format compatible with getPanelData requestParams
        assertTrue(restored.getGlobalFilters().containsKey("region"),
                "restored globalFilters must be in resolveFilterValues output format (usable as requestParams)");
    }

    /**
     * filter_state 按 userName 隔离 E2E：用户 A 保存的状态不影响用户 B；never-saved 用户返回 null。
     */
    @Test
    public void testFilterStateIsolationEndToEnd() {
        IServiceContext aliceCtx = newContext("alice");
        IServiceContext bobCtx = newContext("bob");
        NopDatavDashboard dashboard = saveDashboard("dash-fs-iso", "FS ISO");

        // Alice saves east
        filterStateBiz.saveFilterState(dashboard.getDashboardId(),
                Map.of("region", "east"), Map.of(), "region=east", aliceCtx);

        // Bob (different user) has no state for this dashboard
        FilterState bobState = filterStateBiz.getFilterState(dashboard.getDashboardId(), bobCtx);
        assertNull(bobState, "user B has no state even though user A saved");

        // Alice still has her state
        FilterState aliceState = filterStateBiz.getFilterState(dashboard.getDashboardId(), aliceCtx);
        assertNotNull(aliceState);
        assertEquals("east", aliceState.getGlobalFilters().get("region"));
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavDashboard saveDashboard(String id, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private NopDatavPanel newPanel(String id, String dashboardId, String name) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(name);
        p.setDisplayName(name);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
        return p;
    }

    private NopDatavDatasetRef newDatasetRef(String id, String dashboardId, String refDsId, String refDsName) {
        long now = System.currentTimeMillis();
        NopDatavDatasetRef r = new NopDatavDatasetRef();
        r.setDatasetRefId(id);
        r.setDashboardId(dashboardId);
        r.setRefDatasetId(refDsId);
        r.setRefDatasetName(refDsName);
        r.setVersion(0L);
        r.setCreatedBy("test");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("test");
        r.setUpdateTime(new Timestamp(now));
        return r;
    }

    private NopReportDataset newReportDataset(String sid, String dsType, String dsText) {
        long now = System.currentTimeMillis();
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(sid);
        ds.setIsSingleRow(false);
        ds.setDsType(dsType);
        ds.setDsText(dsText);
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(now));
        return ds;
    }

    private void createLinkE2ETable() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_LINK_E2E")
                .sql("create table TEST_DATAV_LINK_E2E(REGION varchar(50), AMOUNT int)")
                .end());
    }

    private void insertLinkE2ERow(String region, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_LINK_E2E")
                .sql("insert into TEST_DATAV_LINK_E2E(REGION, AMOUNT) values(")
                .param(region).sql(",").param(amount).sql(")")
                .end());
    }
}
