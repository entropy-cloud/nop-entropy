package io.nop.api.core.ioc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBeanContainer {

    private IBeanContainerProvider savedProvider;

    @AfterEach
    void restoreProvider() {
        BeanContainer.registerProvider(savedProvider);
    }

    @Test
    public void testIsInitializedReflectsRegisteredContainer() {
        savedProvider = BeanContainer._provider;
        IBeanContainer container = dummyContainer();

        BeanContainer.registerInstance(container);
        assertTrue(BeanContainer.isInitialized());
        assertSame(container, BeanContainer.instance());

        BeanContainer.registerInstance(null);
        assertFalse(BeanContainer.isInitialized(),
                "registerInstance(null) must unregister: isInitialized() must be false");
    }

    @Test
    public void testUnregisteredInstanceYieldsNull() {
        savedProvider = BeanContainer._provider;
        BeanContainer.registerInstance(null);
        assertFalse(BeanContainer.isInitialized());
        assertNull(BeanContainer.instance());
    }

    /**
     * 回归：StaticBeanContainer.getBean声明@Nonnull（与BeanContainerImpl/Spring容器惯例一致），
     * 缺失bean必须抛异常而不是返回null。
     */
    @Test
    public void testStaticBeanContainerGetBeanMissing() {
        StaticBeanContainer container = new StaticBeanContainer();
        Object bean = new Object();
        container.registerBean("svc", bean);
        assertSame(bean, container.getBean("svc"));

        assertThrows(IllegalArgumentException.class, () -> container.getBean("no-such-bean"),
                "missing bean must throw instead of returning null");
    }

    private static IBeanContainer dummyContainer() {
        return (IBeanContainer) java.lang.reflect.Proxy.newProxyInstance(
                IBeanContainer.class.getClassLoader(),
                new Class[]{IBeanContainer.class},
                (proxy, method, args) -> null);
    }
}
