/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
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
