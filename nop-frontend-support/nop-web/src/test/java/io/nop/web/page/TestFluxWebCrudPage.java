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

        // 默认多选 checkbox
        assertEquals(Map.of("type", "checkbox"), crud.get("selection"),
                "crud should default to checkbox selection");

        // 序号列：固定左侧、宽度 50、居中
        Map<String, Object> firstCol = (Map<String, Object>) columns.get(0);
        assertEquals("index", firstCol.get("type"), "first column should be index column");
        assertEquals("left", firstCol.get("fixed"), "index column should be fixed left");
        assertEquals("50", String.valueOf(firstCol.get("width")), "index column width should be 50");
        assertEquals("center", firstCol.get("align"), "index column should be centered");

        // 首个数据列固定左侧（GenGridCol colIndex==0 → left）
        Map<String, Object> secondCol = (Map<String, Object>) columns.get(1);
        assertEquals("left", secondCol.get("fixed"), "first data column should be fixed left");

        Map<String, Object> lastCol = (Map<String, Object>) columns.get(columns.size() - 1);
        assertNotNull(lastCol.get("buttons"), "last column should be rowActions column with buttons");
        assertEquals("right", lastCol.get("fixed"), "rowActions column should be fixed right");

        Object loadAction = crud.get("loadAction");
        assertNotNull(loadAction, "crud should have loadAction");
        Map<String, Object> loadActionMap = (Map<String, Object>) loadAction;
        assertEquals("ajax", loadActionMap.get("action"), "loadAction should be ajax action");
        Object args = loadActionMap.get("args");
        assertNotNull(args, "loadAction should have args");
        assertNotNull(((Map<?, ?>) args).get("url"), "loadAction args should have url");

        // footerToolbar 现为 flux 分页启用标记（空 list），具体渲染由 flux 运行时默认填充，
        // 参见 grid_crud.xpl 的 <footerToolbar j:list="true" xpl:if="pager != 'none'"/>。
        Object footerToolbar = crud.get("footerToolbar");
        if (footerToolbar != null) {
            assertTrue(footerToolbar instanceof List, "footerToolbar should be a list if present");
        }
    }

    @Test
    public void testQueryFormHorizontalLayout() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON (queryForm layout test):\n" + json);

        Map<String, Object> crud = getCrud(page);

        Object queryForm = crud.get("queryForm");
        assertNotNull(queryForm, "crud with filterForm should generate queryForm");
        Map<String, Object> queryFormMap = (Map<String, Object>) queryForm;

        assertEquals("horizontal", queryFormMap.get("mode"),
                "queryForm should default to horizontal label layout (G-001 后校验器直读 mode)");

        Object labelWidth = queryFormMap.get("labelWidth");
        assertNotNull(labelWidth, "queryForm should have uniform default labelWidth");
        assertEquals(80, ((Number) labelWidth).intValue(),
                "queryForm default labelWidth should be 80");
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
                if ("ajax".equals(onClickMap.get("action")) && onClickMap.get("confirmText") != null) {
                    hasConfirmGuard = true;
                    break;
                }
            }
        }
        assertTrue(hasConfirmGuard, "row-delete-button should have confirmText on the ajax action");
    }

    @Test
    public void testDialogCloseOnSubmitAndSubmitButton() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON (dialog closeOnSubmit test):\n" + json);

        Map<String, Object> crud = getCrud(page);
        List<?> toolbar = (List<?>) crud.get("toolbar");

        Map<String, Object> addButton = null;
        for (Object item : toolbar) {
            Map<String, Object> btn = (Map<String, Object>) item;
            if ("add-button".equals(btn.get("id"))) {
                addButton = btn;
                break;
            }
        }
        assertNotNull(addButton, "add-button should exist");

        Map<String, Object> onClick = (Map<String, Object>) addButton.get("onClick");
        assertNotNull(onClick, "add-button should have onClick");
        assertEquals("openDialog", onClick.get("action"), "add-button should open a dialog");

        Map<String, Object> args = (Map<String, Object>) onClick.get("args");
        assertNotNull(args, "openDialog should have args");
        assertEquals(Boolean.TRUE, args.get("closeOnSubmit"),
                "dialog should default to closeOnSubmit: true (AMIS semantic, Enter submit closes too)");

        // owner 侧 onSubmitSuccess 保留：提交成功后刷新下层列表
        Object onSubmitSuccess = args.get("onSubmitSuccess");
        assertNotNull(onSubmitSuccess, "openDialog args should keep onSubmitSuccess for owner-side refresh");

        // dialog body 内 form 的提交按钮：submitForm 触发提交，关闭由 closeOnSubmit 统一处理。
        // page_simple.xpl 把缺省按钮渲染在 page 级 actions，openDialog 展开后位于 args.actions。
        Object body = args.get("body");
        assertNotNull(body, "dialog args should have body");
        List<?> dialogActions = (List<?>) args.get("actions");
        assertNotNull(dialogActions, "dialog args should have page-level actions");
        Map<String, Object> submitButton = null;
        for (Object a : dialogActions) {
            Map<String, Object> action = (Map<String, Object>) a;
            if ("_default_submit".equals(action.get("id"))) {
                submitButton = action;
                break;
            }
        }
        assertNotNull(submitButton, "form should have default submit button");
        Map<String, Object> submitOnClick = (Map<String, Object>) submitButton.get("onClick");
        assertNotNull(submitOnClick, "submit button should have onClick");
        assertEquals("submitForm", submitOnClick.get("action"), "submit button should trigger submitForm");
        assertFalse(submitOnClick.containsKey("then"),
                "submit button should not carry then closeSurface (closeOnSubmit handles closing)");
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

    @Test
    public void testAsidePropertiesAreEmittedInFlux() {
        String path = "/nop/test/pages/test-flux-crud.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux CRUD page JSON (aside emitted test):\n" + json);

        assertTrue(json.contains("\"asideSticky\""),
                "Flux PageDefaultAttrs should emit asideSticky (Flux PageSchema supports it)");
        assertTrue(json.contains("\"asideResizable\""),
                "Flux PageDefaultAttrs should emit asideResizable (Flux naming for AMIS asideResizor)");
        assertFalse(json.contains("\"asideResizor\""),
                "Flux JSON must NOT contain AMIS naming asideResizor (renamed to asideResizable)");
        assertTrue(json.contains("\"asideMinWidth\""),
                "Flux PageDefaultAttrs should emit asideMinWidth");
        assertTrue(json.contains("\"asideMaxWidth\""),
                "Flux PageDefaultAttrs should emit asideMaxWidth");
        assertTrue(json.contains("\"asideClassName\""),
                "Flux PageDefaultAttrs should emit asideClassName");
    }
}
