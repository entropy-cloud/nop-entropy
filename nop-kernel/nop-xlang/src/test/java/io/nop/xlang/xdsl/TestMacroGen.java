package io.nop.xlang.xdsl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMacroGen extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGenExtends() {
        DynamicObject obj = (DynamicObject) new DslModelParser().parseFromVirtualPath("/test/macro/test-macro-gen.xml");
        IXNodeGenerator generator = (IXNodeGenerator) obj.prop_get("filter");
        XNode node = generator.generateNode(XLang.newEvalScope());
        assertEquals(1, node.child(0).attrInt("value"));
    }
}
