/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.parse;

import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.xjava.JavaObjMetaParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaObjMetaParser extends BaseTestCase {

    @Test
    public void testJava() {
        IResource resource = attachmentResource("TestAST.java");
        ObjMetaImpl objMeta = new JavaObjMetaParser().parseFromResource(resource);
        assertEquals("TestAST", objMeta.getName());

        assertTrue(objMeta.getDefine("Program").getProp("body").isMandatory());

        ISchema declMeta = objMeta.getDefine("FunctionDeclaration");
        assertFalse(declMeta.getProp("decorators").isMandatory());
        assertTrue(declMeta.getProp("name").isMandatory());

        assertNotNull(objMeta.getDefine("Literal"));
        assertTrue(objMeta.getDefine("Expression").isAbstract());

        ISchema ifExpr = objMeta.getDefine("IfStatement");
        IObjPropMeta prop = ifExpr.getProp("consequent");
        assertNotNull(objMeta.getDefine(prop.getSimpleClassName()));

        ISchema block = objMeta.getDefine("BlockStatement");
        IObjPropMeta items = block.getProp("body");
        assertEquals("Expression", items.getItemSimpleClassName());

        assertNotNull(objMeta.getDefine("NamedTypeNode"));

        assertNull(block.getImplementsTypes());

        ISchema func = objMeta.getDefine("FunctionDeclaration");
        assertNotNull(func.getProp("decorators"));

    }
}