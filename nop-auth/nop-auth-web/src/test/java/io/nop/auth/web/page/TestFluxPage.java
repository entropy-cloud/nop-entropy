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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Disabled("启用此方法重新生成快照文件，运行后复制 _tmp/ 下输出到 test/resources")
    @Test
    public void regenerateSnapshots() {
        String[][] paths = {
            {"/nop/auth/pages/TestWebControl/edit-flux.page.yaml", "edit-flux.page.json"},
            {"/nop/auth/pages/TestWebControl/view-flux.page.yaml", "view-flux.page.json"},
            {"/nop/auth/pages/TestWebControl/query-flux.page.yaml", "query-flux.page.json"},
        };
        for (String[] pair : paths) {
            Map<String, Object> page = pageProvider.getPage(pair[0], null);
            String text = JSON.serialize(page, true);
            System.out.println("=== " + pair[1] + " ===");
            System.out.println(text);
        }
    }

    @Test
    public void testFluxEditControls() {
        String path = "/nop/auth/pages/TestWebControl/edit-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);

        assertNotNull(page, "page should not be null");
        assertFalse(text.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(text.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
        assertTrue(text.contains("@query:"), "Flux JSON should contain @query: API markers");
        assertEquals(attachmentJsonText("edit-flux.page.json"), text);
    }

    @Test
    public void testFluxViewControls() {
        String path = "/nop/auth/pages/TestWebControl/view-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);

        assertNotNull(page, "page should not be null");
        assertFalse(text.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(text.contains("\"staticOn\""), "Flux JSON should not contain staticOn");
        assertEquals(attachmentJsonText("view-flux.page.json"), text);
    }

    @Test
    public void testFluxQueryControls() {
        String path = "/nop/auth/pages/TestWebControl/query-flux.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        String text = JSON.serialize(page, true);

        assertNotNull(page, "page should not be null");
        assertFalse(text.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(text.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
        assertTrue(text.contains("@query:"), "Flux JSON should contain @query: API markers");
        assertEquals(attachmentJsonText("query-flux.page.json"), text);
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

    @SuppressWarnings("unchecked")
    private void collectOnClickActions(Object node, List<Map<String, Object>> actions) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (map.containsKey("onClick")) {
                Object onClick = map.get("onClick");
                if (onClick instanceof Map) {
                    actions.add((Map<String, Object>) onClick);
                }
            }
            for (Object val : map.values()) {
                collectOnClickActions(val, actions);
            }
        } else if (node instanceof Collection) {
            for (Object item : (Collection<Object>) node) {
                collectOnClickActions(item, actions);
            }
        }
    }

    @Test
    public void testFluxCrudPageActionsUseNativeFormat() {
        String[] pages = {
            "/nop/auth/pages/NopAuthUser/main.page.yaml",
            "/nop/auth/pages/NopAuthRole/main.page.yaml",
            "/nop/auth/pages/NopAuthResource/main.page.yaml",
            "/nop/auth/pages/NopAuthDept/main.page.yaml",
        };

        String[][] expectedActions = {
            {"NopAuthUser", "openDialog", "ajax", "submitForm", "refreshTable"},
            {"NopAuthRole", "openDialog", "ajax", "submitForm", "refreshTable"},
            {"NopAuthResource", "openDialog", "ajax", "submitForm", "refreshTable"},
            {"NopAuthDept", "openDialog", "ajax", "submitForm", "refreshTable"},
        };

        for (int i = 0; i < pages.length; i++) {
            String path = pages[i];
            System.out.println("\n=== Verifying: " + path + " ===");
            Map<String, Object> page = pageProvider.getPage(path, null);
            assertNotNull(page, "Page should not be null: " + path);

            String text = JSON.serialize(page, true);

            // 1. All onClick actions should use native `action` field, not old `type` field
            assertFalse(text.contains("\"type\": \"dialog\""),
                    path + " should not contain old type='dialog' action format");
            assertFalse(text.contains("\"type\": \"api\""),
                    path + " should not contain old type='api' action format");
            assertFalse(text.contains("\"type\": \"component\""),
                    path + " should not contain old type='component' action format");
            assertFalse(text.contains("\"type\": \"toast\""),
                    path + " should not contain old type='toast' action format");
            assertFalse(text.contains("\"type\": \"confirm\""),
                    path + " should not contain old type='confirm' action format");
            assertFalse(text.contains("\"type\": \"sequence\""),
                    path + " should not contain old type='sequence' action format");
            assertFalse(text.contains("\"type\": \"link\""),
                    path + " should not contain old type='link' action format");
            assertFalse(text.contains("\"type\": \"url\""),
                    path + " should not contain old type='url' action format");

            // 2. Collect all onClick objects
            List<Map<String, Object>> actions = new ArrayList<>();
            collectOnClickActions(page, actions);
            System.out.println("Found " + actions.size() + " onClick actions in " + pages[i]);

            // 3. Each onClick must have `action` field
            for (Map<String, Object> act : actions) {
                assertTrue(act.containsKey("action") || act.containsKey("then"),
                        "Each onClick should have 'action' or 'then' field. Found: " + act);
            }

            // 4. Verify key action types exist in the page
            String pageName = expectedActions[i][0];
            int expectedCount = expectedActions[i].length - 1;
            int foundCount = 0;
            for (int j = 1; j < expectedActions[i].length; j++) {
                String expected = expectedActions[i][j];
                if (text.contains("\"action\": \"" + expected + "\"")) {
                    System.out.println("  [OK] Found action: " + expected);
                    foundCount++;
                } else {
                    System.out.println("  [WARN] Action not found: " + expected + " in " + pageName);
                }
            }
            assertTrue(foundCount > 0, pageName + " should have at least one expected action type");
        }
    }

    @Test
    public void testFluxUserMainPageKeyButtons() {
        String path = "/nop/auth/pages/NopAuthUser/main.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, null);
        assertNotNull(page, "NopAuthUser page should not be null");

        List<Map<String, Object>> actions = new ArrayList<>();
        collectOnClickActions(page, actions);
        System.out.println("NopAuthUser-main onClick actions: " + JSON.serialize(actions, true));

        assertFalse(actions.isEmpty(), "NopAuthUser-main should have onClick actions");

        for (Map<String, Object> act : actions) {
            if (act.containsKey("action")) {
                assertNull(act.get("type"), "onClick should not have 'type' field, found: " + act);
            }
        }
    }
}
