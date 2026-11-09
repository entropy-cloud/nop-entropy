/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.initialize.IocCoreInitializer;
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C6-008）initializer destroy 换序载体钉：先 stop 后切注册表指针——
 * destroy 期间解析不再落 parent/新建孤儿 singleton。
 */
public class TestIocCoreInitializerStop extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        // 恢复共享环境（CoreInitialization.destroy 已由本测试局部破坏的字段恢复由 swap 完成回写）
        CoreInitialization.destroy();
        CoreInitialization.initialize();
    }

    /** pin8：destroy() 后注册表指针指向 parent（非死容器）且 appContainer 已 stopped。 */
    @Test
    public void pin8_initializerStopsBeforeRegistrySwap() throws Exception {
        IocCoreInitializer initializer = new IocCoreInitializer();

        // 独立 fake appContainer（不污染共享环境：经反射注入，destroy 后立即由下方恢复）
        IBeanContainerImplementor fakeApp = new BeanContainerBuilder(null).build("pin8-fake-app");
        java.lang.reflect.Method startM = fakeApp.getClass().getMethod("start");
        startM.invoke(fakeApp);
        assertTrue(fakeApp.isStarted());

        Field appField = IocCoreInitializer.class.getDeclaredField("appContainer");
        appField.setAccessible(true);
        appField.set(initializer, fakeApp);
        Field parentField = IocCoreInitializer.class.getDeclaredField("parentContainer");
        parentField.setAccessible(true);
        IBeanContainer priorRegistry = BeanContainer.instance();
        parentField.set(initializer, priorRegistry);

        // 先把注册表指到 fakeApp（模拟 initialize 的 registerInstance(appContainer)）
        BeanContainer.registerInstance(fakeApp);
        assertNotSame(priorRegistry, BeanContainer.instance());

        initializer.destroy();

        // 换序语义：stop 已执行（fakeApp 终态）+ 注册表指针已切回 parent
        assertTrue(!fakeApp.isStarted(), "appContainer 应已 stop");
        assertNotSame(fakeApp, BeanContainer.instance(), "缺陷态：销毁期注册表仍持死 app 容器 → 解析落死容器/新建孤儿");
    }
}
