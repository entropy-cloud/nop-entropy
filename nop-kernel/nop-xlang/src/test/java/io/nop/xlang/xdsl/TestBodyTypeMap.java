package io.nop.xlang.xdsl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestBodyTypeMap extends BaseTestCase {
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
        XNode node = XNode.load("/test/test.map.xml");
        Object obj = new DslModelParser().parseFromNode(node);
        Map<String, Object> map = (Map<String, Object>) BeanTool.getProperty(obj, "resources");
        Object resource = map.get("test");
        assertNotNull(resource);
        assertEquals("/a.html", BeanTool.getProperty(resource, "url"));

        XNode node2 = DslModelHelper.dslModelToXNode("/test/test-body-type-map.xdef", obj);
        node2.dump();
        assertEquals(node.xml(), node2.xml());
    }


    @Test
    public void testParseJson() {

        IObjMeta objMeta = SchemaLoader.loadXMeta("/test/test-body-type-map.xdef");
        String jsonText = " {\n"
                + "                      \"resources\": {\n"
                + "                        \"Sheet_A\": {\n"
                + "                          \"url\": \"http\"\n"
                + "                        }\n"
                + "                      }\n"
                + "                    }";

        XNode xNode = XDslParseHelper.parseXJson(null, jsonText, objMeta);
        xNode.dump();
        Object iComponentModel = new DslModelParser().parseFromNode(xNode);
        assertNotNull(iComponentModel);
    }
}
