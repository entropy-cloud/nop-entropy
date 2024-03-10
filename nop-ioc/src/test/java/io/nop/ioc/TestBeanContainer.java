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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyBeanA;
import test.io.entropy.beans.MyBeanB;
import test.io.entropy.beans.MyBeanC;
import test.io.entropy.beans.MyChild;
import test.io.entropy.beans.MyConstants;
import test.io.entropy.beans.MyDestroyBean;
import test.io.entropy.beans.MyFactoryBean;
import test.io.entropy.beans.MyInitBean;
import test.io.entropy.beans.MyInjectBean;
import test.io.entropy.beans.MyLazyInitBean;
import test.io.entropy.beans.MyPrototypeBean;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author canonical_entropy@163.com
 */
public class TestBeanContainer extends BaseTestCase {
    IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        forceStackTrace();
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    protected void setUp() {
        setTestConfig("test.path", "aa");
        container = new AppBeanContainerLoader().loadFromResource("test", attachmentResource("test_spring.beans.xml"));
        container.start();
        container.toConfigNode().dump();
    }

    @Test
    public void testVar() {

        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_var.beans.xml"));
        container.start();
        MyBeanA a = (MyBeanA) container.getBean("myBeanA");
        assertEquals(3, a.getX());
        container.stop();

    }

    @Test
    public void testFactoryMethod() {

        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_factory.beans.xml"));
        container.start();
        MyBeanA a = (MyBeanA) container.getBean("myBeanA");
        assertEquals(3, a.getX());

        MyBeanA a2 = (MyBeanA) container.getBean("myBeanA");
        assertNotNull(a2.getB());
        assertTrue(a.getB() == a2.getB());
        container.stop();

    }

    @Test
    public void testScope() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_scope.beans.xml"));
        container.start();
        MyBeanA a = (MyBeanA) container.getBean("myBeanA");
        assertEquals(11, a.getX());

        MyBeanC c = (MyBeanC) container.getBean("myBeanC");
        assertNotNull(c.getA());
        assertTrue(c.getA() == a);

        assertEquals(a, container.getBean("myBeanA"));

        MyBeanC c2 = (MyBeanC) container.getBean("myBeanC2");
        assertNotNull(c2.getA());
        assertTrue(a == c2.getA());

        c2 = (MyBeanC) container.getBean("myBeanC2");
        assertTrue(a == c2.getA());
        container.stop();
    }

    @Test
    public void testContainerParent() {
        IBeanContainerImplementor parent = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_scope.beans.xml"));
        parent.start();
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_container_parent.beans.xml"), parent);
        container.start();
        MyBeanA a = (MyBeanA) container.getBean("myBeanA");
        assertEquals(11, a.getX());

        MyBeanC c5 = (MyBeanC) container.getBean("myBeanC5");
        assertTrue(c5.getA() == a);
        MyBeanC c = (MyBeanC) container.getBean("myBeanC");
        assertNotNull(c.getA());
        assertTrue(c.getA() == a);

        assertEquals(a, container.getBean("myBeanA"));

        MyBeanC c2 = (MyBeanC) container.getBean("myBeanC2");
        assertNotNull(c2.getA());
        assertTrue(a == c2.getA());

        c2 = (MyBeanC) container.getBean("myBeanC2");
        assertTrue(a == c2.getA());
        parent.stop();
        container.stop();
    }

    @Test
    public void testBeanRef() {

        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_bean_ref.beans.xml"));
        container.start();
        MyBeanA beanA = (MyBeanA) container.getBean("test");
        assertEquals(container, beanA.container);
        container.stop();
    }

    @Test
    public void testInject() {
        MyBeanC c = (MyBeanC) container.getBean("c");
        assertNotNull(c.getA2());
        assertNotNull(c.getA());
        assertNotNull(c.getA3());
        assertTrue(c.getA() == c.getA2());
        assertTrue(c.getA() == c.getA3());
    }

    @Test
    public void testInitProperty() {
        MyBeanB b = (MyBeanB) container.getBean("testInitProperty");
        assertTrue(b.getA().isInited());
    }

    @Test
    public void testConst() {
        assertEquals(MyConstants.STATE_DRAFT, container.getBean("cc"));

        MyBeanA a = (MyBeanA) container.getBean("a");
        assertEquals(MyConstants.STATE_DRAFT, a.getDataList().get(0));
    }

    /**
     * 测试别名
     */
    @Test
    public void testAlias() {
        System.out.println(container.getBean("a"));
        assertEquals(container.getBean("_a"), container.getBean("a"));
        assertEquals(container.getBean("a1"), container.getBean("a"));
        assertEquals(container.getBean("a2"), container.getBean("a"));
    }

    /**
     * 测试A-->B-->A
     */
    @Test
    public void testReferenceAB() {
        MyBeanA a = (MyBeanA) container.getBean("a");
        assertEquals(a, a.getB().getA());
    }

    /**
     * 测试A-->B-->C-->A
     */
    @Test
    public void testReferenceAC() {
        MyBeanA a = (MyBeanA) container.getBean("a");
        assertEquals(a, a.getB().getC().getA());
    }

    /**
     * 测试销毁方法
     */
    @Test
    public void testDestroy() {
        MyDestroyBean myDestroyBean = (MyDestroyBean) container.getBean("myDestroyBean");
        assertFalse(myDestroyBean.isDestroyed());
        container.stop();
        assertTrue(myDestroyBean.isDestroyed());
    }

    /**
     * 测试初始化方法
     */
    @Test
    public void testInit() {
        MyInitBean myInitBean = (MyInitBean) container.getBean("myInitBean");
        assertTrue(myInitBean.isInited());
    }

    /**
     * 测试集合
     */
    @Test
    public void testCollections() {
        MyBeanA a = (MyBeanA) container.getBean("a");
        assertEquals("[1, 2, 3, 4]", a.getDataList().toString());
        assertEquals("{name=TerrorM, age=23}", a.getStrMap().toString());
        Properties props = new Properties();
        props.put("height", "175cm");
        props.put("gender", "male");
        assertEquals(props, a.getProps());
    }

    @Test
    public void testObjList() {
        MyBeanA a = (MyBeanA) container.getBean("a");
        assertTrue(a.getObjList().get(0) instanceof MyBeanB);
        assertTrue(a.getObjList().get(1) instanceof MyBeanC);
        assertEquals(a, container.getBean("a"));
    }

    /**
     * 测试parent，子类的bean要包含父类bean中的全部属性
     */
    @Test
    public void testParent() {
        MyChild myChild = (MyChild) container.getBean("myChild");
        assertEquals(myChild.getName(), myChild.getMyParent().getName());
    }

    /**
     * 测试工厂
     */
    @Test
    public void testFactory() {
        Object a = container.getBean("myFactoryBean");
        assertEquals(MyBeanA.class, a.getClass());

        Object a2 = container.getBean("myFactoryBean");
        assertEquals(a, a2);

        Object f = container.getBean("&myFactoryBean");
        assertEquals(MyFactoryBean.class, f.getClass());

        assertTrue(((MyFactoryBean) f).isInited());

        container.stop();

        assertTrue(!((MyFactoryBean) f).isInited());
    }

    /**
     * 测试scope
     */
    @Test
    public void testPrototype() {
        assertEquals(0, MyPrototypeBean.createdCount);

        MyPrototypeBean myPrototypeBean1 = (MyPrototypeBean) container.getBean("myPrototypeBean");
        assertEquals(1, MyPrototypeBean.createdCount);

        MyPrototypeBean myPrototypeBean2 = (MyPrototypeBean) container.getBean("myPrototypeBean");
        assertEquals(2, MyPrototypeBean.createdCount);

        assertFalse(myPrototypeBean1 == myPrototypeBean2);
    }

    /**
     * 测试延迟加载
     */
    @Test
    public void testLazyInit() {
        assertEquals(0, MyLazyInitBean.createdCount);

        container.getBean("myLazyInitBean");
        assertEquals(1, MyLazyInitBean.createdCount);

        container.getBean("myLazyInitBean");
        assertEquals(1, MyLazyInitBean.createdCount);
    }

    @Test
    public void testInject2() {
        MyInjectBean bean = (MyInjectBean) container.getBean("myInjectBean");
        assertTrue(bean.isInited());
        assertTrue(bean.getA() != null);
        assertTrue(bean.getB() != null);
    }
}
