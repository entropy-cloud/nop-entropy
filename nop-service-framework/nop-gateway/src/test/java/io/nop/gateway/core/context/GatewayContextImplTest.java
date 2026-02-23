/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.context;

import io.nop.gateway.model.GatewayRouteModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GatewayContextImpl单元测试
 */
class GatewayContextImplTest {

    private GatewayContextImpl context;

    @BeforeEach
    void setUp() {
        context = new GatewayContextImpl();
    }

    @Test
    void testAttributeOperations() {
        context.setAttribute("key1", "value1");
        assertEquals("value1", context.getAttribute("key1"));

        context.setAttribute("key2", 123);
        assertEquals(123, context.getAttribute("key2"));

        assertNull(context.getAttribute("nonexistent"));
    }

    @Test
    void testRemoveAttribute() {
        context.setAttribute("key1", "value1");
        assertEquals("value1", context.getAttribute("key1"));

        context.removeAttribute("key1");
        assertNull(context.getAttribute("key1"));
    }

    @Test
    void testStreamingMode() {
        assertFalse(context.isStreamingMode());

        context.setStreamingMode(true);
        assertTrue(context.isStreamingMode());

        context.setStreamingMode(false);
        assertFalse(context.isStreamingMode());
    }

    @Test
    void testCurrentRoute() {
        assertNull(context.getCurrentRoute());

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("test-route");
        context.setCurrentRoute(route);
        assertSame(route, context.getCurrentRoute());
    }

    @Test
    void testRequestPath() {
        assertNull(context.getRequestPath());

        context.setRequestPath("/api/test");
        assertEquals("/api/test", context.getRequestPath());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        // Test thread safety of attributes
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                context.setAttribute("counter", i);
                context.getAttribute("counter");
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        // Should complete without exceptions
    }
}
