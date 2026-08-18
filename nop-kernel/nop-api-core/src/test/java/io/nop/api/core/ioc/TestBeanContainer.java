package io.nop.api.core.ioc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    private static IBeanContainer dummyContainer() {
        return (IBeanContainer) java.lang.reflect.Proxy.newProxyInstance(
                IBeanContainer.class.getClassLoader(),
                new Class[]{IBeanContainer.class},
                (proxy, method, args) -> null);
    }
}
