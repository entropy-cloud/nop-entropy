/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.imp;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.imp.model.ImportModel;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXlsxObjectLoader extends BaseTestCase {
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
        ImportModel importModel = (ImportModel) new DslModelParser()
                .parseFromResource(attachmentResource("test.imp.xml"));

        XlsxObjectLoader parser = new XlsxObjectLoader(importModel);

        DynamicObject bean = (DynamicObject) parser.loadObjectFromResource(new ClassPathResource("classpath:xlsx/test-imp.xlsx"));
        System.out.println(JsonTool.stringify(bean, null, "  "));
        assertEquals("/nop/schema/orm/orm.xdef", bean.prop_get(XDslKeys.DEFAULT.SCHEMA));

        IObjMeta meta = SchemaLoader.loadXMeta("/nop/schema/orm/orm.xdef");
        XNode node = new DslModelToXNodeTransformer(meta).transformToXNode(bean);
        node.dump();
        assertEquals(attachmentXml("result.orm.xml").xml(), node.xml());

        Object bean2 = new DslModelParser().dynamic(true).parseFromNode(node);
        XNode node2 = new DslModelToXNodeTransformer(meta).transformToXNode(bean2);
        assertEquals(node.xml(), node2.xml());
    }

    @Test
    public void testReflection() {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(XDslConstants.EXCEL_MODEL_LOADER_CLASS);
        IFunctionModel fn = classModel.getConstructor(new Class[]{String.class});
        assertEquals(1, fn.getArgCount());
    }
}
