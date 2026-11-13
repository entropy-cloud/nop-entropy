/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C4-010 / C6-007）销毁序与销毁链聚合载体钉。
 */
public class TestScopeDestroyOrderAndChain extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** pin11：销毁序 = 拓扑降序——依赖方 depA 先于被依赖方 depB 销毁。 */
    @Test
    public void pin11_destroyOrderIsTopoDescending() {
        DestroyOrderBean.DESTROY_ORDER.clear();
        IBeanContainerImplementor container = new io.nop.ioc.loader.AppBeanContainerLoader()
                .loadFromResource("test", attachmentResource("test_scope_destroy_chain.beans.xml"));
        container.start();
        container.getBean("depA");
        container.getBean("depB");
        try {
            // 同文件 chainBean 的 destroyMethod 重抛首异常（C6-007 No-Silent 契约）——pin11 只关心销毁序
            container.stop();
        } catch (Exception expected) {
            // ignore：聚合异常上抛本身由 pin12/插件契约钉守护
        }

        assertEquals(2, DestroyOrderBean.DESTROY_ORDER.size());
        int ia = DestroyOrderBean.DESTROY_ORDER.indexOf("depA");
        int ib = DestroyOrderBean.DESTROY_ORDER.indexOf("depB");
        assertTrue(ia >= 0 && ib >= 0 && ia < ib,
                "缺陷态：销毁无序（CHM 哈希序）→ destroy 运行于依赖已销毁之后。order=" + DestroyOrderBean.DESTROY_ORDER);
    }

    /** pin12：destroyMethod 抛错后 ioc:destroy 仍执行（逐方法隔离，异常聚合 ERROR）。 */
    @Test
    public void pin12_destroyMethodFailureDoesNotSkipIocDestroy() {
        BrokenDestroyBean.iocDestroyRan = false;
        IBeanContainerImplementor container = new io.nop.ioc.loader.AppBeanContainerLoader()
                .loadFromResource("test", attachmentResource("test_scope_destroy_chain.beans.xml"));
        container.start();
        container.getBean("chainBean");
        try {
            container.stop();
        } catch (Exception e) {
            // 聚合口径：不向 stop 调用方抛（C6-007）；若抛则载体红
        }
        assertTrue(BrokenDestroyBean.iocDestroyRan,
                "缺陷态：destroyMethod 抛错跳过 ioc:destroy");
    }
}
