/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyCreationLog;
import test.io.entropy.beans.MyDependsBeanA;
import test.io.entropy.beans.MyDependsBeanB;
import test.io.entropy.beans.MyDependsBeanC;
import test.io.entropy.beans.MyDependsBeanD;
import test.io.entropy.beans.MyDependsBeanX;
import test.io.entropy.beans.MyDependsBeanY;
import test.io.entropy.beans.MyDependsBeanZ;

import java.util.Set;

import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_ORDER_CONSTRAINT_VIOLATED;
import static io.nop.ioc.IocErrors.ERR_IOC_UNKNOWN_DEPEND_REF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 ioc:before/ioc:after/depends-on 统一为 resolvedDepends 单一前置依赖机制。
 */
public class TestBeanDepends extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    protected void setUp() {
        MyDependsBeanA.reset();
        MyDependsBeanB.reset();
        MyDependsBeanC.reset();
        MyDependsBeanD.reset();
        MyDependsBeanX.reset();
        MyDependsBeanY.reset();
        MyDependsBeanZ.reset();
        MyCreationLog.reset();
    }

    IBeanContainerImplementor load(String resource) {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource(resource));
        container.start();
        return container;
    }

    @Test
    public void testAfterForceCreate() {
        IBeanContainerImplementor container = load("test_bean_depends.beans.xml");
        // 创建 afterA 强制创建其前置 afterB
        container.getBean("afterA");
        assertEquals(1, MyDependsBeanB.createdCount);
        assertEquals(1, MyDependsBeanA.createdCount);
        container.stop();
    }

    @Test
    public void testNoForwardCreation() {
        IBeanContainerImplementor container = load("test_bean_depends.beans.xml");
        // 创建 afterB 不再连带创建 afterA（nextBeans 前向联动已移除）
        container.getBean("afterB");
        assertEquals(1, MyDependsBeanB.createdCount);
        assertEquals(0, MyDependsBeanA.createdCount);
        container.stop();
    }

    @Test
    public void testBeforeForceCreate() {
        IBeanContainerImplementor container = load("test_bean_depends.beans.xml");
        // 创建 beforeA 时前置方 beforeZ 进入其 resolvedDepends
        container.getBean("beforeA");
        assertEquals(1, MyDependsBeanZ.createdCount);
        container.stop();
    }

    @Test
    public void testRefTopologyFilter() {
        IBeanContainerImplementor container = load("test_bean_depends.beans.xml");
        // ref 目标 refY 拓扑序在 refX 之前，进入 resolvedDepends，保证完整初始化
        container.getBean("refX");
        assertEquals(1, MyDependsBeanY.createdCount);
        container.stop();
    }

    @Test
    public void testDependsOnForceCreate() {
        IBeanContainerImplementor container = load("test_bean_depends.beans.xml");
        // depends-on 强制创建语义保持不变
        container.getBean("dependsC");
        assertEquals(1, MyDependsBeanD.createdCount);
        container.stop();
    }

    @Test
    public void testAsyncStartOrder() {
        // 通过 BeanContainerBuilder 直接开启 concurrent start
        IBeanContainerImplementor container = new BeanContainerBuilder(null)
                .concurrentStart(true)
                .addResource(attachmentResource("test_bean_depends_async.beans.xml"))
                .build("test");
        container.start();
        container.awaitStartFinished();
        // asyncB 是 asyncA 的前置，必须比 asyncA 先创建
        assertTrue(MyCreationLog.order.indexOf("B") < MyCreationLog.order.indexOf("A"),
                "async start should respect ioc:after order, got " + MyCreationLog.order);
        assertEquals(1, MyDependsBeanA.createdCount);
        assertEquals(1, MyDependsBeanB.createdCount);
        container.stop();
    }

    @Test
    public void testModelNotRewritten() {
        IBeanContainerImplementor container = load("test_bean_depends.beans.xml");
        // model.dependsOn 不包含 before/after 推导的目标
        BeanDefinition afterA = (BeanDefinition) container.getBeanDefinition("afterA");
        Set<String> dependsOn = afterA.getBeanModel().getDependsOn();
        assertTrue(dependsOn == null || !dependsOn.contains("afterB"),
                "model dependsOn must not be rewritten by ioc:after");

        // dump 输出 ext:resolved-depends
        XNode node = container.toConfigNode();
        XNode afterANode = node.childByAttr("id", "afterA");
        assertEquals("afterB", afterANode.attrText("ext:resolved-depends"));
        container.stop();
    }

    private void assertOrderError(String resource) {
        NopException e = assertThrows(NopException.class,
                () -> new AppBeanContainerLoader().loadFromResource("test", attachmentResource(resource)),
                "顺序违例应在容器加载阶段报错: " + resource);
        assertEquals(ERR_IOC_BEAN_ORDER_CONSTRAINT_VIOLATED.getErrorCode(), e.getErrorCode(), e.getMessage());
    }

    @Test
    public void testNoFalsePositiveOnValidOrder() {
        // 正常顺序不误报：beanZ(ioc:before="beanA")，id 字母序 beanA < beanZ，
        // 最终顺序必须 beanZ 先于 beanA（只有 ioc:before 约束边被消费时成立）
        IBeanContainerImplementor container = load("test_bean_depends_order.beans.xml");
        container.stop();
    }

    @Test
    public void testBeforeCycleReported() {
        assertOrderError("test_bean_depends_cycle.beans.xml");
    }

    @Test
    public void testDependsOnCycleReported() {
        assertOrderError("test_bean_depends_depends_on_cycle.beans.xml");
    }

    @Test
    public void testMissingBeforeTargetIgnored() {
        IBeanContainerImplementor container = load("test_bean_depends_missing.beans.xml");
        container.stop();
    }

    @Test
    public void testMissingDependsOnTargetReported() {
        // depends-on 是强声明：目标缺失必须在加载期报错（弱声明只适用于 ioc:before/after）
        NopException e = assertThrows(NopException.class,
                () -> new AppBeanContainerLoader().loadFromResource("test",
                        attachmentResource("test_bean_depends_missing_depends_on.beans.xml")),
                "depends-on 缺失目标应在加载期报错");
        assertEquals(ERR_IOC_UNKNOWN_DEPEND_REF.getErrorCode(), e.getErrorCode(), e.getMessage());
    }

    @Test
    public void testBeforeAliasOfDefaultBean() {
        // ioc:default bean 的 id 自动加 $DEFAULT$ 前缀，ioc:before 声明使用原始别名。
        // 反向匹配必须按 normalize 后的 id 建边，使 initializer 排在 default bean 之前。
        load("test_bean_depends_default_alias.beans.xml");
        // myInitializer = A，nopMySessionFactory = B，创建顺序 A 先于 B
        assertTrue(MyCreationLog.order.indexOf("A") < MyCreationLog.order.indexOf("B"),
                "ioc:before default-alias must order initializer first, got " + MyCreationLog.order);
    }
}