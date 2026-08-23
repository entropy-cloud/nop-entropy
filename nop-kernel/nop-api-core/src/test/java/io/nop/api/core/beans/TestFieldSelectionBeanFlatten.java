/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFieldSelectionBeanFlatten {

    @Test
    public void testFlattenFields() {
        // root -> { b, c -> { d } }
        FieldSelectionBean c = new FieldSelectionBean();
        c.addField("d");

        FieldSelectionBean root = new FieldSelectionBean();
        root.addField("b");
        root.addField("c", c);

        Set<String> flattened = root.flattenFields();
        // flattenFields必须返回全部字段的点分路径，而不是空集合
        assertEquals(Set.of("b", "c", "c.d"), flattened);
    }

    @Test
    public void testFlattenFieldsSingleLevel() {
        FieldSelectionBean selection = new FieldSelectionBean();
        selection.addField("x");
        selection.addField("y");

        assertEquals(Set.of("x", "y"), selection.flattenFields());
    }
}
