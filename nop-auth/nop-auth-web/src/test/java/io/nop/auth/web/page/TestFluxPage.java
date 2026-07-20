package io.nop.auth.web.page;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.web.WebConfigs;
import io.nop.web.page.PageProvider;
import io.nop.web.page.WebPageHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig
public class TestFluxPage extends JunitBaseTestCase {

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
    public void testFluxEditControls() {
        String path = "/nop/auth/pages/TestWebControl/edit-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);
        System.out.println(text);

        assertNotNull(page, "page should not be null");
        assertFalse(text.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(text.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
        assertTrue(text.contains("@query:"), "Flux JSON should contain @query: API markers");
    }

    @Test
    public void testFluxViewControls() {
        String path = "/nop/auth/pages/TestWebControl/view-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);
        System.out.println(text);

        assertNotNull(page, "page should not be null");
        assertFalse(text.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(text.contains("\"staticOn\""), "Flux JSON should not contain staticOn");
    }

    @Test
    public void testFluxQueryControls() {
        String path = "/nop/auth/pages/TestWebControl/query-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);
        System.out.println(text);

        assertNotNull(page, "page should not be null");
        assertFalse(text.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(text.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
        assertTrue(text.contains("@query:"), "Flux JSON should contain @query: API markers");
    }

    @Test
    public void testFluxPageStructure() {
        String path = "/nop/auth/pages/TestWebControl/edit-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        System.out.println(JSON.serialize(page, true));

        Object body = page.get("body");
        assertNotNull(body, "page body should exist");
        assertTrue(body instanceof java.util.List || body instanceof Map,
                "page body should be a List or Map");
    }

    @Test
    public void testFluxFormRowUsesFlex() {
        String path = "/nop/auth/pages/TestWebControl/edit-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);

        assertTrue(text.contains("\"flex\""), "Flux should use flex containers for form rows");
    }

    @Test
    public void testFluxWebPageHelperFixPageSkipsAmisWhenFlux() {
        Map<String, Object> inner = new java.util.HashMap<>(Map.of("type", "group", "body", Map.of("name", "x")));
        Map<String, Object> pageData = new java.util.HashMap<>(Map.of("dialog", Map.of("body", inner)));
        WebPageHelper.fixPage(pageData, null, false);

        Map<String, Object> dialog = (Map<String, Object>) pageData.get("dialog");
        Map<String, Object> body = (Map<String, Object>) dialog.get("body");
        assertInstanceOf(Map.class, body, "flux mode: group body should remain a Map");
    }
}
