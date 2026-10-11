/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.junit;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归测试：NopJunitExtension.afterAll 必须恢复配置环境。 测试类通过
 * {@code @NopTestProperty} / {@code setTestConfig} 注入的配置值在 afterAll
 * 之后不可再见，否则会泄漏到同一 fork 中后续不经过本 Extension 的代码（plain
 * JUnit 测试、静态初始化器等）。
 *
 * <p>surefire 配置为 reuseForks=true，同一 JVM 会串行执行多个测试类，配置
 * 环境随类残留即表现为“整个 fork 持久”。
 */
public class TestNopJunitExtensionConfigRestore {

    @Test
    public void testAfterAllRestoresTestConfig() {
        // 动态读取路径：仅经 assignConfigValue 注入、从未静态引用的变量
        String dynName = "nop.autotest.restore-test.dynamic-prop";

        // 静态引用路径：varRef 首次访问时登记系统属性快照（此处系统属性为空，
        // 即干净基线为缺省值）
        IConfigReference<String> staticRef = AppConfig.varRef(null,
                "nop.autotest.restore-test.static-prop", String.class, "default-static");

        NopJunitExtension ext = new NopJunitExtension();

        // 模拟受管测试类的 beforeAll：beginTest + processTestConfig 注入
        BaseTestCase.beginTest();
        BaseTestCase.setTestConfig(dynName, "injected-dynamic");
        BaseTestCase.setTestConfig(staticRef.getName(), "injected-static");

        assertEquals("injected-dynamic", AppConfig.var(dynName, "default-dynamic"));
        assertEquals("injected-static", staticRef.get());

        ext.afterAll(null);

        assertEquals("default-dynamic", AppConfig.var(dynName, "default-dynamic"));
        assertEquals("default-static", staticRef.get());
    }
}
