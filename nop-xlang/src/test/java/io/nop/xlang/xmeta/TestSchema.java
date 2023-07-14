/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSchema {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testReflection() {
        SchemaImpl schema = new SchemaImpl();
        BeanTool.instance().setProperty(schema, "stdDomain", "string");
        assertEquals("string", BeanTool.instance().getProperty(schema, "stdDomain"));
    }

    @Test
    public void testRef() {
        IXDefinition def = SchemaLoader.loadXDefinition("/nop/schema/xmeta.xdef");
        IXDefNode defNode = def.getRootNode().getChild("props").getChild("prop");
        defNode = defNode.getChild("schema").getChild("props").getChild("prop");
        IXDefAttribute domainAttr = defNode.getChild("schema").getAttribute("stdDomain");
        assertNotNull(domainAttr);
    }
}
