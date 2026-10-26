/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import io.nop.ioc.api.IBeanContainerImplementor;

/**
 * C2-001 factoryBean 环夹具：fbProdA 经 factory-bean="fbHolderB" 创建（factoryBean 边
 * 不入构建期拓扑图），而 fbHolderB 的构造器反向获取 fbProdA——两不可见边构成跨线程 BD 互等环。
 */
public class TestFbHolderB {
    /**
     * 测试线程启动前注入容器引用。
     */
    public static volatile IBeanContainerImplementor container;

    public TestFbHolderB() {
        TestBeanDefFlags.bCtorEnterAndWait();
        IBeanContainerImplementor c = container;
        if (c != null)
            c.getBean("fbProdA", false, null);
    }

    public TestFbProductA makeA() {
        return new TestFbProductA();
    }
}
