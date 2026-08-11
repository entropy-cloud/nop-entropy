/**
 * 临时最小复现（TEMP-DIAG，验证后转为平台回归测试或删除）：
 * 递归环中 depends-on（resolvedDepends 强制创建，getBean(name, false)）是否返回未完成 init 的 bean。
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
import test.io.entropy.beans.TestCircularA;
import test.io.entropy.beans.TestCircularB;

import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReentrantCircularRepro extends BaseTestCase {

    @BeforeAll
    public static void init() {
        setTestConfig(IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE, "ALL_LAZY");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        TestCircularA.s_inited = false;
    }

    @Test
    public void testResolvedDependsReturnsFullyInitedBean() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("repro",
                attachmentResource("test_reentrant_circular.beans.xml"));
        container.start();
        try {
            TestCircularB b = (TestCircularB) container.getBean("beanB");
            // 语义契约：depends-on 应保证 init 时 beanA 已完整创建
            assertTrue(b.aFullyInited, "depends-on dependency must be fully inited (incl. init()) before dependent.init()");
        } finally {
            container.stop();
        }
    }
}
