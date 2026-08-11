package io.nop.wf.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证工作流设计器 designer.flux.yaml 在 flux 模式下可加载，并返回包含 dynamic-renderer 的页面。
 *
 * <p>设计契约 ai-dev/design/nop-wf/workflow-designer-integration.md §3.5：
 * <ul>
 *     <li>页面 JSON 顶层 body 含 {@code dynamic-renderer}；</li>
 *     <li>{@code dynamic-renderer.loadAction} 指向 {@code /r/WorkflowDesignerService__loadDesignerPage}；</li>
 *     <li>loadAction 数据含 {@code wfDefId} 绑定（由 view.xml drawer.data 传入）。</li>
 * </ul>
 */
@NopTestConfig
public class TestFluxYamlPages extends JunitBaseTestCase {
    @Inject
    PageProvider pageProvider;

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "amis");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
    }

    @Test
    public void testDesigner() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
        assertNotNull(pageProvider.getPage("/nop/wf/designer/designer.flux.yaml", ""));
    }

    /**
     * 验证 designer.flux.yaml 在 flux 模式下经 PageProvider 加载后返回的页面 JSON 结构正确：
     * 顶层 body 包含 dynamic-renderer，且 loadAction URL 指向 WorkflowDesignerService__loadDesignerPage。
     */
    @Test
    public void testDesignerFluxContainsDynamicRenderer() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");

        Map<String, Object> page = pageProvider.getPage("/nop/wf/designer/designer.flux.yaml", "");
        assertNotNull(page, "designer page must load in flux mode");

        assertEquals("page", page.get("type"), "top-level type must be page");

        Object body = page.get("body");
        assertTrue(body instanceof List, "body must be a list");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bodyList = (List<Map<String, Object>>) body;

        Map<String, Object> dynamicRenderer = bodyList.stream()
                .filter(item -> "dynamic-renderer".equals(item.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("dynamic-renderer not found in body"));

        Object loadAction = dynamicRenderer.get("loadAction");
        assertNotNull(loadAction, "dynamic-renderer must have loadAction");
        Map<String, Object> loadActionMap = asMap(loadAction);
        assertEquals("ajax", loadActionMap.get("action"), "loadAction must be ajax");

        Map<String, Object> args = asMap(loadActionMap.get("args"));
        String url = String.valueOf(args.get("url"));
        assertTrue(url.contains("WorkflowDesignerService__loadDesignerPage"),
                "loadAction URL must point to WorkflowDesignerService__loadDesignerPage, got: " + url);

        Map<String, Object> data = asMap(args.get("data"));
        assertTrue(data.containsKey("wfDefId"),
                "loadAction data must contain wfDefId binding (passed from view.xml drawer.data)");
    }

    /**
     * 验证 dynamic-renderer 加载期有 spinner fallback。
     */
    @Test
    public void testDesignerFluxHasSpinnerFallback() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");

        Map<String, Object> page = pageProvider.getPage("/nop/wf/designer/designer.flux.yaml", "");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bodyList = (List<Map<String, Object>>) page.get("body");
        Map<String, Object> dynamicRenderer = bodyList.stream()
                .filter(item -> "dynamic-renderer".equals(item.get("type")))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rendererBody = (List<Map<String, Object>>) dynamicRenderer.get("body");
        assertNotNull(rendererBody, "dynamic-renderer body (spinner fallback) required");
        boolean hasSpinner = rendererBody.stream().anyMatch(item -> "spinner".equals(item.get("type")));
        assertTrue(hasSpinner, "dynamic-renderer must contain spinner fallback during loading");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertNotNull(value, "expected non-null Map");
        return (Map<String, Object>) value;
    }
}
