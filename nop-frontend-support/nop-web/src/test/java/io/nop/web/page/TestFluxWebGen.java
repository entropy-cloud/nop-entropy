package io.nop.web.page;

import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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
}
