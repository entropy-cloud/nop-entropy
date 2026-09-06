/**
 * prototype 构造器循环依赖检测回归测试：
 * 修复前 prototype 路径（beanScope == null）不做创建中标记，构造器自引用/互引环
 * 每次注入都新建实例，无限递归至 StackOverflowError；
 * 修复后同 singleton 路径使用 inCreationThread 标记，抛 ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE。
 */
package io.nop.ioc;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPrototypeCycleDetection extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testPrototypeConstructorSelfRefFailsWithCycleError() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_prototype_cycle.beans.xml"));
        try {
            container.start();
            // 修复前：此处抛 StackOverflowError（Error 而非 NopException，测试直接红）
            NopException e = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                    () -> container.getBean("protoSelfRef"));
            assertEquals(IocErrors.ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE.getErrorCode(), e.getErrorCode(),
                    "prototype 构造器环必须显式失败，不得无限递归");
            assertEquals("protoSelfRef", e.getParam("beanName"), "错误信息必须定位到环上的 bean");
        } finally {
            container.stop();
        }
    }
}
