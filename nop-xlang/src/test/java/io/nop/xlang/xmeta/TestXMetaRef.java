/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.impl.XDefToObjMeta;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXMetaRef extends BaseTestCase {
    @BeforeAll
    public static void setUp() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @DisplayName("xdef转xmeta")
    @Test
    public void testXDefToObjMeta() {
        IXDefinition xdef = new XDefinitionParser().resolveRef(false).parseFromVirtualPath("/test/test.xdef");
        IObjMeta objMeta = new XDefToObjMeta().resolveRef(false).transform(xdef);

        XNode node = objMeta.toNode();
        node.dump();

        assertEquals(normalize(attachmentXml("test.xmeta.unresolved.xml").xml()), node.xml());
    }

    @DisplayName("filter.xdef转objMeta")
    @Test
    public void testXDefToObjMetaForFilter() {
        IXDefinition xdef = new XDefinitionParser().resolveRef(false).parseFromVirtualPath("/test/test-filter.xdef");
        xdef.toNode().clearLocation().dump();
        assertEquals(normalize(attachmentXml("filter.xdef.unresolved.xml").xml()), xdef.toNode().xml());

        IObjMeta objMeta = new XDefToObjMeta().resolveRef(false).transform(xdef);

        XNode node = objMeta.toNode();
        node.dump();

        assertEquals(normalize(attachmentXml("filter.xmeta.unresolved.xml").xml()), node.xml());

        objMeta = new XDefToObjMeta().resolveRef(true).transform(xdef);
        node = objMeta.toNode();
        node.dump();

        assertEquals(normalize(attachmentXml("filter.xmeta.resolved.xml").xml()), node.xml());
    }

    @DisplayName("xdef转xmeta,且resolveRef")
    @Test
    public void testXDefToObjMetaResolved() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/test/test.xdef");

        XNode node = objMeta.toNode().clearLocation();
        node.dump();

        assertEquals(normalize(attachmentXml("test.xmeta.resolved.xml").xml()), node.xml());
    }

    String normalize(String s) {
        return StringHelper.replace(s, "\r\n", "\n");
    }

    @DisplayName("解析xlib.xdef")
    @Test
    public void testXlib() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/xlib.xdef");
        objMeta.toNode().dump();
        assertTrue(objMeta.getDefine("XplTag") != null);

        for (IObjSchema schema : objMeta.getDefinedObjSchemas()) {
            assertTrue(schema.getType() != PredefinedGenericTypes.ANY_TYPE);
        }

        IObjSchema schema = objMeta.getDefine("XplTagLib");
        assertEquals("XplTagLib", schema.getSimpleClassName());
        assertTrue(schema.getLocalProps().size() > 0);
    }

    @DisplayName("测试union类型")
    @Test
    public void testUnion() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/orm/dialect.xdef");
        objMeta.toNode().dump();
        assertEquals("type", objMeta.getProp("functions").getSchema().getItemSchema().getSubTypeProp());
    }

    @DisplayName("测试xdef:bean-body-type")
    @Test
    public void testBeanBodyType() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/beans.xdef");
        ISchema propValue = objMeta.getDefine("BeanPropValue");
        ISchema body = propValue.getProp("body").getSchema();
        assertEquals("IBeanPropValue", body.getType().getSimpleClassName());
    }

    @Test
    public void testMetaGen() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/schema/obj-schema.xdef");
        ISchema propSchema = objMeta.getDefine("ObjPropMetaImpl");
        assertNotNull(propSchema.getProp("lazy"));
    }

    @Test
    public void testNoReflection() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(SchemaImpl.class);
        classModel.getBeanModel();
        assertNull(classModel.getMethodsByName("isAbstract"));
    }
}
