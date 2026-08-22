/**
 * ioc:condition 的 unless-property 分支回归测试：
 * 修复前 unless 分支误读 getIfProperty()——仅配置 unless-property 时 NPE，
 * if+unless 同时配置时 unless 条件重复求值 if 条件（if 通过即被错误禁用）。
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBeanConditionUnlessProperty extends BaseTestCase {

    static final String UNLESS_FLAG = "test.unless.flag";
    static final String IF_FLAG = "test.if.flag";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainerImplementor loadContainer() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_condition_unless.beans.xml"));
        container.start();
        return container;
    }

    private void setFlag(String name, Object value) {
        if (value == null) {
            AppConfig.getConfigProvider().assignConfigValue(name, null);
        } else {
            AppConfig.getConfigProvider().assignConfigValue(name, value);
        }
    }

    @Test
    public void testUnlessOnlyPropertyMissingEnablesBean() {
        // 修复前：进入 unless 分支即 getIfProperty().getName() NPE，容器构建直接崩溃
        setFlag(UNLESS_FLAG, null);
        IBeanContainerImplementor container = loadContainer();
        try {
            assertTrue(container.containsBean("unlessOnlyBean"),
                    "unless-property 未命中时 bean 必须启用");
        } finally {
            container.stop();
        }
    }

    @Test
    public void testUnlessOnlyPropertyMatchedDisablesBean() {
        setFlag(UNLESS_FLAG, "on");
        IBeanContainerImplementor container = loadContainer();
        try {
            assertFalse(container.containsBean("unlessOnlyBean"),
                    "unless-property 命中时 bean 必须禁用");
        } finally {
            container.stop();
        }
    }

    @Test
    public void testIfAndUnlessEvaluatedIndependently() {
        // if 命中 + unless 未命中 → 启用
        setFlag(IF_FLAG, "on");
        setFlag(UNLESS_FLAG, null);
        IBeanContainerImplementor container = loadContainer();
        try {
            assertTrue(container.containsBean("ifAndUnlessBean"),
                    "if 命中且 unless 未命中时 bean 必须启用");
        } finally {
            container.stop();
        }
    }

    @Test
    public void testIfAndUnlessBothMatchedDisablesBean() {
        // if 命中 + unless 命中 → 禁用（unless 按自身字段求值）
        setFlag(IF_FLAG, "on");
        setFlag(UNLESS_FLAG, "on");
        IBeanContainerImplementor container = loadContainer();
        try {
            assertFalse(container.containsBean("ifAndUnlessBean"),
                    "unless 命中时 bean 必须禁用（即使 if 同时命中）");
        } finally {
            container.stop();
        }
    }

    @Test
    public void testIfNotMatchedDisablesBean() {
        setFlag(IF_FLAG, null);
        setFlag(UNLESS_FLAG, null);
        IBeanContainerImplementor container = loadContainer();
        try {
            assertFalse(container.containsBean("ifAndUnlessBean"),
                    "if 未命中时 bean 必须禁用");
        } finally {
            container.stop();
        }
    }
}
