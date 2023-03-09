/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.core.initialize.CoreInitialization;
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
}