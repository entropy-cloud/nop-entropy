/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyXplBean;
import test.io.entropy.beans.TestXplService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestXplBean extends BaseTestCase {

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
    }

    @Test
    public void testXplProperty() {

        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                attachmentResource("test_xpl_property.beans.xml"));
        container.start();
        MyXplBean bean = (MyXplBean) container.getBean("myXplBean");
        assertNotNull(bean.getXplA());
        assertNotNull(bean.getXplB());
        assertNotNull(bean.getExprC());
        container.stop();
    }

    public void testAutoConfig() {
        TestXplService service = BeanContainer.instance().getBeanByType(TestXplService.class);
        assertEquals(0, service.getStartCount());

        // IConfigSetUpdator updator = ConfigManager.instance().getConfigSet("test").beginUpdate();
        // updator.setValue("test.config.str1", "aaa");
        // updator.setValue("test.config.int2", "3");
        // updator.endUpdate();

        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }

        assertEquals(1, service.getStartCount());
        assertEquals("aaa", service.getConfig().getStr1());
        assertEquals(3, service.getConfig().getInt2());
    }
}
