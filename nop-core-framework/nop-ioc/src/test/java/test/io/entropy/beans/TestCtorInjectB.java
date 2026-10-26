/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import io.nop.api.core.ioc.IBeanContainer;

/**
 * C2-001 夹具：TestCtorInjectA 的对偶 bean（见其类注释）。
 */
public class TestCtorInjectB {
    public TestCtorInjectB() {
        TestBeanDefFlags.bCtorEnterAndWait();
        IBeanContainer c = TestCtorInjectA.container;
        if (c != null)
            c.getBeanByType(TestCtorInjectA.class);
    }
}
