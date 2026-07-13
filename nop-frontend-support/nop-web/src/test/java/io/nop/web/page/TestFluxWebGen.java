package io.nop.web.page;

import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getBodyList(Map<String, Object> page) {
        Object body = page.get("body");
        assertNotNull(body, "page.body should exist");
        assertTrue(body instanceof List, "page.body should be a list");
        return (List<Map<String, Object>>) body;
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

