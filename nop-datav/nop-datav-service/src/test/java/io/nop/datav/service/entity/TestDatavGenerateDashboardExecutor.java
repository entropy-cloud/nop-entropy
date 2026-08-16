package io.nop.datav.service.entity;

import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavGenerateDashboardExecutor;
import io.nop.orm.IOrmTemplate;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatavGenerateDashboardExecutor} 单元测试（D6-1b Phase 2 Exit Criteria）。
 *
 * <p>覆盖：合法规格真实创建 Dashboard+DatasetRef+Panel / 装饰类型拒绝（裁定 M）/ 未知类型拒绝 /
 * datasetSid 不存在 / fieldMapping 非法 / DatasetRef 去重（裁定 I）/ needsDataset=false 组件无 datasetSid /
 * 事务无半成品（裁定 O）/ panelName + panelConfig 落地 / operator 经 context 强转传递（裁定 G）。</p>
 *
 * <p>接线验证（rule #23）：从 {@link IToolManager} 取 {@code datav-generate-dashboard} 工具执行
 * 返回真实 dashboardId（非 "no executor registered"）。</p>
 */
public class TestDatavGenerateDashboardExecutor extends AbstractNopDatavTest {

    private static final String OPERATOR = "gen-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    // ==================== 合法规格：真实创建 + operator 传递 + panelName/panelConfig 落地 ====================

    @Test
    public void testValidSpecCreatesDashboardDatasetRefPanel() {
        NopReportDataset ds = newActiveSqlDataset("ds-gen-valid",
                "select REGION as region, AMOUNT as amount from TEST_GEN_SALES where REGION = ${region}",
                fieldsMeta("region", "amount"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", "Regional Sales Dashboard");
        spec.put("description", "by region");
        spec.put("panels", Arrays.asList(
                panel("Sales by Region", "chart", "ds-gen-valid",
                        fieldMapping("x", "region", "y", "amount"), 0),
                panel("Notes", "text", null, null, 1)
        ));

        AiToolCallResult result = runExecutor(spec);

        assertEquals("success", result.getStatus(), "valid spec should succeed");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        String dashboardId = (String) parsed.get("dashboardId");
        assertNotNull(dashboardId, "dashboardId must be returned");

        // 断言 Dashboard 落库 + DRAFT + createdBy=operator
        NopDatavDashboard dash = daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        assertNotNull(dash, "Dashboard row must exist in DB");
        assertEquals(0, dash.getPublishStatus(), "publishStatus=DRAFT (0)");
        assertEquals(Long.valueOf(0L), dash.getPublishedVersion(), "publishedVersion=0");
        assertEquals(OPERATOR, dash.getCreatedBy(), "createdBy=operator (裁定 G)");
        assertEquals(OPERATOR, dash.getUpdatedBy());

        // 断言 Panel 落库 + panelName=title + panelConfig 含 fieldMapping
        List<NopDatavPanel> panels = findPanelsByDashboard(dashboardId);
        assertEquals(2, panels.size(), "2 panels created");

        NopDatavPanel chartPanel = panels.stream()
                .filter(p -> TYPE_CHART == p.getPanelType()).findFirst().orElse(null);
        assertNotNull(chartPanel);
        assertEquals("Sales by Region", chartPanel.getPanelName(), "panelName=规格.title (mandatory)");
        assertNotNull(chartPanel.getDatasetRefId(), "chart panel must have datasetRefId");
        assertNotNull(chartPanel.getPanelConfig(), "panelConfig must be populated");
        assertTrue(chartPanel.getPanelConfig().contains("fieldMapping"),
                "panelConfig must contain fieldMapping subkey (非空壳)");
        assertTrue(chartPanel.getPanelConfig().contains("region"),
                "panelConfig.fieldMapping content is the LLM-provided mapping");

        NopDatavPanel textPanel = panels.stream()
                .filter(p -> TYPE_TEXT == p.getPanelType()).findFirst().orElse(null);
        assertNotNull(textPanel);
        assertNull(textPanel.getDatasetRefId(), "text panel has no datasetRefId (needsDataset=false)");
    }

    // ==================== DatasetRef 去重（裁定 I） ====================

    /**
     * D1(b)（plan 2026-08-15-2146-3 Phase 2，镜像 Screen executor 裁定 Q 先例）：
     * dashboardName UK 物化后的重名兜底——预检查命中 → DUPLICATE_DASHBOARD_NAME 结构化错误
     * （非裸 DB 异常），LLM 收到错误可 rename-retry。
     */
    @Test
    public void testDuplicateDashboardNameRejectedWithStructuredError() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", "dup-gen-dashboard");
        spec.put("panels", Collections.singletonList(panel("Chart", "text", null, null, 0)));

        AiToolCallResult first = runExecutor(spec);
        assertEquals("success", first.getStatus(), "first creation should succeed");

        AiToolCallResult second = runExecutor(spec);
        assertFailure(second, "nop.err.datav.chatbi-generate-duplicate-dashboard-name",
                "duplicate dashboardName must be rejected as DUPLICATE_DASHBOARD_NAME (D1b)");
    }

    @Test
    public void testDatasetRefDeduplicationForSameDatasetSid() {
        NopReportDataset ds = newActiveSqlDataset("ds-dedup",
                "select REGION as region from TEST_GEN_SALES", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", "Dedup Dashboard");
        spec.put("panels", Arrays.asList(
                panel("Chart A", "chart", "ds-dedup", null, 0),
                panel("Table B", "table", "ds-dedup", null, 1)
        ));

        AiToolCallResult result = runExecutor(spec);
        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        String dashboardId = parsed.get("dashboardId").toString();

        // 2 个 panel 引用同一 datasetSid → 只创建 1 个 DatasetRef，两个 panel 的 datasetRefId 相同
        List<NopDatavDatasetRef> refs = findDatasetRefsByDashboard(dashboardId);
        assertEquals(1, refs.size(), "only 1 DatasetRef for 2 panels referencing same datasetSid (裁定 I)");
        String refId = refs.get(0).getDatasetRefId();

        List<NopDatavPanel> panels = findPanelsByDashboard(dashboardId);
        assertEquals(2, panels.size());
        for (NopDatavPanel p : panels) {
            assertEquals(refId, p.getDatasetRefId(), "both panels' datasetRefId point to the single deduped DatasetRef");
        }
        assertEquals("{}", refs.get(0).getParamMapping(), "paramMapping 初值 {}");
    }

    // ==================== 装饰类型拒绝（裁定 M） ====================

    @Test
    public void testDecorativeComponentRejectedAsUnsupported() {
        Map<String, Object> spec = baseSpecOnePanel("Dash Decor", panel("Border", "decorative-border", null, null, 0));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-unsupported-component",
                "decorative-border must be rejected as UNSUPPORTED_COMPONENT (not allowed for dashboards)");
    }

    // ==================== 未知类型拒绝（裁定 M） ====================

    @Test
    public void testUnknownComponentRejected() {
        Map<String, Object> spec = baseSpecOnePanel("Dash Unknown", panel("Weird", "nonexistent-type", null, null, 0));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-unknown-component",
                "unknown component type must be rejected as UNKNOWN_COMPONENT");
    }

    // ==================== datasetSid 不存在/非活跃（裁定 J） ====================

    @Test
    public void testDatasetSidNotFoundRejected() {
        Map<String, Object> spec = baseSpecOnePanel("Dash NoDs",
                panel("Chart", "chart", "ds-does-not-exist", null, 0));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-dataset-not-found",
                "non-existent datasetSid must be rejected");
    }

    @Test
    public void testDatasetSidInactiveRejected() {
        NopReportDataset ds = newActiveSqlDataset("ds-inactive-gen",
                "select 1", fieldsMeta("a"));
        ds.setStatus(0); // inactive
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = baseSpecOnePanel("Dash InactiveDs",
                panel("Chart", "chart", "ds-inactive-gen", null, 0));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-dataset-not-found",
                "inactive dataset (status=0) must be rejected");
    }

    // ==================== fieldMapping 字段非法（裁定 J + N） ====================

    @Test
    public void testFieldMappingUnknownFieldRejected() {
        NopReportDataset ds = newActiveSqlDataset("ds-fm",
                "select REGION as region from TEST_GEN_SALES", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = baseSpecOnePanel("Dash BadFm",
                panel("Chart", "chart", "ds-fm", fieldMapping("x", "nonExistentField"), 0));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-spec",
                "fieldMapping referencing a field not in dsMeta must be rejected");
    }

    // ==================== needsDataset=true 缺 datasetSid（裁定 J） ====================

    @Test
    public void testNeedsDatasetComponentMissingDatasetSid() {
        Map<String, Object> spec = baseSpecOnePanel("Dash MissingDs",
                panel("Chart", "chart", null, null, 0));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-spec",
                "chart (needsDataset=true) without datasetSid must be rejected");
    }

    // ==================== 无半成品残留（裁定 O） ====================

    @Test
    public void testValidationFailureLeavesNoHalfBakedRows() {
        // 一个 panel 合法，第二个 panel 非法（未知类型）→ 整体事务回滚，无任何行落盘
        NopReportDataset ds = newActiveSqlDataset("ds-noref",
                "select 1 as one", fieldsMeta("one"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", "Half Baked Dashboard");
        spec.put("panels", Arrays.asList(
                panel("Good Chart", "chart", "ds-noref", null, 0),
                panel("Bad", "nonexistent-type", null, null, 1)
        ));

        long dashBefore = daoProvider.daoFor(NopDatavDashboard.class).findAll().size();
        long refBefore = daoProvider.daoFor(NopDatavDatasetRef.class).findAll().size();
        long panelBefore = daoProvider.daoFor(NopDatavPanel.class).findAll().size();

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-unknown-component",
                "unknown component type must be rejected (no half-baked rows)");

        // 无半成品：失败后无新增行
        assertEquals(dashBefore, daoProvider.daoFor(NopDatavDashboard.class).findAll().size(),
                "no Dashboard row left after validation failure (裁定 O)");
        assertEquals(refBefore, daoProvider.daoFor(NopDatavDatasetRef.class).findAll().size(),
                "no DatasetRef row left after validation failure");
        assertEquals(panelBefore, daoProvider.daoFor(NopDatavPanel.class).findAll().size(),
                "no Panel row left after validation failure");
    }

    // ==================== 接线验证（rule #23）：IToolManager 发现 + 可调用 ====================

    @Test
    public void testExecutorWiredAndCallableViaToolManager() {
        NopReportDataset ds = newActiveSqlDataset("ds-wire",
                "select 1 as one", fieldsMeta("one"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        IToolManager toolManager = buildToolManagerWithExecutor();

        // 断言 IToolManager.listTools() 包含 datav-generate-dashboard
        boolean listed = toolManager.listTools().stream()
                .anyMatch(t -> DatavGenerateDashboardExecutor.TOOL_NAME.equals(t.getName()));
        assertTrue(listed, "IToolManager.listTools() must include datav-generate-dashboard (接线 rule #23)");

        // 经 IToolManager.callTool 调用 → 返回真实 dashboardId（非 "no executor registered"）
        AiToolCall call = new AiToolCall();
        call.setToolName(DatavGenerateDashboardExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(baseSpecOnePanel("Wired Dashboard",
                panel("Chart", "chart", "ds-wire", null, 0))));

        AiToolCallResult result = toolManager.callTool(
                DatavGenerateDashboardExecutor.TOOL_NAME, call,
                new ChatBiToolExecuteContext(null, OPERATOR)).join();

        assertEquals("success", result.getStatus(), "wired executor must return success via IToolManager");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        String dashboardId = parsed.get("dashboardId").toString();
        assertNotNull(dashboardProvider().getEntityById(dashboardId),
                "dashboardId must point to a real DB row (Anti-Hollow)");
    }

    // ==================== schemaJson round-trip（嵌套数组） ====================

    @Test
    public void testSchemaJsonRoundTripNestedPanelsArray() {
        // 模拟 LLM 工具调用参数（JSON）能干净解析为规格对象（嵌套 panels 数组往返）
        String llmArguments = "{"
                + "\"dashboardName\":\"Round Trip Dash\","
                + "\"panels\":["
                + "  {\"title\":\"A\",\"componentType\":\"chart\",\"datasetSid\":\"ds-rt\",\"fieldMapping\":{\"x\":\"region\"},\"sortOrder\":0},"
                + "  {\"title\":\"B\",\"componentType\":\"text\"}"
                + "]}";

        NopReportDataset ds = newActiveSqlDataset("ds-rt",
                "select REGION as region from TEST_GEN_SALES", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavGenerateDashboardExecutor.TOOL_NAME);
        call.setInput(llmArguments);

        DatavGenerateDashboardExecutor exec = newExecutor();
        AiToolCallResult result = exec.executeAsync(call, new ChatBiToolExecuteContext(null, OPERATOR))
                .toCompletableFuture().join();

        assertEquals("success", result.getStatus(), "nested panels JSON must round-trip cleanly");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        List<?> panels = (List<?>) parsed.get("panels");
        assertEquals(2, panels.size(), "both panels created from nested array");
    }

    // ==================== 数据集可见性（AR-1 / P1-03 裁定 D4 选项 B，plan 2026-08-16-2137-1） ====================

    /**
     * AR-1: 非 owner 非 admin（alice）经 datav-generate-dashboard 绑定他人数据集（createdBy=bob）→
     * 显式拒绝（携带 ERR_DATAV_CHATBI_DATASET_NO_ACCESS 结构化错误码三要素），且不落库
     * （无 Dashboard/DatasetRef/Panel 残留，镜像「事务无半成品」断言模式）。
     */
    @Test
    public void testGenerateInvisibleDatasetRejectedForNonOwner() {
        NopReportDataset ds = newActiveSqlDataset("ds-vis-bob-dash",
                "select 1 as one", fieldsMeta("one"));
        ds.setCreatedBy("bob");
        ds.setUpdatedBy("bob");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        long dashBefore = daoProvider.daoFor(NopDatavDashboard.class).findAll().size();
        long refBefore = daoProvider.daoFor(NopDatavDatasetRef.class).findAll().size();
        long panelBefore = daoProvider.daoFor(NopDatavPanel.class).findAll().size();

        Map<String, Object> spec = baseSpecOnePanel("Vis Reject Dash",
                panel("Chart", "chart", "ds-vis-bob-dash", null, 0));
        AiToolCallResult result = runExecutor(spec, new ChatBiToolExecuteContext(null, "alice", false));

        assertFailure(result, "nop.err.datav.chatbi-dataset-no-access",
                "non-owner generate-dashboard binding another user's dataset must be rejected");
        // 错误体三要素（镜像 describe/query 先例）：errorCode + datasetSid + userName
        String body = result.getError().getBody();
        assertTrue(body.contains("ds-vis-bob-dash"), "error body must carry datasetSid: " + body);
        assertTrue(body.contains("alice"), "error body must carry userName: " + body);

        // 不落库：拒绝后无 Dashboard/DatasetRef/Panel 残留
        assertEquals(dashBefore, daoProvider.daoFor(NopDatavDashboard.class).findAll().size(),
                "no Dashboard row left after visibility rejection");
        assertEquals(refBefore, daoProvider.daoFor(NopDatavDatasetRef.class).findAll().size(),
                "no DatasetRef row left after visibility rejection");
        assertEquals(panelBefore, daoProvider.daoFor(NopDatavPanel.class).findAll().size(),
                "no Panel row left after visibility rejection");
    }

    /**
     * AR-1: owner（fixture createdBy=调用 operator，非 admin）对自己数据集正常生成（无过度限制）。
     */
    @Test
    public void testGenerateVisibleForOwnerOwnDataset() {
        NopReportDataset ds = newActiveSqlDataset("ds-vis-owner-dash",
                "select 1 as one", fieldsMeta("one"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = baseSpecOnePanel("Vis Owner Dash",
                panel("Chart", "chart", "ds-vis-owner-dash", null, 0));
        AiToolCallResult result = runExecutor(spec, new ChatBiToolExecuteContext(null, OPERATOR, false));

        assertEquals("success", result.getStatus(), "owner must generate against own dataset");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        assertNotNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(parsed.get("dashboardId").toString()),
                "owner-generated dashboard is a real DB row");
    }

    /**
     * AR-1: admin 跨 createdBy 正常生成（回归：无过度限制，镜像 testQueryVisibleForAdminAcrossCreators）。
     */
    @Test
    public void testGenerateVisibleForAdminAcrossCreators() {
        NopReportDataset ds = newActiveSqlDataset("ds-vis-admin-dash",
                "select 1 as one", fieldsMeta("one"));
        ds.setCreatedBy("bob");
        ds.setUpdatedBy("bob");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = baseSpecOnePanel("Vis Admin Dash",
                panel("Chart", "chart", "ds-vis-admin-dash", null, 0));
        AiToolCallResult result = runExecutor(spec, new ChatBiToolExecuteContext(null, "admin-user", true));

        assertEquals("success", result.getStatus(), "admin must generate across creators");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        assertNotNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(parsed.get("dashboardId").toString()),
                "admin-generated dashboard is a real DB row");
    }

    /**
     * AR-1: 无身份 context（operator=null 且非 admin，fail-closed）绑定任何活跃数据集 → 同样拒绝
     * NO_ACCESS（不走 SYSTEM_OPERATOR 回退——否则 createdBy="system" 数据集会对无身份调用可见）。
     */
    @Test
    public void testGenerateNoIdentityContextRejected() {
        NopReportDataset ds = newActiveSqlDataset("ds-vis-noidentity-dash",
                "select 1 as one", fieldsMeta("one"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        long dashBefore = daoProvider.daoFor(NopDatavDashboard.class).findAll().size();

        Map<String, Object> spec = baseSpecOnePanel("Vis NoIdentity Dash",
                panel("Chart", "chart", "ds-vis-noidentity-dash", null, 0));
        AiToolCallResult result = runExecutor(spec, new ChatBiToolExecuteContext());

        assertFailure(result, "nop.err.datav.chatbi-dataset-no-access",
                "no-identity context must be rejected (fail-closed)");
        assertEquals(dashBefore, daoProvider.daoFor(NopDatavDashboard.class).findAll().size(),
                "no Dashboard row left after fail-closed rejection");
    }

    // ==================== Helpers ====================

    private DatavGenerateDashboardExecutor newExecutor() {
        DatavGenerateDashboardExecutor exec = new DatavGenerateDashboardExecutor();
        exec.setDaoProvider(daoProvider);
        exec.setOrmTemplate(ormTemplate);
        return exec;
    }

    private IToolManager buildToolManagerWithExecutor() {
        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Collections.singletonList(newExecutor()));
        return new ToolManagerImpl(provider, Collections.emptyList());
    }

    private AiToolCallResult runExecutor(Map<String, Object> spec) {
        return runExecutor(spec, new ChatBiToolExecuteContext(null, OPERATOR));
    }

    private AiToolCallResult runExecutor(Map<String, Object> spec, ChatBiToolExecuteContext context) {
        DatavGenerateDashboardExecutor exec = newExecutor();
        AiToolCall call = new AiToolCall();
        call.setToolName(DatavGenerateDashboardExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(spec));
        return exec.executeAsync(call, context)
                .toCompletableFuture().join();
    }

    private void assertFailure(AiToolCallResult result, String expectedErrorCode, String msg) {
        assertEquals("failure", result.getStatus(), msg);
        assertNotNull(result.getError(), msg);
        String body = result.getError().getBody();
        assertTrue(body.contains(expectedErrorCode),
                msg + " (expected " + expectedErrorCode + " in: " + body + ")");
    }

    private IEntityDao<NopDatavDashboard> dashboardProvider() {
        return daoProvider.daoFor(NopDatavDashboard.class);
    }

    private List<NopDatavPanel> findPanelsByDashboard(String dashboardId) {
        return daoProvider.daoFor(NopDatavPanel.class).findAll().stream()
                .filter(p -> dashboardId.equals(p.getDashboardId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<NopDatavDatasetRef> findDatasetRefsByDashboard(String dashboardId) {
        return daoProvider.daoFor(NopDatavDatasetRef.class).findAll().stream()
                .filter(r -> dashboardId.equals(r.getDashboardId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<String, Object> baseSpecOnePanel(String dashName, Map<String, Object> panel) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", dashName);
        spec.put("panels", Collections.singletonList(panel));
        return spec;
    }

    private static Map<String, Object> panel(String title, String componentType, String datasetSid,
                                              Map<String, Object> fieldMapping, int sortOrder) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("componentType", componentType);
        if (datasetSid != null) {
            p.put("datasetSid", datasetSid);
        }
        if (fieldMapping != null) {
            p.put("fieldMapping", fieldMapping);
        }
        p.put("sortOrder", sortOrder);
        return p;
    }

    private static Map<String, Object> fieldMapping(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String fieldsMeta(String... names) {
        java.util.List<Map<String, Object>> fields = new java.util.ArrayList<>();
        for (String n : names) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", n);
            f.put("type", "string");
            fields.add(f);
        }
        return JsonTool.stringify(java.util.Collections.singletonMap("fields", fields));
    }

    private NopReportDataset newActiveSqlDataset(String sid, String dsText, String dsMeta) {
        long now = System.currentTimeMillis();
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(sid);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText(dsText);
        ds.setDsMeta(dsMeta != null ? dsMeta : "{}");
        ds.setStatus(1);
        ds.setVersion(0);
        // AR-1 迁移（plan 2026-08-16-2137-1）：正向用例以 OPERATOR（非 admin）调用，fixture createdBy
        // 必须与调用 operator 对齐，否则被可见性判定拒绝（预期内迁移，非回归）
        ds.setCreatedBy(OPERATOR);
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy(OPERATOR);
        ds.setUpdateTime(new Timestamp(now));
        return ds;
    }
}
