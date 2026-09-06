/**
 * prototype + ioc:init xpl 回归测试：
 * 修复前 prototype 的 beanScope 为 null，runXpl 直接解引用 getEvalScope() 抛 NPE；
 * 修复后回退到独立 scope（挂容器变量解析扩展），xpl 正常执行。
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyPrototypeXplBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPrototypeXpl extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testPrototypeIocInitXplRuns() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_prototype_xpl.beans.xml"));
        try {
            container.start();
            // 修复前：getBean 触发 initBean → runXpl 对 null scope NPE（适配为 NopException）
            MyPrototypeXplBean bean = (MyPrototypeXplBean) container.getBean("myPrototypeXplBean");
            assertTrue(bean.isInitialized(), "prototype bean 的 ioc:init xpl 必须执行（不得 NPE）");

            // prototype 语义：每次 getBean 返回新实例
            MyPrototypeXplBean second = (MyPrototypeXplBean) container.getBean("myPrototypeXplBean");
            assertFalse(second == bean, "prototype 每次返回新实例");
            assertTrue(second.isInitialized(), "新实例的 ioc:init xpl 同样执行");
        } finally {
            container.stop();
        }
    }
}
