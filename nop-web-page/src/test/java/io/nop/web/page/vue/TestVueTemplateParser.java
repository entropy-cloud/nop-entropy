package io.nop.web.page.vue;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestVueTemplateParser extends BaseTestCase {
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
        XNode node = attachmentXml("test-vue-1.xml");
        VueNode vue = VueTemplateParser.INSTANCE.parseTemplate(node);
        XNode result = vue.toNode();
        result.dump();
        assertEquals(result.xml(), attachmentXml("test-vue-1.vue.xml").xml());
    }
}
