/**
 * 构造器循环依赖检测回归测试：
 * 修复前容器静默创建重复单例（B 持有 A2、容器注册 A1）；修复后创建路径检测到
 * 同线程重入即抛 ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE（对齐 Spring 的
 * BeanCurrentlyInCreationException fail-fast 语义），提示用 ioc:ignore-depends 或改属性注入。
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
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestConstructorCycleDetection extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainerImplementor buildContainer(String resource) {
        return new AppBeanContainerLoader().loadFromResource("test", attachmentResource(resource));
    }

    @Test
    public void testEagerStartFailsOnConstructorCycle() {
        IBeanContainerImplementor container = buildContainer("test_ctor_cycle.beans.xml");
        // eager 启动创建 beanA → 构造器解析 beanB → B 属性回引 beanA → 重入创建检测。
        // 修复前：启动静默通过，TestCtorCycleA 被创建两次（容器注册 A1、B 持有 A2）
        NopException e = assertThrows(NopException.class, container::start);
        assertEquals(IocErrors.ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE.getErrorCode(), e.getErrorCode(),
                "构造器循环依赖必须显式失败，不得静默产生重复单例");
        assertEquals("beanA", e.getParam("beanName"), "错误信息必须定位到环上的 bean");
    }

    @Test
    public void testLazyGetBeanFailsOnConstructorCycle() {
        IBeanContainerImplementor container = buildContainer("test_ctor_cycle_lazy.beans.xml");
        container.start();
        try {
            // 懒加载 bean 首次 getBean 触发环并显式失败（不产生重复实例）
            NopException e = assertThrows(NopException.class, () -> container.getBean("beanA"));
            assertEquals(IocErrors.ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE.getErrorCode(), e.getErrorCode());
        } finally {
            container.stop();
        }
    }
}
