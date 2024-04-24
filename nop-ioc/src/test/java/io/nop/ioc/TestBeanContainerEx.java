/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyBeanA;
import test.io.entropy.beans.MyCycleA;
import test.io.entropy.beans.MyCycleB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestBeanContainerEx extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        setTestConfig("my.prefix", "test");
    }

    @Test
    public void testEmbedded() {
        IBeanContainer container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_embedded.beans.xml"));
        container.start();
        MyBeanA a = container.getBeanByType(MyBeanA.class);
        assertNotNull(a.getB().getC());
        container.stop();
    }

    @Test
    public void autowireBeanInParentContainer() {
        setTestConfig("test.path", "aa");
        IBeanContainer parent = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_spring.beans.xml"));
        parent.start();

        IBeanContainer container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_parent_autowire.beans.xml"), parent);

        container.start();

        MyBeanA a = container.getBeanByType(MyBeanA.class);
        assertNotNull(a.getB());

    }

    @Test
    public void testInjectProtected() {
        IBeanContainer container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_inject.beans.xml"));
        container.start();
        MyBeanA a = container.getBeanByType(MyBeanA.class);
        assertNotNull(a.getBeanD());
        container.stop();
    }

    @Test
    public void testCycleDepends() {
        IBeanContainer container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_cycle_depends.beans.xml"));
        container.start();
        MyCycleA a = container.getBeanByType(MyCycleA.class);
        MyCycleB b = container.getBeanByType(MyCycleB.class);
        assertNotNull(a.getPropB());
        assertNotNull(b.getPropA());
        assertEquals(a, b.getPropA());
        assertEquals(b, a.getPropB());
        container.stop();
    }
}
