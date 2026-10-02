/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.util.Symbol;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFieldSelectionPrinter {

    /**
     * 构造一个可修改的子选择对象（hasNext=true 保证返回非冻结实例）
     */
    private FieldSelectionBean subSelection(FieldSelectionBean root, String name) {
        return root.addCompositeField(name, true);
    }

    @Test
    public void testPrintSimpleFieldsCommaSeparated() {
        FieldSelectionBean bean = FieldSelectionBean.fromProp("a", "b", "c");
        assertEquals("a,b,c", bean.toString(false));
        // pretty 模式下平铺字段以换行分隔（无子字段时不输出花括号）
        assertEquals("a\nb\nc", FieldSelectionPrinter.instance().print(bean, true));
    }

    @Test
    public void testPrintAliasUsesColonSyntax() {
        FieldSelectionBean bean = new FieldSelectionBean();
        FieldSelectionBean sub = new FieldSelectionBean("sourceName");
        bean.addField("alias", sub);
        assertEquals("alias:sourceName", bean.toString(false));
    }

    @Test
    public void testPrintNestedFieldsWithBraces() {
        FieldSelectionBean bean = FieldSelectionBean.fromProp("a.b", "a.c");
        assertEquals("a{b,c}", bean.toString(false));
    }

    @Test
    public void testPrettyPrintUsesNewlineAndIndent() {
        FieldSelectionBean bean = FieldSelectionBean.fromProp("a.b");
        String pretty = bean.toString(true);
        assertEquals("a{\n  b\n}", pretty);
    }

    @Test
    public void testPrintArgsEncodesByValueType() {
        FieldSelectionBean bean = new FieldSelectionBean();
        FieldSelectionBean sub = subSelection(bean, "a");
        sub.setArg("num", 1);
        sub.setArg("str", "x");
        sub.setArg("flag", Boolean.TRUE);
        // Symbol 按变量引用输出（不带引号）
        sub.setArg("ref", Symbol.of("varName"));
        // List 展开为 [..]
        sub.setArg("list", Arrays.asList(1, "y"));
        assertEquals("a(num:1,str:\"x\",flag:true,ref:varName,list:[1,\"y\"])", bean.toString(false));
    }

    @Test
    public void testPrintObjectArgAndDollarKeyQuoting() {
        FieldSelectionBean bean = new FieldSelectionBean();
        FieldSelectionBean sub = subSelection(bean, "a");
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", "n");
        obj.put("$where", "cond");
        sub.setArg("input", obj);
        // $ 开头的 key 需要加引号
        assertEquals("a(input:{name:\"n\",\"$where\":\"cond\"})", bean.toString(false));
    }

    @Test
    public void testPrintDirectives() {
        FieldSelectionBean bean = new FieldSelectionBean();
        FieldSelectionBean sub = subSelection(bean, "a");
        sub.setDirectiveArg("auth", "role", "admin");
        assertEquals("a @auth(role:\"admin\")", bean.toString(false));
    }

    @Test
    public void testPrintCompositeSelectionWithArgsAndChildren() {
        FieldSelectionBean bean = new FieldSelectionBean();
        FieldSelectionBean sub = subSelection(bean, "user");
        sub.setArg("id", 1);
        bean.addCompositeField("user.name", false);
        assertEquals("user(id:1){name}", bean.toString(false));
    }
}
