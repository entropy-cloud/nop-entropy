/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C2-002 PBI 互等环夹具 B：构造器等待 A 的 init 已开始（保证 A 的 init owner 已置位），
 * init 阶段经 includeCreating=false 反向获取 A——两等待点互等构成 PBI 等待环。
 */
public class TestPbiRingB {
    public TestPbiRingB() {
        TestPbiRingFlags.markBCtorEntered();
        TestPbiRingFlags.awaitAInitStarted();
    }

    public void initMe() {
        // initMe 由 B 的 init owner 调用（runUntil 已置 initThread）：先放行 A 侧再交叉请求
        TestPbiRingFlags.markBInitStarted();
        io.nop.ioc.api.IBeanContainerImplementor c = TestPbiRingA.container;
        if (c != null)
            c.getBean("pbiRingA", false, null);
    }
}
