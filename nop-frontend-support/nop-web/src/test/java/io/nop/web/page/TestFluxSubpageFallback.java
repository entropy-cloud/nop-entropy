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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 flux-web LoadPage 的子页面 flux.yaml 回退（Phase 2）。
 * 覆盖 LoadPage 两个分支：pageId 分支（tab.page="subA"）与直接路径分支（tab.page="subB.page.yaml"）。
 */
public class TestFluxSubpageFallback extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    private static final String MAIN_PATH = "/nop/test/pages/test-flux-subpage/main.page.yaml";

    @BeforeEach
    public void setUpFlux() {
        setRenderMode("flux");
    }

    @AfterEach
    public void tearDownMode() {
        setRenderMode("amis");
    }

    // fixture 依赖 impl_flux_mode.xpl 的 xlib post-extends 自动切换（编译期 + 缓存），
    // 故切换模式时必须同时清 xlib（重编译 web.xlib）与 xpage（重新生成页面）。
    private void setRenderMode(String mode) {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, mode);
        ResourceComponentManager.instance().clearCache("xlib");
        ResourceComponentManager.instance().clearCache("xpage");
    }

    @Test
    public void testSubpageFallbackFluxMode() {
        Map<String, Object> page = pageProvider.getPage(MAIN_PATH, "");
        assertNotNull(page);
        String json = JSON.serialize(page, true);
        System.out.println("Flux mode subpage fallback JSON:\n" + json);

        // 两个分支（pageId 与直接路径）都应回退到各自 flux.yaml
        assertTrue(json.contains("__SUB_A_FLUX__"), "pageId 分支应回退到 subA.flux.yaml");
        assertTrue(json.contains("__SUB_B_FLUX__"), "直接路径分支应回退到 subB.flux.yaml");
        assertFalse(json.contains("__SUB_A_AMIS__"), "flux 模式不应加载 subA.page.yaml");
        assertFalse(json.contains("__SUB_B_AMIS__"), "flux 模式不应加载 subB.page.yaml");
    }

    @Test
    public void testSubpageFallbackAmisMode() {
        setRenderMode("amis");
        Map<String, Object> page = pageProvider.getPage(MAIN_PATH, "");
        assertNotNull(page);
        String json = JSON.serialize(page, true);
        System.out.println("AMIS mode subpage fallback JSON:\n" + json);

        // amis 模式下两分支都加载 page.yaml（web.xlib:LoadPage 未改，原行为）
        assertTrue(json.contains("__SUB_A_AMIS__"), "amis 模式应加载 subA.page.yaml");
        assertTrue(json.contains("__SUB_B_AMIS__"), "amis 模式应加载 subB.page.yaml");
        assertFalse(json.contains("__SUB_A_FLUX__"), "amis 模式不应加载 flux.yaml");
        assertFalse(json.contains("__SUB_B_FLUX__"), "amis 模式不应加载 flux.yaml");
    }
}
