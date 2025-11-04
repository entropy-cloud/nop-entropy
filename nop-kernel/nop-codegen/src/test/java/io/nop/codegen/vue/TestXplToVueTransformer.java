/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.vue;

import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXplToVueTransformer extends BaseTestCase {
    @Test
    public void testTransform() {
        XNode node = attachmentXml("test-vue.xpage");
        XNode vue = new XplToVueTransformer().transformNode(node);
        assertEquals(attachmentXml("test-vue.vue").xml(), vue.xml());
    }
}
