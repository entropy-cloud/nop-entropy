package io.nop.spring.core.ioc;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: NopSpringBeanContainer bridges the Spring
 * ApplicationContext onto the Nop IBeanContainer SPI — bean lookup by name
 * and by type, scope reporting, and autowire-candidate resolution must agree
 * with what the underlying Spring context holds.
 */
public class TestNopSpringBeanContainer {

    @Test
    public void testContainsAndLookupByRegisteredName() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("myList", ArrayList.class);
        NopSpringBeanContainer container = new NopSpringBeanContainer(context);

        assertEquals("spring", container.getId(), "default container id is 'spring'");

        assertFalse(container.containsBean("missing"));
        assertTrue(container.containsBean("myList"));

        Object bean = container.getBean("myList");
        assertTrue(bean instanceof ArrayList);
    }

    @Test
    public void testGetBeanScopeReportsUnderlyingDefinition() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("myList", ArrayList.class);
        NopSpringBeanContainer container = new NopSpringBeanContainer(context);

        // The bridge forwards Spring's raw scope string verbatim: beans
        // registered without an explicit scope report "" (= Spring's
        // singleton default), which is exactly what the Nop side must see.
        assertEquals(context.getBeanFactory().getBeanDefinition("myList").getScope(),
                container.getBeanScope("myList"),
                "scope reporting is a verbatim pass-through of the Spring definition");
    }

    @Test
    public void testTypeBasedLookupAndAutowireCandidate() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("myList", ArrayList.class);
        NopSpringBeanContainer container = new NopSpringBeanContainer(context);

        Map<String, ArrayList> beansOfType = container.getBeansOfType(ArrayList.class);
        assertEquals(1, beansOfType.size());
        assertTrue(beansOfType.containsKey("myList"));

        assertEquals("myList", container.findAutowireCandidate(ArrayList.class),
                "the single candidate is the autowire candidate");
        assertNull(container.findAutowireCandidate(String.class),
                "no candidate must resolve to null, never to a guessed name");

        assertTrue(container.containsBeanType(ArrayList.class));
        assertFalse(container.containsBeanType(String.class));

        assertSame(beansOfType.get("myList"), container.getBeanByType(ArrayList.class));
        assertNull(container.tryGetBeanByType(String.class),
                "tryGetBeanByType resolves missing types to null");
    }

    @Test
    public void testLifecycleDelegatesToApplicationContext() {
        StaticApplicationContext context = new StaticApplicationContext();
        NopSpringBeanContainer container = new NopSpringBeanContainer(context);

        assertFalse(container.isRunning(), "a non-refreshed Spring context is not running");

        List<Object> events = new java.util.ArrayList<>();
        container.start();
        events.add("start-no-op");
        container.stop();
        container.restart();
        assertEquals(1, events.size(), "start/stop/restart are intentional no-ops on the Spring bridge");
    }
}
