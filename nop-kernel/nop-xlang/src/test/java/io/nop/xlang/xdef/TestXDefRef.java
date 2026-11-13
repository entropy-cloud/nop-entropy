/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.type.IGenericType;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xdsl.GenericDslParser;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXDefRef extends BaseTestCase {
    @BeforeAll
    public static void setUp() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @DisplayName("测试xdef文件中的ref引用")
    @Test
    public void testXDefRef() {
        IXDefinition def = SchemaLoader.loadXDefinition("/test/test.xdef");
        XNode node = def.toNode();
        node.clearLocation();
        node.dump();

        assertEquals(attachmentXml("test.xdef.resolved.xml").xml(), node.xml());

        XNode reparsed = new XDefinitionParser().parseFromResource(attachmentResource("test.xdef.resolved.xml"))
                .toNode();
        assertEquals(reparsed.xml(), node.xml());
    }

    @DisplayName("测试xdef元文件自身的xdef描述")
    @Test
    public void testXDefXDef() {
        DynamicObject obj = new GenericDslParser().parseFromResource(attachmentResource("test.xdef.resolved.xml"));
        System.out.println(JSON.serialize(obj, true));
        assertEquals(attachmentJsonText("test.xdef.json"), JSON.serialize(obj, true));
    }

    /**
     * xdef:ref不允许循环引用。plan 2504 C2-001 装载状态机落地后行为收敛（设计§3.2.1「不再无限重载」）：
     * 循环引用在 entry 层被同线程重入环检测即时拦截，收敛为平台类型化环错误
     * （load-reentrant-loop，携带 resourcePath，诊断等价），解析层的
     * ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE 依赖装载器重入的旧路径不再触达。
     */
    @DisplayName("xdef:ref不允许循环引用")
    @Test
    public void testLoop() {
        try {
            SchemaLoader.loadXDefinition("/test/loop1.xdef");
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals("nop.err.core.component.resource-cache.load-reentrant-loop", e.getErrorCode());
        }
    }

    @DisplayName("解析obj-schema.xdef")
    @Test
    public void testObjSchema() {
        IXDefinition def = SchemaLoader.loadXDefinition("/nop/schema/schema/obj-schema.xdef");
        def.toNode().clearLocation().dump();
        assertEquals("props", def.getChild("props").getXdefBeanProp());
    }

    @DisplayName("测试嵌套ref")
    @Test
    public void testNestedRef() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/beans.xdef");
        ISchema beanMeta = objMeta.getDefine("BeanValue");
        IObjPropMeta propMeta = beanMeta.getProp("properties");
        assertEquals("io.nop.ioc.model.BeanPropertyModel", propMeta.getComponentType().toString());
    }

    @DisplayName("测试根节点ref")
    @Test
    public void testRootRef() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/xdef.xdef");
        IGenericType type = objMeta.getRootSchema().getExtendsType();
        assertEquals("XDefNode", type.getSimpleClassName());
    }
}
