/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyForceLazyBean;
import test.io.entropy.beans.MyLazyPropertyBean;
import test.io.entropy.beans.MyLazyPropertyWithDelayBean;
import test.io.entropy.beans.MyNormalRefBean;
import test.io.entropy.beans.MyReferForceLazyBean;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试ioc:lazy-property和ioc:force-lazy-property属性
 */
public class TestLazyProperty extends BaseTestCase {
    IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 测试ioc:lazy-property：lazyPropertyProp属性应该在init-method之后设置
     */
    @Test
    public void testLazyProperty() {
        container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_lazy_property.beans.xml"));
        container.start();

        MyLazyPropertyBean bean = (MyLazyPropertyBean) container.getBean("lazyPropertyBean");

        // 验证属性值
        assertEquals("lazy-value", bean.getLazyPropertyProp());
        assertEquals("normal-value", bean.getNormalProp());

        // 验证init-method已执行
        assertTrue(bean.isInited());

        // 验证执行顺序：构造函数 -> 普通属性 -> init-method -> lazy-property
        List<String> executionOrder = bean.getExecutionOrder();
        assertEquals("constructor", executionOrder.get(0));
        assertEquals("setNormalProp", executionOrder.get(1));
        assertEquals("init-method", executionOrder.get(2));
        assertEquals("setLazyPropertyProp", executionOrder.get(3));

        container.stop();
    }

    /**
     * 测试ioc:force-lazy-property：引用forceLazyBean的属性应该自动设置为lazy-property
     */
    @Test
    public void testForceLazyProperty() {
        container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_lazy_property.beans.xml"));
        container.start();

        MyReferForceLazyBean bean = (MyReferForceLazyBean) container.getBean("referForceLazyBean");

        // 验证引用的bean都存在
        assertNotNull(bean.getForceLazyRef());
        assertNotNull(bean.getNormalRef());

        // 验证init-method已执行
        assertTrue(bean.isInited());

        // 验证执行顺序：构造函数 -> 普通引用属性 -> init-method -> force-lazy引用属性
        List<String> executionOrder = bean.getExecutionOrder();
        assertEquals("constructor", executionOrder.get(0));
        assertEquals("setNormalRef", executionOrder.get(1));
        assertEquals("init-method", executionOrder.get(2));
        assertEquals("setForceLazyRef", executionOrder.get(3));

        // 验证forceLazyRef bean确实设置了force-lazy-property
        MyForceLazyBean forceLazyBean = (MyForceLazyBean) container.getBean("forceLazyBean");
        assertNotNull(forceLazyBean);
        assertEquals("force-lazy-bean", forceLazyBean.getName());

        container.stop();
    }

    /**
     * 测试lazy-property与delay-method的执行顺序
     */
    @Test
    public void testLazyPropertyWithDelayMethod() {
        container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_lazy_property.beans.xml"));
        container.start();

        MyLazyPropertyWithDelayBean bean = (MyLazyPropertyWithDelayBean) container.getBean("lazyPropertyWithDelayBean");

        // 验证属性值
        assertEquals("lazy-value", bean.getLazyProp());
        assertEquals("normal-value", bean.getNormalProp());

        // 验证init-method和delay-method都已执行
        assertTrue(bean.isInited());
        assertTrue(bean.isDelayed());

        // 验证执行顺序：构造函数 -> 普通属性 -> init-method -> lazy-property -> delay-method
        List<String> executionOrder = bean.getExecutionOrder();
        assertEquals(5, executionOrder.size());
        assertEquals("constructor", executionOrder.get(0));
        assertEquals("setNormalProp", executionOrder.get(1));
        assertEquals("init-method", executionOrder.get(2));
        assertEquals("setLazyProp", executionOrder.get(3));
        assertEquals("delay-method", executionOrder.get(4));

        container.stop();
    }

    /**
     * 验证lazy-property属性会自动设置ignore-depends=true
     */
    @Test
    public void testLazyPropertyIgnoreDepends() {
        container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_lazy_property.beans.xml"));
        container.start();

        // 验证可以正常启动，没有循环依赖错误
        MyLazyPropertyBean bean = (MyLazyPropertyBean) container.getBean("lazyPropertyBean");
        assertNotNull(bean);
        assertTrue(bean.isInited());

        container.stop();
    }
}
