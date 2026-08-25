/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * 回归：freeze(false)遵循IFreezable的cascade约定——只冻结自身持有的Map包装，
     * 不深度冻结args/directives的嵌套值；freeze(true)仍深度冻结（嵌套Map经args取出为不可变视图）。
     */
    @Test
    public void testFreezeCascadeSemantics() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", 1);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("opts", nested);

        FieldSelectionBean selection = new FieldSelectionBean();
        selection.setArgs(args);
        selection.addField("f");

        // cascade=false：顶层args被冻结（不可增删entry），嵌套map保持可变
        selection.freeze(false);
        assertThrows(UnsupportedOperationException.class, () -> selection.getArgs().put("newKey", 2));
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedView = (Map<String, Object>) selection.getArgs().get("opts");
        nestedView.put("inner2", 2);

        // cascade=true：嵌套map经args取出为不可变视图
        FieldSelectionBean deep = new FieldSelectionBean();
        Map<String, Object> nested2 = new LinkedHashMap<>();
        Map<String, Object> args2 = new LinkedHashMap<>();
        args2.put("opts", nested2);
        deep.setArgs(args2);
        deep.freeze(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedView2 = (Map<String, Object>) deep.getArgs().get("opts");
        assertThrows(UnsupportedOperationException.class, () -> nestedView2.put("inner", 1));
    }
}
