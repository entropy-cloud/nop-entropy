package io.nop.web.page;

import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestFluxNormalizeAction extends JunitBaseTestCase {

    public static class MockViewModel {
        public String resourcePath() {
            return "/nop/test/pages";
        }
    }

    private IXplTag getNormalizeActionTag() {
        IXplTagLib lib = XplLibHelper.loadLib("/nop/web/xlib/flux-web.xlib");
        IXplTag tag = lib.getTag("NormalizeAction");
        assertNotNull(tag, "NormalizeAction tag should exist");
        return tag;
    }

    private Object invokeNormalizeAction(Map<String, Object> action, Map<String, Object> genScope) {
        IXplTag tag = getNormalizeActionTag();
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("viewModel", new MockViewModel());
        Map<String, Object> args = new HashMap<>();
        args.put("action", action);
        args.put("genScope", genScope);
        return tag.invokeWithNamedArgs(scope, args);
    }

    private String normalizeAndSerialize(Map<String, Object> action) {
        Object result = invokeNormalizeAction(action, null);
        return JSON.serialize(result, true);
    }

    private Map<String, Object> actionWithId(String id, String label) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", id);
        action.put("label", label);
        return action;
    }

    private Map<String, Object> api(String url, String method) {
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("url", url);
        if (method != null) api.put("method", method);
        return api;
    }

    // ======== Snapshot regeneration ========

    @Disabled("Run to regenerate golden snapshots, copy target output to test resources")
    @Test
    public void regenerateSnapshots() {
        String[][] cases = {
                {"ajax", "{\"id\":\"save-btn\",\"label\":\"Save\",\"actionType\":\"ajax\",\"api\":{\"url\":\"/test/save\",\"method\":\"POST\"}}"},
                {"ajax-no-type", "{\"id\":\"del-btn\",\"label\":\"Delete\",\"api\":{\"url\":\"/test/delete\"}}"},
                {"confirm", "{\"id\":\"del-btn\",\"label\":\"Delete\",\"api\":{\"url\":\"/test/delete\"},\"confirmText\":\"Are you sure?\"}"},
                {"reload", "{\"id\":\"reload-btn\",\"label\":\"Reload\",\"actionType\":\"reload\"}"},
                {"reload-target", "{\"id\":\"reload-btn\",\"label\":\"Reload\",\"actionType\":\"reload\",\"target\":\"myTable\"}"},
                {"close", "{\"id\":\"close-btn\",\"label\":\"Close\",\"actionType\":\"close\"}"},
                {"toast", "{\"id\":\"toast-btn\",\"label\":\"Show Toast\",\"actionType\":\"toast\",\"content\":\"Operation successful\"}"},
                {"link", "{\"id\":\"link-btn\",\"label\":\"Go to Page\",\"actionType\":\"link\",\"link\":\"/my-page\"}"},
                {"url", "{\"id\":\"url-btn\",\"label\":\"Open URL\",\"actionType\":\"url\",\"url\":\"https://example.com\",\"blank\":true}"},
                {"copy", "{\"id\":\"copy-btn\",\"label\":\"Copy\",\"actionType\":\"copy\",\"copyFormat\":\"orderNo\",\"content\":\"ORD-001\"}"},
                {"submit", "{\"id\":\"submit-btn\",\"label\":\"Submit\",\"actionType\":\"submit\"}"},
                {"icon-only", "{\"id\":\"icon-btn\",\"label\":\"Save\",\"icon\":\"fa fa-save\",\"iconOnly\":true,\"level\":\"primary\",\"actionType\":\"reload\"}"},
                {"onclick-passthrough", "{\"id\":\"test-btn\",\"label\":\"Test\",\"onClick\":{\"type\":\"custom\",\"customField\":\"abc\"}}"},
        };

        for (String[] c : cases) {
            String name = c[0];
            String actionJson = c[1];
            Map<String, Object> action = (Map<String, Object>) JSON.parse(actionJson);
            String text = normalizeAndSerialize(action);
            String path = "/io/nop/web/page/normalize-" + name + ".json";
            ResourceHelper.writeText(ResourceHelper.resolve(path), text);
            System.out.println("=== " + name + " ===");
            System.out.println(text);
        }
    }

    // ======== Snapshot-based tests ========

    @Test
    public void testAjaxAction() {
        Map<String, Object> action = actionWithId("save-btn", "Save");
        action.put("actionType", "ajax");
        action.put("api", api("/test/save", "POST"));
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-ajax.json"), text);
    }

    @Test
    public void testAjaxActionNoExplicitType() {
        Map<String, Object> action = actionWithId("del-btn", "Delete");
        action.put("api", api("/test/delete", null));
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-ajax-no-type.json"), text);
    }

    @Test
    public void testConfirmText() {
        Map<String, Object> action = actionWithId("del-btn", "Delete");
        action.put("api", api("/test/delete", null));
        action.put("confirmText", "Are you sure?");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-confirm.json"), text);
    }

    @Test
    public void testReloadAction() {
        Map<String, Object> action = actionWithId("reload-btn", "Reload");
        action.put("actionType", "reload");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-reload.json"), text);
    }

    @Test
    public void testReloadActionWithTarget() {
        Map<String, Object> action = actionWithId("reload-btn", "Reload");
        action.put("actionType", "reload");
        action.put("target", "myTable");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-reload-target.json"), text);
    }

    @Test
    public void testCloseAction() {
        Map<String, Object> action = actionWithId("close-btn", "Close");
        action.put("actionType", "close");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-close.json"), text);
    }

    @Test
    public void testToastAction() {
        Map<String, Object> action = actionWithId("toast-btn", "Show Toast");
        action.put("actionType", "toast");
        action.put("content", "Operation successful");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-toast.json"), text);
    }

    @Test
    public void testLinkAction() {
        Map<String, Object> action = actionWithId("link-btn", "Go to Page");
        action.put("actionType", "link");
        action.put("link", "/my-page");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-link.json"), text);
    }

    @Test
    public void testUrlAction() {
        Map<String, Object> action = actionWithId("url-btn", "Open URL");
        action.put("actionType", "url");
        action.put("url", "https://example.com");
        action.put("blank", true);
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-url.json"), text);
    }

    @Test
    public void testCopyAction() {
        Map<String, Object> action = actionWithId("copy-btn", "Copy");
        action.put("actionType", "copy");
        action.put("copyFormat", "orderNo");
        action.put("content", "ORD-001");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-copy.json"), text);
    }

    @Test
    public void testSubmitAction() {
        Map<String, Object> action = actionWithId("submit-btn", "Submit");
        action.put("actionType", "submit");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-submit.json"), text);
    }

    @Test
    public void testIconOnlyHandling() {
        Map<String, Object> action = actionWithId("icon-btn", "Save");
        action.put("icon", "fa fa-save");
        action.put("iconOnly", true);
        action.put("level", "primary");
        action.put("actionType", "reload");
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-icon-only.json"), text);
    }

    @Test
    public void testOnClickPassthrough() {
        Map<String, Object> action = actionWithId("test-btn", "Test");
        Map<String, Object> onClick = new LinkedHashMap<>();
        onClick.put("type", "custom");
        onClick.put("customField", "abc");
        action.put("onClick", onClick);
        String text = normalizeAndSerialize(action);
        assertEquals(attachmentJsonText("normalize-onclick-passthrough.json"), text);
    }

    // Dialog/drawer require PageProvider/GenPage infra; covered by TestFluxPage end-to-end tests
}
