package io.nop.datav.service.entity;

import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavGenerateScreenExecutor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DatavGenerateScreenExecutor} 单元测试（D6-2 Phase 2 Exit Criteria）。
 *
 * <p>覆盖：合法规格真实创建 Screen+ScreenWidget / 装饰+数据组件混合（裁定 M）/ 装饰组件可用全部 14 类 /
 * datasetRefId 直存 sid 不经 DatasetRef（裁定 N）/ 自由画布定位落地 / 越界 throw（裁定 L）/ 非法定位 throw /
 * 未知 componentType / datasetSid 缺失 / backgroundConfig 非法 / screenName UK 冲突（裁定 Q）/ 无半成品残留 /
 * displayName 回退（裁定 P）/ operator 经 context 强转传递（裁定 G）/ 接线验证（rule #23）。</p>
 */
public class TestDatavGenerateScreenExecutor extends AbstractNopDatavTest {

    private static final String OPERATOR = "screen-gen-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    // ==================== 合法规格：真实创建 + operator 传递 + 定位落地 + 直存 sid ====================

    @Test
    public void testValidSpecCreatesScreenAndWidgets() {
        NopReportDataset ds = newActiveSqlDataset("ds-screen-valid",
                "select REGION as region, AMOUNT as amount from TEST_SCREEN_SALES",
                fieldsMeta("region", "amount"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = baseSpec("Business KPI Screen", 1920, 1080,
                Arrays.asList(
                        widget("chart", "ds-screen-valid", fieldMapping("x", "region", "y", "amount"),
                                0, 100, 900, 400, 1),
                        widget("stat-tile", "ds-screen-valid", null,
                                1000, 100, 400, 200, 1),
                        widget("decorative-border", null, null,
                                0, 0, 1920, 80, 0)
                ));

        AiToolCallResult result = runExecutor(spec);

        assertEquals("success", result.getStatus(), "valid spec should succeed");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        String screenId = (String) parsed.get("screenId");
        assertNotNull(screenId, "screenId must be returned");

        // 断言 Screen 落库 + DRAFT + createdBy=operator
        NopDatavScreen screen = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screenId);
        assertNotNull(screen, "Screen row must exist in DB");
        assertEquals(0, screen.getPublishStatus(), "publishStatus=DRAFT (0)");
        assertEquals(Long.valueOf(0L), screen.getPublishedVersion(), "publishedVersion=0");
        assertEquals(OPERATOR, screen.getCreatedBy(), "createdBy=operator (裁定 G)");
        assertEquals(OPERATOR, screen.getUpdatedBy());
        assertEquals(1920, screen.getScreenWidth());
        assertEquals(1080, screen.getScreenHeight());

        // 断言 Widget 落库 + 定位落地
        List<NopDatavScreenWidget> widgets = findWidgetsByScreen(screenId);
        assertEquals(3, widgets.size(), "3 widgets created");

        NopDatavScreenWidget chartWidget = widgets.stream()
                .filter(w -> "chart".equals(w.getComponentType())).findFirst().orElse(null);
        assertNotNull(chartWidget);
        assertEquals("ds-screen-valid", chartWidget.getDatasetRefId(),
                "chart widget datasetRefId = nop-report sid (裁定 N: 直存 sid，不经 DatasetRef)");
        assertEquals(0, chartWidget.getX());
        assertEquals(100, chartWidget.getY());
        assertEquals(900, chartWidget.getW());
        assertEquals(400, chartWidget.getH());
        assertEquals(1, chartWidget.getZ());
        assertNotNull(chartWidget.getWidgetConfig(), "widgetConfig must be populated");
        assertTrue(chartWidget.getWidgetConfig().contains("fieldMapping"),
                "widgetConfig must contain fieldMapping subkey");

        // 装饰组件 datasetRefId 为空（裁定 M）
        NopDatavScreenWidget borderWidget = widgets.stream()
                .filter(w -> "decorative-border".equals(w.getComponentType())).findFirst().orElse(null);
        assertNotNull(borderWidget);
        assertNull(borderWidget.getDatasetRefId(), "decorative-border datasetRefId is null (裁定 M)");

        // 裁定 N：不创建 DatasetRef 行（大屏直存 sid）
        List<NopDatavDatasetRef> refs = daoProvider.daoFor(NopDatavDatasetRef.class).findAll();
        assertTrue(refs.isEmpty(), "no DatasetRef rows created (裁定 N: 直存 sid 不经 DatasetRef)");
    }

    // ==================== displayName 回退（裁定 P） ====================

    @Test
    public void testDisplayNameFallsBackToScreenName() {
        Map<String, Object> spec = baseSpec("No Display Name Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 0, 100, 50, 0)));
        // 不设置 displayName

        AiToolCallResult result = runExecutor(spec);
        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        String screenId = (String) parsed.get("screenId");

        NopDatavScreen screen = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screenId);
        assertEquals("No Display Name Screen", screen.getDisplayName(),
                "displayName falls back to screenName (裁定 P)");
    }

    // ==================== 装饰组件可用全部 14 类（裁定 M） ====================

    @Test
    public void testDecorativeComponentsAllowedForScreen() {
        // 大屏可用 decorative-border（看板会拒，大屏允许）
        Map<String, Object> spec = baseSpec("Decorative Screen", 1920, 1080,
                Collections.singletonList(
                        widget("decorative-border", null, null, 0, 0, 100, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertEquals("success", result.getStatus(),
                "decorative-border must be ALLOWED for screen (unlike dashboard)");
    }

    // ==================== 未知 componentType 拒绝 ====================

    @Test
    public void testUnknownComponentRejected() {
        Map<String, Object> spec = baseSpec("Unknown Screen", 1920, 1080,
                Collections.singletonList(
                        widget("nonexistent-type", null, null, 0, 0, 100, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-unknown-component",
                "unknown component type must be rejected");
    }

    // ==================== 非法定位（裁定 L）：x 为负 / w <= 0 ====================

    @Test
    public void testNegativePositionRejected() {
        Map<String, Object> spec = baseSpec("Neg Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, -1, 0, 100, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-widget-position",
                "negative x must be rejected as INVALID_WIDGET_POSITION");
    }

    @Test
    public void testZeroWidthRejected() {
        Map<String, Object> spec = baseSpec("ZeroW Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 0, 0, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-widget-position",
                "zero w must be rejected as INVALID_WIDGET_POSITION");
    }

    // ==================== 越界（裁定 L）：x+w > screenWidth → OUT_OF_BOUNDS ====================

    @Test
    public void testOutOfBoundsRejected() {
        // x=1000 + w=1000 = 2000 > screenWidth 1920
        Map<String, Object> spec = baseSpec("Oob Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 1000, 0, 1000, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-widget-out-of-bounds",
                "x+w > screenWidth must be rejected as OUT_OF_BOUNDS (裁定 L mandatory throw)");
    }

    @Test
    public void testOutOfBoundsHeightRejected() {
        // y=1000 + h=100 = 1100 > screenHeight 1080
        Map<String, Object> spec = baseSpec("Oob H Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 1000, 100, 100, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-widget-out-of-bounds",
                "y+h > screenHeight must be rejected as OUT_OF_BOUNDS");
    }

    @Test
    public void testWidgetExactlyAtCanvasEdgeAllowed() {
        // x+w = screenWidth exactly (not exceeding) → allowed
        Map<String, Object> spec = baseSpec("Edge Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 1820, 980, 100, 100, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertEquals("success", result.getStatus(),
                "x+w == screenWidth (exactly at edge) must be ALLOWED (not out of bounds)");
    }

    // ==================== datasetSid 缺失（needsDataset=true） ====================

    @Test
    public void testNeedsDatasetComponentMissingDatasetSid() {
        Map<String, Object> spec = baseSpec("Missing Ds Screen", 1920, 1080,
                Collections.singletonList(
                        widget("chart", null, null, 0, 0, 100, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-spec",
                "chart (needsDataset=true) without datasetSid must be rejected");
    }

    // ==================== datasetSid 不存在 ====================

    @Test
    public void testDatasetSidNotFound() {
        Map<String, Object> spec = baseSpec("No Ds Screen", 1920, 1080,
                Collections.singletonList(
                        widget("chart", "ds-does-not-exist", null, 0, 0, 100, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-dataset-not-found",
                "non-existent datasetSid must be rejected");
    }

    // ==================== fieldMapping 字段非法 ====================

    @Test
    public void testFieldMappingUnknownFieldRejected() {
        NopReportDataset ds = newActiveSqlDataset("ds-screen-fm",
                "select REGION as region from TEST_SCREEN_SALES", fieldsMeta("region"));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        Map<String, Object> spec = baseSpec("Bad Fm Screen", 1920, 1080,
                Collections.singletonList(
                        widget("chart", "ds-screen-fm", fieldMapping("x", "nonExistentField"),
                                0, 0, 100, 50, 0)));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-spec",
                "fieldMapping referencing a field not in dsMeta must be rejected");
    }

    // ==================== backgroundConfig 非法 ====================

    @Test
    public void testInvalidBackgroundConfigRejected() {
        Map<String, Object> spec = baseSpec("Bad Bg Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 0, 100, 50, 0)));
        // palette 非 object（string）→ ScreenThemeParser 会抛 ERR_DATAV_INVALID_THEME_CONFIG
        Map<String, Object> bg = new LinkedHashMap<>();
        bg.put("palette", "not-an-object");
        spec.put("backgroundConfig", bg);

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-invalid-background-config",
                "invalid backgroundConfig must be rejected");
    }

    // ==================== screenName UK 冲突（裁定 Q） ====================

    @Test
    public void testDuplicateScreenNameRejected() {
        // 先创建一个大屏
        Map<String, Object> spec1 = baseSpec("Duplicate Name", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 0, 100, 50, 0)));
        AiToolCallResult result1 = runExecutor(spec1);
        assertEquals("success", result1.getStatus(), "first creation should succeed");

        // 同名再创建 → DUPLICATE_SCREEN_NAME
        Map<String, Object> spec2 = baseSpec("Duplicate Name", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 0, 100, 50, 0)));
        AiToolCallResult result2 = runExecutor(spec2);
        assertFailure(result2, "nop.err.datav.chatbi-generate-duplicate-screen-name",
                "duplicate screenName must be rejected as DUPLICATE_SCREEN_NAME (裁定 Q)");
    }

    // ==================== 无半成品残留（裁定 O） ====================

    @Test
    public void testValidationFailureLeavesNoHalfBakedRows() {
        long screenBefore = daoProvider.daoFor(NopDatavScreen.class).findAll().size();
        long widgetBefore = daoProvider.daoFor(NopDatavScreenWidget.class).findAll().size();

        // 一个 widget 合法，第二个越界 → 整体事务回滚
        Map<String, Object> spec = baseSpec("Half Baked Screen", 1920, 1080,
                Arrays.asList(
                        widget("text", null, null, 0, 0, 100, 50, 0),
                        widget("text", null, null, 1000, 0, 1000, 50, 0) // 越界
                ));

        AiToolCallResult result = runExecutor(spec);
        assertFailure(result, "nop.err.datav.chatbi-generate-widget-out-of-bounds",
                "out-of-bounds widget must be rejected (no half-baked rows)");

        assertEquals(screenBefore, daoProvider.daoFor(NopDatavScreen.class).findAll().size(),
                "no Screen row left after validation failure (裁定 O)");
        assertEquals(widgetBefore, daoProvider.daoFor(NopDatavScreenWidget.class).findAll().size(),
                "no ScreenWidget row left after validation failure");
    }

    // ==================== 接线验证（rule #23）：IToolManager 发现 + 可调用 ====================

    @Test
    public void testExecutorWiredAndCallableViaToolManager() {
        IToolManager toolManager = buildToolManagerWithExecutor();

        boolean listed = toolManager.listTools().stream()
                .anyMatch(t -> DatavGenerateScreenExecutor.TOOL_NAME.equals(t.getName()));
        assertTrue(listed, "IToolManager.listTools() must include datav-generate-screen (接线 rule #23)");

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavGenerateScreenExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(baseSpec("Wired Screen", 1920, 1080,
                Collections.singletonList(
                        widget("text", null, null, 0, 0, 100, 50, 0)))));

        AiToolCallResult result = toolManager.callTool(
                DatavGenerateScreenExecutor.TOOL_NAME, call,
                new ChatBiToolExecuteContext(null, OPERATOR)).join();

        assertEquals("success", result.getStatus(), "wired executor must return success via IToolManager");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        String screenId = (String) parsed.get("screenId");
        assertNotNull(daoProvider.daoFor(NopDatavScreen.class).getEntityById(screenId),
                "screenId must point to a real DB row (Anti-Hollow)");
    }

    // ==================== schemaJson round-trip（嵌套 widgets 数组） ====================

    @Test
    public void testSchemaJsonRoundTripNestedWidgetsArray() {
        String llmArguments = "{"
                + "\"screenName\":\"Round Trip Screen\","
                + "\"screenWidth\":1920,"
                + "\"screenHeight\":1080,"
                + "\"widgets\":["
                + "  {\"componentType\":\"text\",\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"z\":0},"
                + "  {\"componentType\":\"decorative-border\",\"x\":0,\"y\":0,\"w\":1920,\"h\":80,\"z\":1}"
                + "]}";

        AiToolCall call = new AiToolCall();
        call.setToolName(DatavGenerateScreenExecutor.TOOL_NAME);
        call.setInput(llmArguments);

        DatavGenerateScreenExecutor exec = newExecutor();
        AiToolCallResult result = exec.executeAsync(call, new ChatBiToolExecuteContext(null, OPERATOR))
                .toCompletableFuture().join();

        assertEquals("success", result.getStatus(), "nested widgets JSON must round-trip cleanly");
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        List<?> widgets = (List<?>) parsed.get("widgets");
        assertEquals(2, widgets.size(), "both widgets created from nested array");
    }

    // ==================== Helpers ====================

    private DatavGenerateScreenExecutor newExecutor() {
        DatavGenerateScreenExecutor exec = new DatavGenerateScreenExecutor();
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
        DatavGenerateScreenExecutor exec = newExecutor();
        AiToolCall call = new AiToolCall();
        call.setToolName(DatavGenerateScreenExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(spec));
        return exec.executeAsync(call, new ChatBiToolExecuteContext(null, OPERATOR))
                .toCompletableFuture().join();
    }

    private void assertFailure(AiToolCallResult result, String expectedErrorCode, String msg) {
        assertEquals("failure", result.getStatus(), msg);
        assertNotNull(result.getError(), msg);
        String body = result.getError().getBody();
        assertTrue(body.contains(expectedErrorCode),
                msg + " (expected " + expectedErrorCode + " in: " + body + ")");
    }

    private List<NopDatavScreenWidget> findWidgetsByScreen(String screenId) {
        return daoProvider.daoFor(NopDatavScreenWidget.class).findAll().stream()
                .filter(w -> screenId.equals(w.getScreenId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<String, Object> baseSpec(String screenName, int width, int height,
                                                  List<Map<String, Object>> widgets) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("screenName", screenName);
        spec.put("screenWidth", width);
        spec.put("screenHeight", height);
        spec.put("widgets", widgets);
        return spec;
    }

    private static Map<String, Object> widget(String componentType, String datasetSid,
                                                Map<String, Object> fieldMapping,
                                                int x, int y, int w, int h, int z) {
        Map<String, Object> wid = new LinkedHashMap<>();
        wid.put("componentType", componentType);
        if (datasetSid != null) {
            wid.put("datasetSid", datasetSid);
        }
        if (fieldMapping != null) {
            wid.put("fieldMapping", fieldMapping);
        }
        wid.put("x", x);
        wid.put("y", y);
        wid.put("w", w);
        wid.put("h", h);
        wid.put("z", z);
        return wid;
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
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(now));
        return ds;
    }
}
