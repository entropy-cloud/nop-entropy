package io.nop.web.page;

import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestFluxWebCrudPage extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    private Map<String, Object> getCrud(Map<String, Object> page) {
        Object body = page.get("body");
        assertNotNull(body, "body should contain crud");

        if (body instanceof List) {
            return (Map<String, Object>) ((List<?>) body).get(0);
        }
        return (Map<String, Object>) body;
    }

    @Test
    public void testCrudPageGeneratesFluxJson() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON:\n" + json);

        assertNotNull(page, "page should not be null");

        Map<String, Object> crud = getCrud(page);

        assertNotNull(crud.get("columns"), "crud should have columns");
        List<?> columns = (List<?>) crud.get("columns");
        assertTrue(columns.size() >= 4, "columns should include data columns plus rowActions column");

        Map<String, Object> lastCol = (Map<String, Object>) columns.get(columns.size() - 1);
        assertNotNull(lastCol.get("buttons"), "last column should be rowActions column with buttons");
        assertEquals("right", lastCol.get("fixed"), "rowActions column should be fixed right");

        Object api = crud.get("api");
        assertNotNull(api, "crud should have api");
        Map<String, Object> apiMap = (Map<String, Object>) api;
        assertNotNull(apiMap.get("url"), "api should have url");

        Object footerToolbar = crud.get("footerToolbar");
        if (footerToolbar != null) {
            List<?> ftList = (List<?>) footerToolbar;
            assertFalse(ftList.isEmpty(), "footerToolbar should have elements if present");
        }
    }

    @Test
    public void testAsideFilterForm() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON (aside test):\n" + json);

        Object aside = page.get("aside");
        assertNotNull(aside, "page should have aside filter form");
    }

    @Test
    public void testToolbarContainsActions() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON (toolbar test):\n" + json);

        Map<String, Object> crud = getCrud(page);

        Object toolbar = crud.get("toolbar");
        assertNotNull(toolbar, "crud should have toolbar");

        List<?> toolbarList;
        if (toolbar instanceof List) {
            toolbarList = (List<?>) toolbar;
        } else {
            toolbarList = java.util.Collections.singletonList(toolbar);
        }

        assertFalse(toolbarList.isEmpty(), "toolbar should contain action buttons");

        boolean hasAddButton = false;
        for (Object item : toolbarList) {
            Map<String, Object> btn = (Map<String, Object>) item;
            if ("add-button".equals(btn.get("id"))) {
                hasAddButton = true;
                break;
            }
        }
        assertTrue(hasAddButton, "toolbar should contain add-button");
    }

    @Test
    public void testFluxPropertyNames() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);

        assertFalse(json.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(json.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
        assertFalse(json.contains("\"staticOn\""), "Flux JSON should not contain staticOn");
    }

    @Test
    public void testRowActionsOnClickStructure() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON (onClick test):\n" + json);

        Map<String, Object> crud = getCrud(page);
        List<?> columns = (List<?>) crud.get("columns");
        Map<String, Object> lastCol = (Map<String, Object>) columns.get(columns.size() - 1);
        List<?> buttons = (List<?>) lastCol.get("buttons");

        boolean hasConfirmGuard = false;
        for (Object btn : buttons) {
            Map<String, Object> btnMap = (Map<String, Object>) btn;
            Object onClick = btnMap.get("onClick");
            if (onClick instanceof Map) {
                Map<String, Object> onClickMap = (Map<String, Object>) onClick;
                if ("confirm".equals(onClickMap.get("type"))) {
                    hasConfirmGuard = true;
                    Object when = onClickMap.get("when");
                    assertNotNull(when, "confirm guard should have when");
                    break;
                }
            }
        }
        assertTrue(hasConfirmGuard, "row-delete-button should have confirm guard from confirmText");
    }

    @Test
    public void testTreeParentPickerRendersFluxTreeSelect() {
        String path = "/nop/test/pages/test-flux-tree.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux tree page JSON:\n" + json);

        assertNotNull(page, "tree page should not be null");

        String bodyJson = JSON.serialize(page.get("body"), true);
        assertTrue(bodyJson.contains("tree-select"), "tree page should contain tree-select control");

        assertFalse(json.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(json.contains("\"staticOn\""), "Flux JSON should not contain staticOn");
    }

    @Test
    public void testFluxExpressionCompatibility() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);

        assertTrue(json.contains("@query:") || json.contains("@mutation:"),
                "Flux JSON should contain @query/@mutation API markers");

        assertFalse(json.contains("visibleOn"), "No AMIS visibleOn");
        assertFalse(json.contains("disabledOn"), "No AMIS disabledOn");
    }
}
