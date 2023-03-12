/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestOrmCodeGen extends BaseTestCase {
    @BeforeAll
    public static void setUp() {
        System.out.println("setUp");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDict() {
        IResource resource = attachmentResource("test.orm.xlsx");
        Object model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());
        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/orm/orm.xdef", model);
        node.dump();
    }
}
