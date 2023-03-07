/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSchema {
    @Test
    public void testReflection() {
        SchemaImpl schema = new SchemaImpl();
        BeanTool.instance().setProperty(schema, "stdDomain", "string");
        assertEquals("string", BeanTool.instance().getProperty(schema, "stdDomain"));
    }
}
