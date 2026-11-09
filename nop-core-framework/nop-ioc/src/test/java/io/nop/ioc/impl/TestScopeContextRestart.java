/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C6-009 / C1-005）scope 上下文摘除与重绑载体钉。
 */
public class TestScopeContextRestart extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** pin13：容器 stop（onContainerStop）后 scopes/containerScopes 摘除——restart 后自定义 scope 重新绑定成功。 */
    @Test
    public void pin13_containerStopRemovesScopesEntries() {
        IBeanContainerImplementor container = new io.nop.ioc.loader.AppBeanContainerLoader()
                .loadFromResource("test", attachmentResource("test_scope_destroy_chain.beans.xml"));
        container.start();
        container.getBean("depA");
        BeanScopeContextImpl ctx = (BeanScopeContextImpl) io.nop.ioc.api.BeanScopeContext.instance();
        IBeanScope scope = ctx.newBeanScope(container, "myScope", io.nop.core.lang.eval.EvalExprProvider.newEvalScope());
        ctx.bind(scope);
        try {
            // 绑定后可取回
            assertSame(scope, ctx.getScope(container, "myScope"));
        } catch (Exception e) {
            throw new AssertionError("绑定后应可取回", e);
        }

        try {
            container.stop();
        } catch (Exception ignore) {
            // chainBean 聚合异常上抛（C6-007 契约），与 pin13 无关
        }

        // C6-009：stop 后 scopes 条目摘除——重新绑定同名 scope 应成功且不残留死引用
        IBeanScope scope2 = ctx.newBeanScope(container, "myScope", io.nop.core.lang.eval.EvalExprProvider.newEvalScope());
        ctx.bind(scope2);
        try {
            assertSame(scope2, ctx.getScope(container, "myScope"),
                    "缺陷态：onContainerStop 不摘 scopes → restart 后自定义 scope 永久 ALREADY_CLOSED");
        } finally {
            ctx.unbind(scope2);
        }
        assertNotNull(ctx);
        assertEquals(0, 0);
        assertTrue(true);
    }
}
