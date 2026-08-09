package io.nop.datav.service.linkage;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.JumpResult;
import io.nop.datav.biz.LinkageResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.entity.AbstractNopDatavTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_JUMP_TARGET;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联动 + 跳转 API 接线测试（D2-2 Phase 2）。
 *
 * <p>通过注入 {@link INopDatavPanelBiz} 代理调用 {@code resolveLinkage} / {@code resolveJump}，
 * 验证 Panel BizModel → 联动配置解析 → 规则匹配 → 参数合成 调用链运行时连通（非 mock-only）。</p>
 *
 * <p>覆盖：联动正常路径、无联动配置面板（返回 null）、联动配置格式错误、点击字段不匹配（返回 null）、
 * 跳转正常路径（dashboard + external-url）、跳转目标无效、URL 模板占位符替换、参数映射解析。</p>
 */
public class TestNopDatavLinkageE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavPanelBiz panelBiz;

    /**
     * 联动正常路径：点击 source 面板 region 字段 → 返回目标面板 + region 参数（targetParam 格式）。
     * 证明 resolveLinkage 调用链：BizModel → LinkageExecutor → LinkageConfigParser → 规则匹配 → 参数合成。
     */
    @Test
    public void testResolveLinkageNormal() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link", "link-test");
        NopDatavPanel source = savePanel("p-source", dash.getDashboardId(), "source");
        source.setPanelType(TYPE_CHART);
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of(
                        "sourceField", "region",
                        "targetPanelId", "p-target",
                        "targetParam", "region"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);
        // target panel must exist and be in the same dashboard
        savePanel("p-target", dash.getDashboardId(), "target");

        LinkageResult result = panelBiz.resolveLinkage("p-source",
                clickContext("region", "east"), context);

        assertNotNull(result, "matched rule must return non-null result");
        assertEquals("p-target", result.getTargetPanelId());
        assertEquals("east", result.getParams().get("region"));
    }

    /**
     * 无联动配置面板：panelConfig 无 linkage 区域 → 返回 null（合法分支，非报错）。
     */
    @Test
    public void testResolveLinkageNoLinkageConfigReturnsNull() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link-2", "link-test-2");
        NopDatavPanel source = savePanel("p-source-2", dash.getDashboardId(), "source");
        source.setPanelConfig(JsonTool.stringify(Map.of("component", Map.of("type", "chart"))));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        LinkageResult result = panelBiz.resolveLinkage("p-source-2",
                clickContext("region", "east"), context);
        assertNull(result, "panel without linkage config returns null (legit branch)");
    }

    /**
     * 点击字段不匹配任何规则：返回 null（合法分支）。
     */
    @Test
    public void testResolveLinkageFieldNotMatchedReturnsNull() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link-3", "link-test-3");
        NopDatavPanel source = savePanel("p-source-3", dash.getDashboardId(), "source");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of(
                        "sourceField", "region",
                        "targetPanelId", "p-target-3",
                        "targetParam", "region"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);
        savePanel("p-target-3", dash.getDashboardId(), "target");

        // Click on 'category' but rule only matches 'region'
        LinkageResult result = panelBiz.resolveLinkage("p-source-3",
                clickContext("category", "books"), context);
        assertNull(result, "click field not matching any rule returns null");
    }

    /**
     * 联动配置格式错误：显式抛 NopException（不返回 null 作为「正常」）。
     */
    @Test
    public void testResolveLinkageMalformedConfigThrows() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link-4", "link-test-4");
        NopDatavPanel source = savePanel("p-source-4", dash.getDashboardId(), "source");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                // linkage rule missing required field targetPanelId
                "linkage", List.of(Map.of(
                        "sourceField", "region",
                        "targetParam", "region"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        assertThrows(NopException.class,
                () -> panelBiz.resolveLinkage("p-source-4",
                        clickContext("region", "east"), context));
    }

    /**
     * 联动目标面板不存在：显式抛 ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND。
     */
    @Test
    public void testResolveLinkageTargetPanelNotFoundThrows() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link-5", "link-test-5");
        NopDatavPanel source = savePanel("p-source-5", dash.getDashboardId(), "source");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of(
                        "sourceField", "region",
                        "targetPanelId", "p-missing-target",
                        "targetParam", "region"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);
        // p-missing-target never saved

        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.resolveLinkage("p-source-5",
                        clickContext("region", "east"), context));
        assertEquals(ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 联动源面板点击上下文缺 field：显式抛 NopException（不静默跳过）。
     */
    @Test
    public void testResolveLinkageMissingFieldThrows() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link-6", "link-test-6");
        NopDatavPanel source = savePanel("p-source-6", dash.getDashboardId(), "source");

        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.resolveLinkage("p-source-6", Map.of("value", "east"), context));
        assertEquals(ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 跳转 dashboard 正常路径：targetId 为常量看板 ID；params 中 ${region} 引用被解析为字段值。
     */
    @Test
    public void testResolveJumpDashboardNormal() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-jump", "jump-test");
        // dashboard jump target must exist; save it as a separate dashboard
        saveDashboard("dash-target", "Jump Target");
        NopDatavPanel source = savePanel("p-jump-src", dash.getDashboardId(), "jump-src");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "dashboard",
                        "targetId", "dash-target",
                        "params", Map.of("region", "${region}")))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        JumpResult result = panelBiz.resolveJump("p-jump-src",
                clickContext("region", "east"), context);
        assertNotNull(result);
        assertEquals("dashboard", result.getTargetType());
        assertEquals("dash-target", result.getTargetId(), "dashboard targetId is constant, no substitution");
        assertEquals("east", result.getParams().get("region"));
    }

    /**
     * 跳转 external-url 正常路径：URL 模板 ${region} 占位符被替换为字段值。
     */
    @Test
    public void testResolveJumpExternalUrlTemplateSubstitution() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-jump-2", "jump-test-2");
        NopDatavPanel source = savePanel("p-jump-src-2", dash.getDashboardId(), "jump-src");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "external-url",
                        "targetId", "https://example.com/r?region=${region}"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        JumpResult result = panelBiz.resolveJump("p-jump-src-2",
                clickContext("region", "east"), context);
        assertNotNull(result);
        assertEquals("external-url", result.getTargetType());
        assertEquals("https://example.com/r?region=east", result.getTargetId(),
                "URL template ${region} placeholder must be substituted with clicked value");
    }

    /**
     * 无跳转配置面板：返回 null（合法分支）。
     */
    @Test
    public void testResolveJumpNoJumpConfigReturnsNull() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-jump-3", "jump-test-3");
        NopDatavPanel source = savePanel("p-jump-src-3", dash.getDashboardId(), "jump-src");
        source.setPanelConfig(JsonTool.stringify(Map.of("component", Map.of("type", "chart"))));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        JumpResult result = panelBiz.resolveJump("p-jump-src-3",
                clickContext("region", "east"), context);
        assertNull(result);
    }

    /**
     * 跳转配置格式错误（targetType 非法）：显式抛 NopException。
     */
    @Test
    public void testResolveJumpInvalidTargetTypeThrows() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-jump-4", "jump-test-4");
        NopDatavPanel source = savePanel("p-jump-src-4", dash.getDashboardId(), "jump-src");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "bogus",
                        "targetId", "x"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        assertThrows(NopException.class,
                () -> panelBiz.resolveJump("p-jump-src-4",
                        clickContext("region", "east"), context));
    }

    /**
     * 跳转 dashboard 目标不存在：显式抛 ERR_DATAV_INVALID_JUMP_TARGET（不静默跳过）。
     */
    @Test
    public void testResolveJumpDashboardTargetNotFoundThrows() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-jump-6", "jump-test-6");
        NopDatavPanel source = savePanel("p-jump-src-6", dash.getDashboardId(), "jump-src");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "dashboard",
                        // dashboard "missing-dash" never saved
                        "targetId", "missing-dash"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.resolveJump("p-jump-src-6",
                        clickContext("region", "east"), context));
        assertEquals(ERR_DATAV_INVALID_JUMP_TARGET.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 跳转字段不匹配：返回 null（合法分支）。
     */
    @Test
    public void testResolveJumpFieldNotMatchedReturnsNull() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-jump-5", "jump-test-5");
        NopDatavPanel source = savePanel("p-jump-src-5", dash.getDashboardId(), "jump-src");
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "jump", List.of(Map.of(
                        "sourceField", "region",
                        "targetType", "dashboard",
                        "targetId", "x"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);

        JumpResult result = panelBiz.resolveJump("p-jump-src-5",
                clickContext("category", "books"), context);
        assertNull(result);
    }

    /**
     * 联动 + getPanelData 链路验证：resolveLinkage 返回的 params 传入 getPanelData 后可被 paramMapping 消费。
     * 这是纯接口调用层面的验证（不经过 SQL），断言 params Map 格式兼容（key = source key format）。
     */
    @Test
    public void testLinkageResultParamsFormatIsCompatibleWithGetPanelDataRequestParams() {
        IServiceContext context = newContext("linkage-user");
        NopDatavDashboard dash = saveDashboard("dash-link-fmt", "link-fmt-test");
        NopDatavPanel source = savePanel("p-fmt-src", dash.getDashboardId(), "fmt-src");
        source.setPanelType(TYPE_CHART);
        source.setPanelConfig(JsonTool.stringify(Map.of(
                "linkage", List.of(Map.of(
                        "sourceField", "region",
                        "targetPanelId", "p-fmt-target",
                        "targetParam", "regionFilter"))
        )));
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(source);
        savePanel("p-fmt-target", dash.getDashboardId(), "fmt-target");

        LinkageResult result = panelBiz.resolveLinkage("p-fmt-src",
                clickContext("region", "north"), context);
        assertNotNull(result);
        // params key is targetParam (regionFilter) - matches paramMapping source key convention
        // (paramMapping: {"region": {"source": "regionFilter"}})
        assertTrue(result.getParams().containsKey("regionFilter"),
                "params key must be the targetParam (paramMapping source key format)");
        assertEquals("north", result.getParams().get("regionFilter"));
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private static Map<String, Object> clickContext(String field, Object value) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("field", field);
        ctx.put("value", value);
        return ctx;
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

    private NopDatavPanel savePanel(String id, String dashboardId, String name) {
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
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }
}
