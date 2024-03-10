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
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.xlang.XLangErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.io.entropy.beans.MyLazyInitBean2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBeanContainerParser extends BaseTestCase {
    IBeanContainerImplementor container;

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
        setTestConfig("test.path", "[rootPath]/a");
        MyLazyInitBean2.createdCount = 0;
        container = new AppBeanContainerLoader().loadFromResource("test", attachmentResource("test_spring.beans.xml"));
        container.start();
    }

    @Test
    public void testDuplicateId() {
        container.stop();

        MyLazyInitBean2.createdCount = 0;
        try {
            container = new AppBeanContainerLoader().loadFromResource("test",
                    attachmentResource("test_duplicate_id.beans.xml"));
            container.start();
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals(XLangErrors.ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE.getErrorCode(),
                    e.getErrorCode());
        }
    }

    @Test
    public void testInvalidMap() {
        container.stop();

        MyLazyInitBean2.createdCount = 0;
        try {
            container = new AppBeanContainerLoader().loadFromResource("test",
                    attachmentResource("test_invalid_map.beans.xml"));
            container.start();
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals(XLangErrors.ERR_XDSL_ATTR_NOT_ALLOWED.getErrorCode(), e.getErrorCode());
        }
    }

    /**
     * 测试延迟加载
     */
    @Test
    public void testLazyInit2() {
        assertEquals(1, MyLazyInitBean2.createdCount);

        container.getBean("myLazyInitBean2");
        assertEquals(1, MyLazyInitBean2.createdCount);

        container.getBean("myLazyInitBean2");
        assertEquals(1, MyLazyInitBean2.createdCount);
    }

    @Test
    public void testInvalidConfig() {
        container.stop();

        try {
            container = new AppBeanContainerLoader().loadFromResource("test",
                    attachmentResource("test_invalid_config.beans.xml"));
            container.start();
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals(XLangErrors.ERR_XDSL_UNDEFINED_CHILD_NODE.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testInvalid2Config() {
        try {
            new AppBeanContainerLoader().loadFromResource("test", attachmentResource("test_invalid2.beans.xml"));
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals(IocErrors.ERR_IOC_CLASS_NOT_FOUND.getErrorCode(), e.getErrorCode());
        }

    }

    @Test
    public void testBeanDuplicate() {
        new AppBeanContainerLoader().loadFromResource("test", attachmentResource("test_bean_duplicate.beans.xml"),
                container);
    }
}
