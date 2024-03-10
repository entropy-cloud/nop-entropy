/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestControlLib extends JunitBaseTestCase {

    @Test
    public void testCustomLib() {
        IXplTagLib lib = XplLibHelper.loadLib("/nop/test/xlib/my.xlib");
        assertTrue(lib.getTag("query-tree-parent") != null);
    }
}
