package io.nop.xui.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.IObjMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestExtModel extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IObjMeta objMeta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel("/test/test.xmeta");
        UiFormModel form = (UiFormModel) BeanTool.getByIndex(objMeta.prop_get("forms"), 0);
        assertNotNull(form);
        assertEquals("a",form.getId());
        objMeta.prop_get("grids");
    }
}
