package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 flux.yaml 页面回退机制（Phase 1：顶层入口 + 注册 + 白名单 + helper）。
 */
public class TestFluxPageFallback extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    private static final String PAGE_PATH = "/nop/test/pages/test-flux-fallback/main.page.yaml";
    private static final String FLUX_PATH = "/nop/test/pages/test-flux-fallback/main.flux.yaml";

    @BeforeEach
    public void setUpFlux() {
        // 切换到 flux 模式并清除页面与 xlib 缓存（缓存键 locale|path 不含渲染模式，必须清 xpage）
        setRenderMode("flux");
    }

    @AfterEach
    public void tearDownMode() {
        setRenderMode("amis");
    }

    private void setRenderMode(String mode) {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, mode);
        ResourceComponentManager.instance().clearCache("xpage");
        ResourceComponentManager.instance().clearCache("xlib");
    }

    @Test
    public void testToFluxPagePath() {
        assertEquals("/a/b/main.flux.yaml", WebPageHelper.toFluxPagePath("/a/b/main.page.yaml"));
        assertEquals("main.flux.yaml", WebPageHelper.toFluxPagePath("main.page.yaml"));
        assertNull(WebPageHelper.toFluxPagePath("main.page.xml"));
        assertNull(WebPageHelper.toFluxPagePath("main.page.json"));
        assertNull(WebPageHelper.toFluxPagePath("main.yaml"));
        assertNull(WebPageHelper.toFluxPagePath(null));
    }

    @Test
    public void testTopLevelFallbackUsesFluxInFluxMode() {
        Map<String, Object> page = pageProvider.getPage(PAGE_PATH, "");
        assertNotNull(page);
        String json = JSON.serialize(page, true);
        System.out.println("Flux mode top-level fallback JSON:\n" + json);

        assertTrue(json.contains("__RENDERED_BY_FLUX__"), "flux 模式应回退到 main.flux.yaml");
        assertFalse(json.contains("__RENDERED_BY_AMIS__"), "flux 模式不应加载 page.yaml 内容");
    }

    @Test
    public void testTopLevelFallbackIgnoredInAmisMode() {
        setRenderMode("amis");
        Map<String, Object> page = pageProvider.getPage(PAGE_PATH, "");
        assertNotNull(page);
        String json = JSON.serialize(page, true);
        System.out.println("AMIS mode top-level fallback JSON:\n" + json);

        assertTrue(json.contains("__RENDERED_BY_AMIS__"), "amis 模式应加载 page.yaml，忽略 flux.yaml");
        assertFalse(json.contains("__RENDERED_BY_FLUX__"), "amis 模式不应加载 flux.yaml 内容");
    }

    @Test
    public void testDirectLoadFluxYaml() {
        // 直接请求 flux.yaml 在两种模式下都应加载成功（loader 注册生效，直接访问不受模式开关限制）
        setRenderMode("flux");
        Map<String, Object> page1 = pageProvider.getPage(FLUX_PATH, "");
        assertNotNull(page1);
        assertTrue(JSON.serialize(page1, true).contains("__RENDERED_BY_FLUX__"));

        setRenderMode("amis");
        Map<String, Object> page2 = pageProvider.getPage(FLUX_PATH, "");
        assertNotNull(page2);
        assertTrue(JSON.serialize(page2, true).contains("__RENDERED_BY_FLUX__"));
    }

    @Test
    public void testCheckPageFileAcceptsFluxYaml() {
        assertDoesNotThrow(() -> WebPageHelper.checkPageFile("/x/y/main.flux.yaml"));
        assertDoesNotThrow(() -> WebPageHelper.checkPageFile("/x/y/main.page.yaml"));
    }
}
