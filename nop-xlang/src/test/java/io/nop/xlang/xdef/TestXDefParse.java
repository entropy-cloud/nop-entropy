/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXDefParse {
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
        new XDefinitionParser().parseFromResource(VirtualFileSystem.instance().getResource("/nop/schema/xdef.xdef"));

        IXDefinition def = new XDefinitionParser().parseFromResource(VirtualFileSystem.instance().getResource("/nop/schema/xdsl.xdef"));
        assertTrue(def.getRootNode().getAttribute("x:schema") != null);

        Collection<? extends IResource> files = VirtualFileSystem.instance().getAllResources("/nop/schema", ".xdef");
        for (IResource file : files) {
            new XDefinitionParser().parseFromResource(file);
        }
    }

    @Test
    public void testXDefToXMeta() {
        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/orm/orm.xdef");
        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/xmeta.xdef",objMeta);
        node.dump();
    }
}
