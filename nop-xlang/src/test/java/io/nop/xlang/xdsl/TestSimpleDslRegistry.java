package io.nop.xlang.xdsl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSimpleDslRegistry extends BaseTestCase {
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
        DynamicObject obj = (DynamicObject) ResourceComponentManager.instance()
                .loadComponentModel("/simple/test.simple");
        assertEquals("a", obj.prop_get("name"));
        assertEquals("b", obj.prop_get("description"));
    }
}
