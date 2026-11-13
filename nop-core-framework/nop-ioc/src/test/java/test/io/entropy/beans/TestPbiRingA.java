/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C2-002 PBI 互等环夹具 A：init 阶段经 includeCreating=false 获取对偶 bean（等待语义）。
 */
public class TestPbiRingA {
    /**
     * 测试线程启动前注入容器引用。
     */
    public static volatile io.nop.ioc.api.IBeanContainerImplementor container;

    public void initMe() {
        TestPbiRingFlags.markAInitStarted();
        // 等 B 的 init 已启动（B 的 init owner 已置位）：保证双 owner 就位后再交叉请求，
        // 排除 init 所有权被单线程吞并的竞态（该竞态下缺陷被 selfWait fail-fast 掩护）
        TestPbiRingFlags.awaitBInitStarted();
        io.nop.ioc.api.IBeanContainerImplementor c = container;
        if (c != null)
            c.getBean("pbiRingB", false, null);
    }
}
