/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.selection;

import io.nop.api.core.beans.FieldSelectionBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestFieldSelectionParser {
    @Test
    public void testParse() {
        String text = "a,b,c\nd{e,f(x:2){c,d}}";
        FieldSelectionBean selection = new FieldSelectionBeanParser().parseFromText(null, text);

        String formatted = selection.toString(true);
        System.out.println(formatted);

        selection = new FieldSelectionBeanParser().parseFromText(null, formatted);
        String compact = selection.toString(false);
        assertEquals("a,b,c,d{e,f(x:2){c,d}}", compact);
    }

    @Test
    public void testFields() {
        FieldSelectionBean selection = new FieldSelectionBean();
        selection.addField("a");
        selection.addCompositeField("a.b", true);
        selection.addCompositeField("a.b.c", true);
        assertNotNull(selection.getField("a").getField("b").getField("c"));
    }

    @Test
    public void testDirective() {
        String text = "a,b,children @TreeChildren( max : 5 ),  d";
        FieldSelectionBean selection = new FieldSelectionBeanParser().parseFromText(null, text);

        String formatted = selection.toString(false);
        System.out.println(formatted);
        assertEquals("a,b,children @TreeChildren(max:5),d", formatted);

        FieldSelectionBean base = new FieldSelectionBean();
        base.addField("a");
        base.addField("u");
        base.merge(selection);
        assertEquals("a,u,b,children @TreeChildren(max:5),d", base.toString(false));
    }
}
