/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.vue;

import io.nop.commons.text.IndentPrinter;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xui.vue.VueNode;
import io.nop.xui.vue.VueTemplateParser;
import io.nop.xui.vue.react.VueNodeToReact;
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
        VueNode vue = parseVue("test-vue-1.xml");
        XNode result = vue.toNode();
        result.dump();
        assertEquals(result.xml(), attachmentXml("test-vue-1.vue.xml").xml());
    }

    private VueNode parseVue(String path) {
        XNode node = attachmentXml(path);
        VueNode vue = VueTemplateParser.INSTANCE.parseTemplate(node);
        return vue;
    }

    @Test
    public void testVueToReact() {
        VueNode vue = parseVue("test-vue-1.xml");
        IndentPrinter out = new IndentPrinter(100);
        new VueNodeToReact().render(vue, out);
        String react = out.toString();
        System.out.println(react);
        assertEquals(normalizeCRLF(react), normalizeCRLF(attachmentText("test-vue-1.react.js")));
    }
}
