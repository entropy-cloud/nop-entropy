/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestErrorBeanClone {

    @Test
    public void testCloneInstanceKeepsForPublic() {
        ErrorBean error = new ErrorBean("nop.err.test");
        error.setForPublic(true);
        // forPublic是NopRebuildException.rebuild读取的对外可见标记，克隆体不能丢失
        assertTrue(error.cloneInstance().isForPublic(), "clone must keep forPublic flag");
    }

    @Test
    public void testCloneInstanceKeepsForPublicFalse() {
        ErrorBean error = new ErrorBean("nop.err.test");
        error.setForPublic(false);
        assertTrue(!error.cloneInstance().isForPublic());
    }
}
