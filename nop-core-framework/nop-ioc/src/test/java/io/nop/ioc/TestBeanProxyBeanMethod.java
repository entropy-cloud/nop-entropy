/**
 * ioc:proxy + ioc:bean-method 组合回归测试：
 * 修复前 initBean 阶段 setHandler 对 JDK 代理对象强转 DelegateInvocationHandler 必抛
 * ClassCastException（容器启动失败）；修复后代理经 bean-method 返回的 handler 委托调用。
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyProxiedService;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBeanProxyBeanMethod extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testProxyBeanMethodCombinationWorks() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_proxy_bean_method.beans.xml"));
        try {
            container.start();
            // 修复前：getBean 触发 initBean → setHandler 强转代理对象抛 CCE（适配为 NopException）
            Object bean = container.getBean("proxiedService");
            assertInstanceOf(MyProxiedService.class, bean, "必须返回 ioc:type 指定接口的实例");
            assertTrue(Proxy.isProxyClass(bean.getClass()), "ioc:proxy 必须经 JDK 动态代理包装");

            MyProxiedService service = (MyProxiedService) bean;
            assertEquals("hello:world", service.greet("world"),
                    "代理调用必须委托到 bean-method 返回的 InvocationHandler");
        } finally {
            container.stop();
        }
    }
}
