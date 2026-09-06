/**
 * 多占位符配置表达式回归测试：
 * 修复前 parseSpringExpr 的 configVars 列表在整个表达式内共享累积，${a}:${b} 的第二个
 * 占位符持有 [a,b]，a 已配置时注入 "aVal:aVal"（静默错误值）；
 * 修复后每个 ${} 占位符独立解析，注入 "aVal:bVal"。嵌套默认值 ${a:${b}} 的
 * fallback 链语义保持不变。
 */
package io.nop.ioc;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyConfigVarBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMultiConfigVarExpression extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private String resolveValue(String resource) {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource(resource));
        try {
            container.start();
            return ((MyConfigVarBean) container.getBean("myConfigVarBean")).getValue();
        } finally {
            container.stop();
        }
    }

    @Test
    public void testSiblingPlaceholdersResolveIndependently() {
        Object priorA = AppConfig.getConfigProvider().getConfigValue("test.multi-var.a", null);
        Object priorB = AppConfig.getConfigProvider().getConfigValue("test.multi-var.b", null);
        try {
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.a", "aVal");
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.b", "bVal");

            // 修复前：第二个占位符持有 [a,b] 按序取到 a 的值，注入 "aVal:aVal"
            assertEquals("aVal:bVal", resolveValue("test_multi_config_vars.beans.xml"),
                    "两个占位符必须各解析各的配置变量");
        } finally {
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.a", priorA);
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.b", priorB);
        }
    }

    @Test
    public void testNestedDefaultFallbackPreserved() {
        // 嵌套默认值 ${a:${b}}：a 未配置时回退 b（fallback 链语义在多占位符修复后保持不变）
        Object priorA = AppConfig.getConfigProvider().getConfigValue("test.multi-var.a", null);
        Object priorB = AppConfig.getConfigProvider().getConfigValue("test.multi-var.b", null);
        try {
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.a", null);
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.b", "bVal");

            assertEquals("bVal", resolveValue("test_nested_fallback.beans.xml"),
                    "a 未配置时必须回退到嵌套默认值 b");
        } finally {
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.a", priorA);
            AppConfig.getConfigProvider().assignConfigValue("test.multi-var.b", priorB);
        }
    }
}
