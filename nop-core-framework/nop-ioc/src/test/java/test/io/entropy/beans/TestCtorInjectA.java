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
 * C2-001 夹具：构造器级跨 bean 依赖（type-injection 边族的不可见边形态）。
 * 构造器在创建临界区内解析对偶 bean——该边不进构建期拓扑图。
 * 通过静态 latch 强制两线程各持一个 BD 锁后再互请求，复现跨线程 BD monitor 互等。
 */
public class TestCtorInjectA {
    /**
     * 测试线程启动前注入容器引用。
     */
    public static volatile IBeanContainer container;

    public TestCtorInjectA() {
        TestBeanDefFlags.aCtorEnterAndWait();
        IBeanContainer c = container;
        if (c != null)
            c.getBeanByType(TestCtorInjectB.class);
    }
}
