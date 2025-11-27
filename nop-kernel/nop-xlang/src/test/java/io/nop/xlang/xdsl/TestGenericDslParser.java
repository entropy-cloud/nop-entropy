/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGenericDslParser extends BaseTestCase {
    @BeforeAll
    public static void setUp() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        GenericDslParser parser = new GenericDslParser();
        DynamicObject obj = parser.parseFromVirtualPath("/test/my.xlib");
        System.out.println(JSON.serialize(obj, true));
    }

    @Test
    public void testUnknownAttr() {
        String xml = "<button id='a' name='b' xui:roles='a' x:schema='/test/test-unknown-attr.xdef' />";
        XNode node = XNode.parse(xml);
        DynamicObject obj = new GenericDslParser().parseFromNode(node);
        assertEquals("{\"attrs\":{\"id\":\"a\",\"name\":\"b\",\"xui:roles\":\"a\"}}", JsonTool.stringify(obj));
    }

    @Test
    public void testParseForEditor() {
        GenericDslParser parser = new GenericDslParser().forEditor(true);
        DynamicObject obj = parser.parseFromVirtualPath("/test/my.xlib");
        System.out.println(JSON.serialize(obj, true));

        assertEquals(normalizeCRLF(attachmentJsonText("my2.xlib.json")), normalizeCRLF(JSON.serialize(obj, true)));
    }

    @Test
    public void testJson() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/xlib.xdef");
        Map<String, Object> json = attachmentBean("my.xlib.json", Map.class);
        XNode node = new DslModelToXNodeTransformer(objMeta).transformToXNode(json);
        node.removeAttr("defaultOutputMode");
        XNode lib = XNodeParser.instance().parseFromVirtualPath("/test/my.xlib");
        assertEquals(normalizeCRLF(lib.xml()), normalizeCRLF(node.xml()));
    }

    @DisplayName("未定义的属性应报错")
    @Test
    public void testParseError() {
        IResource resource = attachmentResource("lib_with_unknown_attr.xml");
        try {
            new GenericDslParser().parseFromResource(resource);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(ERR_XDSL_ATTR_NOT_ALLOWED.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testFilter() {
        XNode node = XNodeParser.instance().parseFromText(null, "<filter x:schema='/nop/schema/query/filter.xdef'><eq name='status' value='1'/></filter>");
        TreeBean bean = (TreeBean) new DslModelParser().parseFromNode(node);
        System.out.println(JsonTool.serialize(bean, true));
    }

    @Test
    public void testXGen() {
        String text = "<view x:schema='/nop/schema/xui/xview.xdef'><x:gen-extends><view xgen-ui:default='true'/></x:gen-extends></view>";
        XNode node = XNodeParser.instance().parseFromText(null, text);
        DynamicObject result = new GenericDslParser().parseFromNode(node);
        System.out.println(JsonTool.serialize(result, true));
        assertEquals("true", result.prop_get("ui:default"));
    }
}