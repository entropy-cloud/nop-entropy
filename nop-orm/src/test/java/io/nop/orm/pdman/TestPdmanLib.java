/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.pdman;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.GenericDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPdmanLib extends BaseTestCase {
    @BeforeAll
    public static void init() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGen() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/test.orm.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.clearLocation().dump();
        assertEquals(attachmentXml("pdman.result.xml").xml(), node.xml());

        DynamicObject obj = new GenericDslParser().parseFromResource(resource);
        String json = JsonTool.stringify(obj, null, "  ");
        System.out.println(json);
        assertEquals(attachmentJsonText("pdman.result.json"), json);
    }
}
