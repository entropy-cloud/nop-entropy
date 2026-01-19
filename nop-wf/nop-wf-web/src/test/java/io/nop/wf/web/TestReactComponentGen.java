package io.nop.wf.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.web.page.DynamicJsLoader;
import io.nop.xui.vue.VueErrors;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled
@NopTestConfig(localDb = true)
public class TestReactComponentGen extends JunitBaseTestCase {

    @Inject
    DynamicJsLoader jsLoader;

    @Test
    public void testGen() {
        String text = jsLoader.loadText("/nop/wf/designer/dingflow.lib.js");
        System.out.println(text);
    }

    @Test
    public void testGenError() {
        try {
            jsLoader.loadText("/nop/wf/test/gen-error.lib.js");
            fail();
        } catch (NopException e) {
            NopException e2 = (NopException) e.getCause();
            assertEquals(VueErrors.ERR_VUE_INVALID_NODE_TYPE.getErrorCode(), e2.getErrorCode());
        }
    }
}
