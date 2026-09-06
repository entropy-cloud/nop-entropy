/**
 * app-beans-file include pattern 语义回归测试：
 * 修复前 getAppBeansFilter 的多 include pattern 为 AND 语义（任一不匹配即排除），
 * 与同文件 getAutoConfigFilter（OR）相反，多 pattern 配置下几乎全部 beans 资源被静默丢弃；
 * 修复后对齐为 OR：任一 include pattern 命中即保留。
 */
package io.nop.ioc.loader;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAppBeansFilter {

    private Object priorPattern;
    private Object priorSkip;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        priorPattern = AppConfig.getConfigProvider().getConfigValue("nop.ioc.app-beans-file.pattern", null);
        priorSkip = AppConfig.getConfigProvider().getConfigValue("nop.ioc.app-beans-file.skip-pattern", null);
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.pattern", priorPattern);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.skip-pattern", priorSkip);
    }

    private static IResource resource(String path) {
        // 过滤器只读取 path，无需真实文件内容
        return new FileResource(path, new File(path));
    }

    @Test
    public void testMultipleIncludePatternsAreOrSemantics() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.pattern", "/a*,/b*");
        Predicate<IResource> filter = new AppBeanContainerLoader().getAppBeansFilter();

        // 修复前：/a/beans 不匹配 /b* → AND 排除；两条路径全部被静默过滤
        assertTrue(filter.test(resource("/a/app/beans/app.beans.xml")),
                "匹配任一 include pattern 的资源必须保留（OR 语义）");
        assertTrue(filter.test(resource("/b/app/beans/app.beans.xml")),
                "匹配另一个 include pattern 的资源同样保留");
        assertFalse(filter.test(resource("/c/app/beans/app.beans.xml")),
                "不匹配任何 include pattern 的资源被排除");
    }

    @Test
    public void testSkipPatternStillExcludes() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.pattern", "/a*");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.skip-pattern", "*/skip/*");
        Predicate<IResource> filter = new AppBeanContainerLoader().getAppBeansFilter();

        assertTrue(filter.test(resource("/a/app/beans/app.beans.xml")), "include 命中且未命中 skip → 保留");
        assertFalse(filter.test(resource("/a/skip/beans/app.beans.xml")), "skip 命中优先排除");
    }
}
