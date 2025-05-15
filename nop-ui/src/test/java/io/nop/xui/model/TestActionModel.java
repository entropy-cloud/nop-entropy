package io.nop.xui.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestActionModel extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSimpleData() {
        IResource resource = attachmentResource("test.action.xml");
        UiActionModel action = (UiActionModel) DslModelHelper.loadDslModel(resource);
        assertEquals("${dataSource}", action.getApi().getData());
    }
}
