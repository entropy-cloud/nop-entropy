/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestResourceHelper {
    @Test
    public void testName() {
        assertEquals("test", ResourceHelper.getName("c:/a/test"));
        assertEquals("test", ResourceHelper.getName("c:test"));
    }

    @Test
    public void testCheckValidPath(){
        ResourceHelper.checkNormalVirtualPath("/_delta/default/nop/web/xlib/web/impl_GenPage.xpl");
    }
}
