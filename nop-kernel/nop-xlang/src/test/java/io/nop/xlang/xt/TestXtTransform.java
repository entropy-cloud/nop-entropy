/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.xt.core.XtTransform;
import io.nop.xlang.xt.model.XtTransformModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestXtTransform extends BaseTestCase {
    private static final String XT_SCHEMA = "/nop/schema/xt.xdef";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private XtTransformModel loadModel(String name) {
        return (XtTransformModel) new DslModelParser(XT_SCHEMA).parseFromResource(attachmentResource(name));
    }

    @Test
    public void testSimpleCopy() {
        XtTransformModel model = loadModel("simple-copy.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("simple-copy.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("result", result.getTagName());
        assertEquals(2, result.getChildCount());
    }

    @Test
    public void testEachRule() {
        XtTransformModel model = loadModel("each-rule.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("each-rule.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("items", result.getTagName());
        assertEquals(2, result.getChildCount());
        assertEquals("1", result.child(0).attrText("id"));
        assertEquals("2", result.child(1).attrText("id"));
    }

    @Test
    public void testMapping() {
        XtTransformModel model = loadModel("mapping.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("mapping.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("html", result.getTagName());
    }

    @Test
    public void testIfRule() {
        XtTransformModel model = loadModel("if-rule.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("if-rule.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("enabled", result.child(0).getTagName());
        assertEquals("true", result.child(0).contentText());
    }

    @Test
    public void testChooseRule() {
        XtTransformModel model = loadModel("choose-rule.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("choose-rule.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("TypeB", result.child(0).contentText());
    }

    @Test
    public void testTemplate() {
        XtTransformModel model = loadModel("template.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("template.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("html", result.getTagName());
        assertEquals("header", result.child(0).getTagName());
        assertEquals("body", result.child(1).getTagName());
    }

    @Test
    public void testSimpleValue() {
        XtTransformModel model = loadModel("simple-value.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("simple-value.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("div", result.getTagName());
        assertEquals("test", result.contentText());
    }

    @Test
    public void testRelativePath() {
        XtTransformModel model = loadModel("relative-path.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("relative-path.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("summary", result.getTagName());

        XNode orderId = result.childByTag("orderId");
        assertNotNull(orderId);
        assertEquals("ORDER-001", orderId.contentText());

        XNode items = result.childByTag("items");
        assertNotNull(items);
        assertEquals(2, items.getChildCount());

        XNode item1 = items.child(0);
        assertEquals("ITEM-1", item1.attrText("id"));
    }

    @Test
    public void testIfRuleWithEach() {
        XtTransformModel model = loadModel("if-each.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("if-each.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("result", result.getTagName());
        // 只有一个 item 的 id 为 X，因此只输出一个 match 子节点
        assertEquals(1, result.getChildCount());
        assertEquals("match", result.child(0).getTagName());
        // xt:if 与 match 的属性表达式看到的都是当前迭代节点
        assertEquals("X", result.child(0).attrText("id"));
    }

    @Test
    public void testApplyMappingMatch() {
        XtTransformModel model = loadModel("mapping-match.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("mapping-match.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("result", result.getTagName());
        assertEquals(2, result.getChildCount());
        // foo 命中 match 规则
        assertEquals("fooOut", result.child(0).getTagName());
        // bar 未命中 match，走 default 规则
        assertEquals("otherOut", result.child(1).getTagName());
    }

    @Test
    public void testImportPrefix() {
        XtTransformModel model = loadModel("import-a.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("import-a.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("result", result.getTagName());
        assertEquals(1, result.getChildCount());
        // 通过 ext:t 前缀 id 调用 import-b.xt.xml 中的 template t
        assertEquals("fromImport", result.child(0).getTagName());
    }

    @Test
    public void testImportConflict() {
        // prefix 隔离：A 自有 t 与 import p:t 互不冲突，两个 id 都可解析
        XtTransformModel model = loadModel("import-conflict-a.xt.xml");
        XtTransform transform = new XtTransform(model);
        XNode source = attachmentXml("import-a.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals(2, result.getChildCount());
        assertEquals("fromImport", result.child(0).getTagName());
        assertEquals("localT", result.child(1).getTagName());

        // 无 prefix import 且同 id 直接定义：抛 ERR_XT_IMPORT_CONFLICT
        XtTransformModel conflictModel = loadModel("import-noprefix-conflict.xt.xml");
        NopException e = assertThrows(NopException.class, () -> new XtTransform(conflictModel));
        assertEquals(XLangErrors.ERR_XT_IMPORT_CONFLICT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMappingInherits() {
        XtTransformModel model = loadModel("mapping-inherits.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("mapping-inherits.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals(3, result.getChildCount());
        // a 被子 mapping 覆盖
        assertEquals("childA", result.child(0).getTagName());
        // b 未覆盖，回退父 mapping
        assertEquals("baseB", result.child(1).getTagName());
        // 未命中 match 时使用继承来的 default
        assertEquals("baseDefault", result.child(2).getTagName());
    }

    @Test
    public void testApplyTemplateCircular() {
        XtTransformModel model = loadModel("template-circular.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("template-circular.input.xml");
        NopException e = assertThrows(NopException.class, () -> transform.transform(source));
        assertEquals(XLangErrors.ERR_XT_CIRCULAR_REFERENCE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testApplyTemplateNotFound() {
        XtTransformModel model = loadModel("template-not-found.xt.xml");
        NopException e = assertThrows(NopException.class, () -> new XtTransform(model));
        assertEquals(XLangErrors.ERR_XT_TEMPLATE_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testApplyMappingNotFound() {
        XtTransformModel model = loadModel("mapping-not-found.xt.xml");
        NopException e = assertThrows(NopException.class, () -> new XtTransform(model));
        assertEquals(XLangErrors.ERR_XT_MAPPING_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testApplyTemplateMandatory() {
        XtTransformModel model = loadModel("template-mandatory.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("template-mandatory.input.xml");
        NopException e = assertThrows(NopException.class, () -> transform.transform(source));
        assertEquals(XLangErrors.ERR_XT_MANDATORY_NODE_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testXtValueExpr() {
        XtTransformModel model = loadModel("value-expr.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("value-expr.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("div", result.getTagName());
        assertEquals("x_y", result.contentText());
    }

    @Test
    public void testXtIfWithXPath() {
        XtTransformModel model = loadModel("if-xpath.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode result = transform.transform(attachmentXml("if-xpath-true.input.xml"));
        assertEquals(1, result.getChildCount());
        assertEquals("yes", result.child(0).getTagName());

        result = transform.transform(attachmentXml("if-xpath-false.input.xml"));
        assertEquals(1, result.getChildCount());
        assertEquals("no", result.child(0).getTagName());

        result = transform.transform(attachmentXml("if-xpath-missing.input.xml"));
        assertEquals(1, result.getChildCount());
        assertEquals("none", result.child(0).getTagName());
    }

    @Test
    public void testXtChooseWhen() {
        XtTransformModel model = loadModel("choose-when.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode result = transform.transform(attachmentXml("choose-when-a.input.xml"));
        assertEquals("typeA", result.child(0).getTagName());

        result = transform.transform(attachmentXml("choose-when-b.input.xml"));
        assertEquals("typeB", result.child(0).getTagName());

        result = transform.transform(attachmentXml("choose-when-c.input.xml"));
        assertEquals("unknown", result.child(0).getTagName());
    }

    @Test
    public void testXtScript() {
        XtTransformModel model = loadModel("script-rule.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("script-rule.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals(1, result.getChildCount());
        assertEquals("built", result.child(0).getTagName());
        assertEquals("yes", result.child(0).contentText());
    }

    @Test
    public void testXtGen() {
        XtTransformModel model = loadModel("gen-rule.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("gen-rule.input.xml");
        java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
        items.add(java.util.Map.of("name", "a"));
        items.add(java.util.Map.of("name", "b"));
        items.add(java.util.Map.of("name", "c"));
        XNode result = transform.transform(source, java.util.Collections.singletonMap("items", items));

        assertNotNull(result);
        assertEquals(3, result.getChildCount());
        for (int i = 0; i < 3; i++) {
            XNode row = result.child(i);
            assertEquals("row", row.getTagName());
            assertEquals(String.valueOf((char) ('a' + i)), row.contentText());
        }
    }

    @Test
    public void testParameters() {
        XtTransformModel model = loadModel("params.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("params.input.xml");
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("k", "myTitle");
        params.put("v", "hello");
        XNode result = transform.transform(source, params);

        assertNotNull(result);
        assertEquals("result", result.getTagName());
        assertEquals("myTitle", result.attrText("title"));
        assertEquals("hello", result.contentText());
    }

    @Test
    public void testCustomTagOutput() {
        XtTransformModel model = loadModel("custom-tag.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("custom-tag.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("output", result.getTagName());
        assertEquals("alice", result.attrText("name"));
        assertEquals("val", result.attrText("extra"));
        assertEquals("inner", result.contentText());
    }

    @Test
    public void testXtRuleMandatoryOnPath() {
        XtTransformModel model = loadModel("mandatory-on-path.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("mandatory-on-path.input.xml");
        NopException e = assertThrows(NopException.class, () -> transform.transform(source));
        assertEquals(XLangErrors.ERR_XT_MANDATORY_NODE_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testComplexExample() {
        XtTransformModel model = loadModel("complex-example.xt.xml");
        XtTransform transform = new XtTransform(model);

        XNode source = attachmentXml("complex-example.input.xml");
        XNode result = transform.transform(source);

        assertNotNull(result);
        assertEquals("report", result.getTagName());

        XNode header = result.childByTag("header");
        assertNotNull(header);
        assertEquals("Order Report", header.childByTag("title").contentText());

        XNode items = result.childByTag("items");
        assertNotNull(items);
        assertEquals(3, items.getChildCount());
    }
}
