/**
 * dumpDisabled 的 missing-class 分支回归测试：
 * 修复前该分支误遍历 getOnClass()（仅配置 missing-class 时为 null），debug 日志开启时
 * 容器构建直接 NPE；修复后正确遍历 getMissingClass()，禁用原因正常输出。
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.ioc.loader.BeanConditionEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestBeanConditionMissingClassDump extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMissingClassOnlyConditionDumpsWithoutNpe() {
        // dumpConditional 有 isDebugEnabled 守卫：显式开启 BeanConditionEvaluator 的 debug
        // 日志，保证 dumpDisabled 路径被执行（logback 默认配置不可依赖）
        ch.qos.logback.classic.Logger log = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(BeanConditionEvaluator.class);
        ch.qos.logback.classic.Level original = log.getLevel();
        log.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            // 修复前：dumpDisabled 遍历 getOnClass() 返回 null → NPE → 容器构建失败
            IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                    attachmentResource("test_missing_class_only.beans.xml"));
            try {
                container.start();
                // java.lang.String 存在 → missing-class 条件不满足 → bean 被禁用
                assertFalse(container.containsBean("missingClassOnlyBean"),
                        "missing-class 指向已存在的类时 bean 必须被禁用");
            } finally {
                container.stop();
            }
        } finally {
            log.setLevel(original);
        }
    }
}
