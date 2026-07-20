package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestRenderModeSwitch extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @BeforeEach
    public void setUpConfig() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "flux");
        ResourceComponentManager.instance().clearCache("xlib");
    }

    @AfterEach
    public void tearDownConfig() {
        AppConfig.getConfigProvider().updateConfigValue(WebConfigs.CFG_WEB_RENDER_MODE, "amis");
        ResourceComponentManager.instance().clearCache("xlib");
    }

    @Test
    public void testWebXlibHasXPostExtends() {
        IXplTagLib lib = XplLibHelper.loadLib("/nop/web/xlib/web.xlib");
        assertNotNull(lib);
        assertNotNull(lib.getTag("GenPage"), "GenPage tag should exist");
    }

    @Test
    public void testFormGenerationUsesFluxViaSwitch() {
        String path = "/nop/test/pages/test-render-mode-switch.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        assertNotNull(page, "page should not be null");

        String json = JSON.serialize(page, true);
        System.out.println("Render mode switch form JSON:\n" + json);

        assertFalse(json.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(json.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
    }

    @Test
    public void testWebPageHelperFixPageSkipsAmisWhenFlux() {
        Map<String, Object> inner = new java.util.HashMap<>(Map.of("type", "group", "body", Map.of("name", "x")));
        Map<String, Object> page = new java.util.HashMap<>(Map.of("dialog", Map.of("body", inner)));
        WebPageHelper.fixPage(page, null, false);

        // flux 模式下 group body 不应被归一化为 Array
        Map<String, Object> dialog = (Map<String, Object>) page.get("dialog");
        Map<String, Object> body = (Map<String, Object>) dialog.get("body");
        assertInstanceOf(Map.class, body, "flux mode: group body should remain a Map");
    }
}
