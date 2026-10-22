/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测试容器注销后（provider 返回空容器），SPI 可选查找必须回落为「未注册」语义。
 * （背景：TestReportDataSecurityHook 曾因 original==null 不恢复导致 fake provider 泄漏到后续测试类。）
 */
public class TestEmptyBeanContainer {
    private static IBeanContainer prev;

    @AfterAll
    public static void restore() {
        // 本测试自身也不得向后续测试类泄漏容器：恢复进入前的注册实例
        BeanContainer.registerInstance(prev != null ? prev : new EmptyBeanContainer());
    }

    @Test
    public void testEmptyContainerYieldsNullSpiLookup() {
        prev = BeanContainer.instance();
        BeanContainer.registerInstance(new EmptyBeanContainer());
        assertNull(IReportAuthChecker.get());
        assertNull(IReportDataSecurityProvider.get());
    }
}
