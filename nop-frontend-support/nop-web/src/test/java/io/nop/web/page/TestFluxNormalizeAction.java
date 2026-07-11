package io.nop.web.page;

import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestFluxNormalizeAction extends JunitBaseTestCase {

    private IXplTag getNormalizeActionTag() {
        IXplTagLib lib = XplLibHelper.loadLib("/nop/web/xlib/flux-web.xlib");
        IXplTag tag = lib.getTag("NormalizeAction");
        assertNotNull(tag, "NormalizeAction tag should exist");
        return tag;
    }

    private Object invokeNormalizeAction(Map<String, Object> action, Map<String, Object> genScope) {
        IXplTag tag = getNormalizeActionTag();
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("viewModel", new HashMap<>());
        Map<String, Object> args = new HashMap<>();
        args.put("action", action);
        args.put("genScope", genScope);
        return tag.invokeWithNamedArgs(scope, args);
    }

    @Test
    public void testOnClickPassthrough() {
        Map<String, Object> onClick = new HashMap<>();
        onClick.put("type", "custom");
        onClick.put("customField", "abc");

        Map<String, Object> action = new HashMap<>();
        action.put("id", "test-btn");
        action.put("label", "Test");
        action.put("onClick", onClick);

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertEquals(onClick, resultMap.get("onClick"),
                "onClick should be passed through directly without conversion");
    }

    @Test
    public void testAjaxActionConvertedToApi() {
        Map<String, Object> api = new HashMap<>();
        api.put("url", "/test/save");
        api.put("method", "POST");

        Map<String, Object> action = new HashMap<>();
        action.put("id", "save-btn");
        action.put("label", "Save");
        action.put("actionType", "ajax");
        action.put("api", api);

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        System.out.println("Ajax action result: " + JSON.serialize(resultMap, true));

        Object onClickObj = resultMap.get("onClick");
        assertNotNull(onClickObj, "onClick should be generated");

        Map<String, Object> onClickMap = (Map<String, Object>) onClickObj;
        assertEquals("api", onClickMap.get("type"), "ajax actionType should convert to type='api'");
        assertEquals("/test/save", onClickMap.get("url"));
    }

    @Test
    public void testAjaxActionWithoutExplicitActionType() {
        Map<String, Object> api = new HashMap<>();
        api.put("url", "/test/delete");

        Map<String, Object> action = new HashMap<>();
        action.put("id", "del-btn");
        action.put("label", "Delete");
        action.put("api", api);

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;

        Object onClickObj = resultMap.get("onClick");
        assertNotNull(onClickObj);
        Map<String, Object> onClickMap = (Map<String, Object>) onClickObj;
        assertEquals("api", onClickMap.get("type"),
                "action with api but no actionType should default to type='api'");
    }

    @Test
    public void testConfirmTextGeneratesConfirmGuard() {
        Map<String, Object> api = new HashMap<>();
        api.put("url", "/test/delete");

        Map<String, Object> action = new HashMap<>();
        action.put("id", "del-btn");
        action.put("label", "Delete");
        action.put("api", api);
        action.put("confirmText", "Are you sure?");

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        System.out.println("Confirm action result: " + JSON.serialize(resultMap, true));

        Object onClickObj = resultMap.get("onClick");
        assertNotNull(onClickObj);
        Map<String, Object> onClickMap = (Map<String, Object>) onClickObj;
        assertEquals("confirm", onClickMap.get("type"),
                "confirmText should generate type='confirm' guard");

        Object when = onClickMap.get("when");
        assertNotNull(when, "confirm guard should have when");
        Map<String, Object> whenMap = (Map<String, Object>) when;
        assertEquals("Are you sure?", whenMap.get("message"));

        Object then = onClickMap.get("then");
        assertNotNull(then, "confirm guard should have then array");
        List<?> thenList = (List<?>) then;
        assertFalse(thenList.isEmpty(), "then should contain the main action");
    }

    @Test
    public void testReloadAction() {
        Map<String, Object> action = new HashMap<>();
        action.put("id", "reload-btn");
        action.put("label", "Reload");
        action.put("actionType", "reload");

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;

        Object onClickObj = resultMap.get("onClick");
        assertNotNull(onClickObj);
        Map<String, Object> onClickMap = (Map<String, Object>) onClickObj;
        assertEquals("component", onClickMap.get("type"),
                "reload actionType should convert to type='component'");
        assertEquals("reload", onClickMap.get("action"));
    }

    @Test
    public void testCloseAction() {
        Map<String, Object> action = new HashMap<>();
        action.put("id", "close-btn");
        action.put("label", "Close");
        action.put("actionType", "close");

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;

        Object onClickObj = resultMap.get("onClick");
        assertNotNull(onClickObj);
        Map<String, Object> onClickMap = (Map<String, Object>) onClickObj;
        assertEquals("component", onClickMap.get("type"));
        assertEquals("close", onClickMap.get("action"));
    }

    @Test
    public void testLabelAndIconHandling() {
        Map<String, Object> action = new HashMap<>();
        action.put("id", "icon-btn");
        action.put("label", "Save");
        action.put("icon", "fa fa-save");
        action.put("iconOnly", true);
        action.put("level", "primary");
        action.put("actionType", "reload");

        Object result = invokeNormalizeAction(action, null);
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertNull(resultMap.get("label"), "iconOnly button should not have label");
        assertEquals("Save", resultMap.get("tooltip"), "iconOnly button should use label as tooltip");
        assertEquals("fa fa-save", resultMap.get("icon"));
        assertNull(resultMap.get("level"), "iconOnly button should not have level");
    }
}
