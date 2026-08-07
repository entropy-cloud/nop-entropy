package io.nop.web.page;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestFluxWebGen extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @Test
    public void testLoadLib() {
        IXplTagLib lib = XplLibHelper.loadLib("/nop/web/xlib/flux-web.xlib");
        assertNotNull(lib);

        assertNotNull(lib.getTag("GenPage"), "GenPage tag should exist");
        assertNotNull(lib.getTag("GenForm"), "GenForm tag should exist");
        assertNotNull(lib.getTag("GenFormBody"), "GenFormBody tag should exist");
        assertNotNull(lib.getTag("GenFormSimpleCell"), "GenFormSimpleCell tag should exist");
        assertNotNull(lib.getTag("GenContainerModel"), "GenContainerModel tag should exist");
        assertNotNull(lib.getTag("DefaultControl"), "DefaultControl tag should exist");
        assertNotNull(lib.getTag("NormalizeApi"), "NormalizeApi tag should exist");
        assertNotNull(lib.getTag("FluxFormDefaultAttrs"), "FluxFormDefaultAttrs tag should exist");
        assertNotNull(lib.getTag("FluxGridDefaultAttrs"), "FluxGridDefaultAttrs tag should exist");
        assertNotNull(lib.getTag("FluxPageDefaultAttrs"), "FluxPageDefaultAttrs tag should exist");
    }

    @Test
    public void testGenFormProducesFluxJson() {
        String path = "/nop/test/pages/test-flux-form.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux form JSON:\n" + json);

        assertNotNull(page.get("body"), "body should contain form");

        String bodyJson = JSON.serialize(page.get("body"), true);
        System.out.println("Form body JSON:\n" + bodyJson);

        assertFalse(bodyJson.contains("\"visibleOn\""), "Flux JSON should not contain visibleOn");
        assertFalse(bodyJson.contains("\"disabledOn\""), "Flux JSON should not contain disabledOn");
        assertFalse(bodyJson.contains("\"staticOn\""), "Flux JSON should not contain staticOn");
    }

    @Test
    public void testLayoutStarMarkedFieldIsRequired() {
        String path = "/nop/test/pages/test-flux-form-mandatory-edit.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> emailCell = findCellByName(getBodyList(page), "email");
        assertNotNull(emailCell, "email cell should exist in output");
        assertEquals(Boolean.TRUE, emailCell.get("required"),
                "layout *email must drive required=true even though xmeta prop email is not mandatory");
    }

    @Test
    public void testLayoutStarOverridesPropMetaMandatoryFalse() {
        String path = "/nop/test/pages/test-flux-form-mandatory-override.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> fieldACell = findCellByName(getBodyList(page), "fieldA");
        assertNotNull(fieldACell, "fieldA cell should exist in output");
        assertEquals(Boolean.TRUE, fieldACell.get("required"),
                "layout *fieldA must override xmeta prop mandatory=false (layout level wins)");
    }

    @Test
    public void testLayoutStarMarkedQueryFieldIsRequired() {
        String path = "/nop/test/pages/test-flux-form-mandatory-query.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> statusCell = findCellByName(getBodyList(page), "filter_status");
        assertNotNull(statusCell, "filter_status cell should exist in query form output");
        assertEquals(Boolean.TRUE, statusCell.get("required"),
                "layout *status in query form must drive required=true on filter_status cell");
    }

    @Test
    public void testControlCaseNoStarNoRequired() {
        String path = "/nop/test/pages/test-flux-form-mandatory-no-star.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> nameCell = findCellByName(getBodyList(page), "name");
        assertNotNull(nameCell, "name cell should exist in output");
        assertFalse(nameCell.containsKey("required"),
                "name without * and with non-mandatory xmeta prop should NOT produce required field");
    }

    @Test
    public void testCellRequiredOnMapsToFluxRequiredExpression() {
        String path = "/nop/test/pages/test-flux-form-cell-attrs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> nameCell = findCellByName(getBodyList(page), "name");
        assertNotNull(nameCell, "name cell should exist in output");
        assertEquals("${status == 1}", nameCell.get("required"),
                "cell.requiredOn in view.xml must be mapped to required (expression string) in Flux JSON");
        assertFalse(nameCell.containsKey("requiredWhen"),
                "Flux JSON must NOT contain requiredWhen (it is a ValidationRule.kind, not a schema field)");
    }

    @Test
    public void testCellRequiredExpressionOverridesStaticMandatory() {
        String path = "/nop/test/pages/test-flux-form-cell-attrs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> emailCell = findCellByName(getBodyList(page), "email");
        assertNotNull(emailCell, "email cell should exist in output");
        assertEquals("${a == b}", emailCell.get("required"),
                "cell.requiredOn expression must take precedence over static mandatory");
    }

    @Test
    public void testCellReadonlyOnMapsToFluxReadOnlyExpression() {
        String path = "/nop/test/pages/test-flux-form-cell-attrs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> emailCell = findCellByName(getBodyList(page), "email");
        assertNotNull(emailCell, "email cell should exist in output");
        assertEquals("${status == 2}", emailCell.get("readOnly"),
                "cell.readonlyOn in view.xml must be mapped to readOnly (expression string) in Flux JSON");
    }

    @Test
    public void testCellClearValueOnHiddenMapsToFluxHiddenFieldPolicy() {
        String path = "/nop/test/pages/test-flux-form-cell-attrs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> nameCell = findCellByName(getBodyList(page), "name");
        assertNotNull(nameCell, "name cell should exist in output");
        assertFalse(nameCell.containsKey("clearValueWhenHidden"),
                "Flux JSON must NOT contain top-level clearValueWhenHidden");
        Object policy = nameCell.get("hiddenFieldPolicy");
        assertNotNull(policy, "hiddenFieldPolicy must be emitted when clearValueOnHidden is set");
        assertTrue(policy instanceof Map, "hiddenFieldPolicy must be a nested object");
        @SuppressWarnings("unchecked")
        Map<String, Object> policyMap = (Map<String, Object>) policy;
        assertEquals(Boolean.TRUE, policyMap.get("clearValueWhenHidden"),
                "hiddenFieldPolicy.clearValueWhenHidden must be true");
    }

    @Test
    public void testFormInitFetchMapsToFluxAutoInit() {
        String path = "/nop/test/pages/test-flux-form.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Object autoInit = page.get("autoInit");
        assertEquals(Boolean.FALSE, autoInit,
                "form.initFetch in view.xml must be mapped to autoInit in Flux JSON");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPageTabsEmitsItemsFieldWithKey() {
        String path = "/nop/test/pages/test-flux-tabs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> tabsNode = findNodeByType(page.get("body"), "tabs");
        assertNotNull(tabsNode, "tabs node should exist in page body");

        // 字段名缺陷回归：Flux TabsSchema 读 items；tabs 数组字段必须不存在（silent no-op defect）
        Object items = tabsNode.get("items");
        assertNotNull(items, "tabs node must emit items field (Flux TabsSchema)");
        assertNull(tabsNode.get("tabs"), "tabs node must not emit a 'tabs' array field");
        assertTrue(items instanceof List, "items must be a JSON array");
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
        assertEquals(2, itemsList.size());

        Map<String, Object> pageTab = itemsList.get(0);
        assertEquals("tab-page", pageTab.get("key"), "tab key must come from tab name");
        assertEquals("Page Tab", pageTab.get("title"));
        assertNotNull(pageTab.get("body"), "page tab body must be present (LoadPage result)");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPageTabsBodyTabRendersContainerJson() {
        String path = "/nop/test/pages/test-flux-tabs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> tabsNode = findNodeByType(page.get("body"), "tabs");
        assertNotNull(tabsNode, "tabs node should exist in page body");
        List<Map<String, Object>> items = (List<Map<String, Object>>) tabsNode.get("items");

        Map<String, Object> bodyTab = items.get(1);
        assertEquals("tab-body", bodyTab.get("key"));
        Object tabBody = bodyTab.get("body");
        assertTrue(tabBody instanceof List, "body tab content must be a JSON array");
        List<Map<String, Object>> body = (List<Map<String, Object>>) tabBody;
        assertEquals(1, body.size());
        Map<String, Object> formNode = body.get(0);
        assertEquals("form", formNode.get("type"),
                "body tab content must come from GenContainerModel container dispatch");
        assertNotEquals("page", formNode.get("type"),
                "body tab content must not be a LoadPage result (no page shell)");
        assertEquals("edit", formNode.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFormLevelTabsEmitsItemsBodyRows() {
        String path = "/nop/test/pages/test-flux-tabs.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> tabsNode = findNodeByType(page.get("body"), "tabs");
        assertNotNull(tabsNode, "tabs node should exist in page body");
        List<Map<String, Object>> items = (List<Map<String, Object>>) tabsNode.get("items");
        List<Map<String, Object>> formBody = (List<Map<String, Object>>) items.get(1).get("body");
        Map<String, Object> formTabs = findNodeByType(formBody, "tabs");
        assertNotNull(formTabs, "form-level layoutControl=tabs must emit a tabs node");
        assertEquals("edit-tabs", formTabs.get("id"));

        List<Map<String, Object>> formItems = (List<Map<String, Object>>) formTabs.get("items");
        assertEquals(2, formItems.size());
        Map<String, Object> baseTab = formItems.get(0);
        assertEquals("base", baseTab.get("key"), "form-level tab key must come from group id");
        assertNull(baseTab.get("tab"), "form-level tab must not emit a 'tab' field (silent no-op defect)");
        Object baseBody = baseTab.get("body");
        assertTrue(baseBody instanceof List, "form-level tab content must live in items[].body");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) baseBody;
        assertTrue(rows.size() > 0, "tab body must contain form row JSON");
    }

    @Test
    public void testGenContainerModelUnknownTypeThrows() {
        IXplTagLib lib = XplLibHelper.loadLib("/nop/web/xlib/flux-web.xlib");
        IXplTag tag = lib.getTag("GenContainerModel");
        assertNotNull(tag, "GenContainerModel tag should exist");

        // 防御性测试：合法 schema 下 tab/step 的 name 必填使 page/body/name 三者皆无不可达，
        // 手工构造无 type 的容器模型直接调用分派标签，断言抛 nop.err.web.unknown-page-type
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("viewModel", null);
        scope.setLocalValue("objMeta", null);
        scope.setLocalValue("controlLib", null);
        scope.setLocalValue("bizObjName", null);
        scope.setLocalValue("i18nRoot", null);
        Map<String, Object> args = new HashMap<>();
        args.put("containerModel", new LinkedHashMap<>());

        NopException ex = assertThrows(NopException.class, () -> tag.invokeWithNamedArgs(scope, args));
        assertEquals("nop.err.web.unknown-page-type", ex.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getBodyList(Map<String, Object> page) {
        Object body = page.get("body");
        assertNotNull(body, "page.body should exist");
        assertTrue(body instanceof List, "page.body should be a list");
        return (List<Map<String, Object>>) body;
    }

    /**
     * 页面 body 单一子节点时 xjson 会折叠为对象，多个子节点时为数组；统一归一化为列表遍历。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toBodyList(Object body) {
        if (body == null) return List.of();
        if (body instanceof List) return (List<Map<String, Object>>) body;
        return List.of((Map<String, Object>) body);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWizardPageEmitsStepsWithBody() {        String path = "/nop/test/pages/test-flux-wizard.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> wizardNode = findNodeByType(page.get("body"), "wizard");
        assertNotNull(wizardNode, "wizard node should exist in page body");

        // 属性映射（设计 §3.3）：mode/action*Label 透传；startStep 暂不映射；其余丢弃
        assertEquals("vertical", wizardNode.get("mode"));
        assertEquals("上一步", wizardNode.get("actionPrevLabel"));
        assertEquals("下一步", wizardNode.get("actionNextLabel"));
        assertEquals("完成", wizardNode.get("actionFinishLabel"));
        assertNull(wizardNode.get("startStep"), "startStep must not be mapped (Flux 0-based vs xview 1-based)");
        assertNull(wizardNode.get("className"), "className must be dropped");
        assertNull(wizardNode.get("actionClassName"), "actionClassName must be dropped");
        assertNull(wizardNode.get("initFetch"), "initFetch must be dropped");
        assertNull(wizardNode.get("reload"), "reload must be dropped");

        Object steps = wizardNode.get("steps");
        assertNotNull(steps, "wizard node must emit steps field (Flux WizardSchema)");
        assertTrue(steps instanceof List, "steps must be a JSON array");
        List<Map<String, Object>> stepsList = (List<Map<String, Object>>) steps;
        assertEquals(3, stepsList.size());

        // step 内嵌 simple 容器：body 为容器分派 JSON
        Map<String, Object> stepSimple = stepsList.get(0);
        assertEquals("step-simple", stepSimple.get("key"), "step key must come from step name");
        assertEquals("Step Simple", stepSimple.get("title"));
        List<Map<String, Object>> simpleBody = (List<Map<String, Object>>) stepSimple.get("body");
        assertEquals(1, simpleBody.size());
        assertEquals("form", simpleBody.get(0).get("type"),
                "step body must contain inner form container JSON (GenContainerModel dispatch)");
        assertEquals("step1", simpleBody.get(0).get("name"));

        // step 内嵌 crud 容器
        Map<String, Object> stepCrud = stepsList.get(1);
        assertEquals("step-crud", stepCrud.get("key"));
        List<Map<String, Object>> crudBody = (List<Map<String, Object>>) stepCrud.get("body");
        assertEquals(1, crudBody.size());
        assertEquals("crud", crudBody.get(0).get("type"),
                "step body must contain inner crud container JSON (GenContainerModel dispatch)");

        // step.page 分支：无同名 .page.yaml 时输出 LoadPage 结果（page 外壳）
        Map<String, Object> stepPage = stepsList.get(2);
        assertEquals("step-page", stepPage.get("key"));
        List<Map<String, Object>> pageBody = (List<Map<String, Object>>) stepPage.get("body");
        assertEquals(1, pageBody.size());
        assertEquals("page", pageBody.get(0).get("type"),
                "step with page attribute must contain LoadPage result");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGroupPageEmitsGridSchema() {
        String path = "/nop/test/pages/test-flux-group.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        Map<String, Object> gridNode = findNodeByType(page.get("body"), "grid");
        assertNotNull(gridNode, "grid node should exist in page body (Flux GridSchema)");

        // 属性映射（设计 §3.4）：columns/gap 透传、autoFlow 枚举映射
        assertEquals(2, gridNode.get("columns"));
        assertEquals(8, gridNode.get("gap"));
        assertEquals("row dense", gridNode.get("autoFlow"), "autoFlow row-dense must map to 'row dense'");
        assertNull(gridNode.get("alignItems"),
                "alignItems=baseline must be filtered (not in Flux enum)");
        assertNull(gridNode.get("justifyItems"),
                "justifyItems=normal must be filtered (not in Flux enum)");
        assertNull(gridNode.get("responsiveColumns"),
                "responsiveColumns must not be emitted (xview string vs Flux object type mismatch)");

        Object items = gridNode.get("items");
        assertNotNull(items, "grid node must emit items field");
        assertTrue(items instanceof List, "items must be a JSON array");
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) items;
        assertEquals(2, itemsList.size());

        // 子容器 1：crud + colSpan/rowSpan 透传
        Map<String, Object> itemA = itemsList.get(0);
        assertEquals("grid-a", itemA.get("key"), "grid item key must come from container name");
        assertEquals(2, itemA.get("colSpan"));
        assertEquals(1, itemA.get("rowSpan"));
        List<Map<String, Object>> bodyA = (List<Map<String, Object>>) itemA.get("body");
        assertEquals(1, bodyA.size());
        assertEquals("crud", bodyA.get(0).get("type"),
                "grid item body must contain the crud container JSON");

        // 子容器 2：simple 无 span 字段
        Map<String, Object> itemB = itemsList.get(1);
        assertEquals("form-b", itemB.get("key"));
        assertNull(itemB.get("colSpan"), "simple container has no colSpan, must not be emitted");
        assertNull(itemB.get("rowSpan"), "simple container has no rowSpan, must not be emitted");
        List<Map<String, Object>> bodyB = (List<Map<String, Object>>) itemB.get("body");
        assertEquals(1, bodyB.size());
        assertEquals("form", bodyB.get(0).get("type"),
                "grid item body must contain the simple form container JSON");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testComplexPageEmitsFourSlots() {
        String path = "/nop/test/pages/test-flux-complex.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux complex JSON:\n" + json);

        // complex 页面直接输出 Flux PageSchema：type='page' + header/footer/aside/body 四槽位
        assertEquals("page", page.get("type"), "complex page must emit a page shell (Flux PageSchema)");
        assertEquals("main", page.get("name"));

        // header 槽位：simple 容器 → form（form name 来自 formModel.id）
        Object header = page.get("header");
        assertNotNull(header, "header slot must be emitted");
        assertTrue(header instanceof List, "header must be a JSON array");
        List<Map<String, Object>> headerList = (List<Map<String, Object>>) header;
        assertEquals(1, headerList.size());
        assertEquals("form", headerList.get(0).get("type"),
                "header slot must contain inner simple container JSON (form) via GenContainerModel");
        assertEquals("header-form", headerList.get(0).get("name"),
                "header form name must come from the referenced form id");

        // aside 槽位：crud 容器 → crud（crud name 来自 table.name）
        Object aside = page.get("aside");
        assertNotNull(aside, "aside slot must be emitted");
        assertTrue(aside instanceof List, "aside must be a JSON array");
        List<Map<String, Object>> asideList = (List<Map<String, Object>>) aside;
        assertEquals(1, asideList.size());
        assertEquals("crud", asideList.get(0).get("type"),
                "aside slot must contain inner crud container JSON via GenContainerModel");
        assertEquals("aside-table", asideList.get(0).get("name"),
                "aside crud name must come from table.name");

        // body 槽位：simple 容器 → form
        Object body = page.get("body");
        assertNotNull(body, "body slot must be emitted");
        assertTrue(body instanceof List, "body must be a JSON array");
        List<Map<String, Object>> bodyList = (List<Map<String, Object>>) body;
        assertEquals(1, bodyList.size());
        assertEquals("form", bodyList.get(0).get("type"),
                "body slot must contain inner simple container JSON (form)");
        assertEquals("body-form", bodyList.get(0).get("name"),
                "body form name must come from the referenced form id");

        // footer 槽位：simple 容器 → form
        Object footer = page.get("footer");
        assertNotNull(footer, "footer slot must be emitted");
        assertTrue(footer instanceof List, "footer must be a JSON array");
        List<Map<String, Object>> footerList = (List<Map<String, Object>>) footer;
        assertEquals(1, footerList.size());
        assertEquals("form", footerList.get(0).get("type"),
                "footer slot must contain inner simple container JSON (form)");
        assertEquals("footer-form", footerList.get(0).get("name"),
                "footer form name must come from the referenced form id");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEmbedPageLoadsExternalViewAndMergesOverride() {
        String path = "/nop/test/pages/test-flux-embed.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux embed JSON:\n" + json);

        // embed 加载外部 view.xml 的 crud 页面：type=page（来自外部 crud 页面外壳）
        assertEquals("page", page.get("type"),
                "embed page must load external view's page shell");
        // override 新增 key（外部 crud 页未配 title，merge 后出现）
        assertEquals("__EMBED_OVERRIDE_TITLE__", page.get("title"),
                "embed override must add title via JsonMerger delta merge");
        // override 覆盖既有 key（asideClassName 由 my-aside 改写，证明非空 override 走 merge 而非原样返回）
        assertEquals("__OVERRIDE_ASIDE__", page.get("asideClassName"),
                "embed override must override existing asideClassName via delta merge");
    }

    @Test
    public void testEmbedPageLoadsPageYamlDirectly() {
        String path = "/nop/test/pages/test-flux-embed-yaml.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux embed yaml JSON:\n" + json);

        // embed 直接加载外部 page.yaml：type=page（来自 page.yaml），override 覆盖 title
        assertEquals("page", page.get("type"),
                "embed page.yaml must load the external page shell");
        assertEquals("__EMBED_YAML_OVERRIDE_TITLE__", page.get("title"),
                "embed override must override page.yaml title via delta merge");
        // body 来自 page.yaml，override 未触及，合并后保留
        Object body = page.get("body");
        assertNotNull(body, "page.yaml body must be preserved when override does not touch it");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCellViewOverrideMerges() {
        String path = "/nop/test/pages/test-flux-cell-override.page.yaml";
        Map<String, Object> page = pageProvider.getPage(path, "");
        String json = JSON.serialize(page, true);
        System.out.println("Flux cell override JSON:\n" + json);

        // cell 级 view override：经 GenDispView → GenInputTable 渲染外部 grid 为 array-editor，
        // 再由 applyViewOverride 合并 override。断言合并后的 title 出现在 items 单元格上。
        Map<String, Object> editorControl = findNodeByType(page.get("body"), "array-editor");
        assertNotNull(editorControl,
                "cell with view+grid must render an array-editor (GenInputTable) via GenDispView");
        Map<String, Object> itemsCell = findNodeByNameDeep(page.get("body"), "items");
        assertNotNull(itemsCell, "items cell should exist in form output");
        assertEquals("__CELL_OVERRIDE_TITLE__", itemsCell.get("title"),
                "cell view override must merge title onto the items cell via delta merge");
    }

    @Test
    public void testEmbedPageWithoutRefThrows() {
        String path = "/nop/test/pages/test-flux-embed-bad.page.yaml";
        // embed 指向 view.xml 但未配置 page/grid 引用 → 显式抛错（不静默 noop，对应 Minimum Rule #24）
        NopException ex = assertThrows(NopException.class, () -> pageProvider.getPage(path, ""));
        assertEquals("nop.err.web.embed-page-ref-required", ex.getErrorCode(),
                "embed with view.xml path but no page/grid must throw explicitly");
    }

    /**
     * 深度优先查找指定 type 的节点（不依赖 body 是 List 还是折叠对象）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findNodeByType(Object parentBody, String type) {
        for (Map<String, Object> node : toBodyList(parentBody)) {
            if (type.equals(node.get("type"))) return node;
            Map<String, Object> found = findNodeByType(node.get("body"), type);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 深度优先查找指定 name 的节点（遍历 body 与 columns 等常见子结构）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findNodeByNameDeep(Object parentBody, String name) {
        for (Map<String, Object> node : toBodyList(parentBody)) {
            if (name.equals(node.get("name"))) return node;
            Map<String, Object> found = findNodeByNameDeep(node.get("body"), name);
            if (found != null) return found;
            Object columns = node.get("columns");
            if (columns instanceof List) {
                found = findNodeByNameDeep(columns, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCellByName(List<Map<String, Object>> bodyList, String cellName) {
        for (Map<String, Object> row : bodyList) {
            Object rowBody = row.get("body");
            if (rowBody instanceof Map) {
                Map<String, Object> cell = (Map<String, Object>) rowBody;
                if (cellName.equals(cell.get("name"))) {
                    return cell;
                }
            } else if (rowBody instanceof List) {
                Map<String, Object> found = findCellByName((List<Map<String, Object>>) rowBody, cellName);
                if (found != null) return found;
            }
        }
        return null;
    }
}

